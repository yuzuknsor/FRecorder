/*
 * Copyright 2018 Dmytro Ponomarenko
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.vdo.frecorder.app.main

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.vdo.frecorder.ARApplication
import com.vdo.frecorder.AppConstants
import com.vdo.frecorder.BackgroundQueue
import com.vdo.frecorder.Mapper
import com.vdo.frecorder.R
import com.vdo.frecorder.app.AppRecorder
import com.vdo.frecorder.app.AppRecorderCallback
import com.vdo.frecorder.app.info.RecordInfo
import com.vdo.frecorder.app.settings.SettingsMapper
import com.vdo.frecorder.audio.AudioDecoder
import com.vdo.frecorder.audio.monitor.AudioMonitor
import com.vdo.frecorder.audio.player.PlayerContractNew
import com.vdo.frecorder.audio.recorder.RecorderContract
import com.vdo.frecorder.data.FileRepository
import com.vdo.frecorder.data.Prefs
import com.vdo.frecorder.data.RecordDataSource
import com.vdo.frecorder.data.database.LocalRepository
import com.vdo.frecorder.data.database.OnRecordsLostListener
import com.vdo.frecorder.data.database.Record
import com.vdo.frecorder.exception.AppException
import com.vdo.frecorder.exception.CantCreateFileException
import com.vdo.frecorder.exception.ErrorParser
import com.vdo.frecorder.util.AndroidUtils
import com.vdo.frecorder.util.FileUtil
import com.vdo.frecorder.util.TimeUtils
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.Date

/** 主界面逻辑。Kotlin 化（原 MainPresenter.java）。 */
class MainPresenter(
		private val prefs: Prefs,
		private val fileRepository: FileRepository,
		private val localRepository: LocalRepository,
		private val audioPlayer: PlayerContractNew.Player,
		private val appRecorder: AppRecorder,
		private val recordingsTasks: BackgroundQueue,
		private val loadingTasks: BackgroundQueue,
		private val processingTasks: BackgroundQueue,
		private val importTasks: BackgroundQueue,
		private val settingsMapper: SettingsMapper,
		private val recordDataSource: RecordDataSource
) : MainContract.UserActionsListener {

	private var view: MainContract.View? = null

	private var songDuration: Long = 0

	private var listenPlaybackProgress = true

	/** 为 true 表示 view 未绑定期间开始了导入，绑定后需要补显示导入进度。 */
	private var showImportProgress = false

	private var appRecorderCallback: AppRecorderCallback? = null
	private var playerCallback: PlayerContractNew.PlayerCallback? = null

	override fun bindView(v: MainContract.View) {
		view = v
		if (showImportProgress) {
			v.showImportStart()
		} else {
			v.hideImportProgress()
		}

		if (!prefs.isMigratedDb3()) {
			migrateDb3()
		}
		if (!prefs.hasAskToRenameAfterStopRecordingSetting()) {
			prefs.setAskToRenameAfterStopRecording(true)
		}

		if (appRecorderCallback == null) {
			appRecorderCallback = object : AppRecorderCallback {
				var prevTime: Long = 0

				override fun onRecordingStarted(file: File) {
					view?.let { v ->
						v.showRecordingStart()
						v.keepScreenOn(prefs.isKeepScreenOn)
					}
					updateInformation(
							prefs.getSettingRecordingFormat(),
							prefs.getSettingSampleRate(),
							0
					)
				}

				override fun onRecordingPaused() {
					view?.let { v ->
						v.keepScreenOn(false)
						v.showRecordingPause()
					}
				}

				override fun onRecordingResumed() {
					view?.let { v ->
						v.showRecordingResume()
						v.keepScreenOn(prefs.isKeepScreenOn)
					}
				}

				override fun onRecordingStopped(file: File, rec: Record) {
					if (prefs.isAskToRenameAfterStopRecording()) {
						view?.askRecordingNewName(rec.id.toLong(), file, true)
					}
					prefs.activeRecord = rec.id.toLong()
					songDuration = rec.duration
					view?.let { v ->
						v.showWaveForm(rec.amps, songDuration, 0)
						v.showName(rec.name)
						v.showDuration(TimeUtils.formatTimeIntervalHourMinSec2(songDuration / 1000))
						v.showOptionsMenu()
					}
					updateInformation(rec.format, rec.sampleRate, rec.size, rec.bitrate)
					view?.let { v ->
						v.keepScreenOn(false)
						v.hideProgress()
						v.showRecordingStop()
					}
				}

				override fun onRecordingProgress(mills: Long, amp: Int) {
					val v = view ?: return
					v.onRecordingProgress(mills, amp)
					val recFile = appRecorder.recordFile
					val curTime = System.currentTimeMillis()
					if (recFile != null && curTime - prevTime > 3000) { //录音期间定期刷新录音信息
						updateInformation(
								prefs.getSettingRecordingFormat(),
								prefs.getSettingSampleRate(),
								recFile.length()
						)
						prevTime = curTime
					}
				}

				override fun onError(throwable: AppException) {
					Timber.e(throwable)
					view?.let { v ->
						v.keepScreenOn(false)
						v.hideProgress()
						v.showRecordingStop()
					}
				}
			}
		}
		appRecorder.addRecordingCallback(appRecorderCallback!!)

		if (playerCallback == null) {
			playerCallback = object : PlayerContractNew.PlayerCallback {
				override fun onStartPlay() {
					// 完全停止监听，把音频设备让给播放
					val monitor = AudioMonitor.getInstance()
					if (monitor.isStandalone) {
						monitor.stopStandalone()
					} else if (monitor.isMonitoring) {
						monitor.stop()
					}
					view?.onMonitoringDisabled()
					loadingTasks.postRunnable {
						val record = recordDataSource.getActiveRecord()
						AndroidUtils.runOnUIThread {
							if (view != null && record != null) {
								view!!.startPlaybackService(record.name)
								view!!.showPlayStart(true)
							}
						}
					}
				}

				override fun onPlayProgress(mills: Long) {
					if (view != null && listenPlaybackProgress) {
						val duration = songDuration / 1000
						if (duration > 0) {
							view!!.onPlayProgress(mills, (1000 * mills / duration).toInt())
						}
					}
				}

				override fun onStopPlay() {
					view?.let { v ->
						audioPlayer.seek(0)
						v.showPlayStop()
						v.showDuration(TimeUtils.formatTimeIntervalHourMinSec2(songDuration / 1000))
					}
				}

				override fun onPausePlay() {
					view?.showPlayPause()
				}

				override fun onSeek(mills: Long) {
				}

				override fun onError(throwable: AppException) {
					Timber.e(throwable)
					view?.showError(ErrorParser.parseException(throwable))
				}
			}
		}

		audioPlayer.addPlayerCallback(playerCallback!!)

		if (audioPlayer.isPlaying()) {
			v.showPlayStart(false)
		} else if (audioPlayer.isPaused()) {
			v.showPlayPause()
		} else {
			audioPlayer.seek(0)
			v.showPlayStop()
		}

		if (appRecorder.isPaused) {
			v.keepScreenOn(false)
			v.showRecordingPause()
			v.showRecordingProgress(TimeUtils.formatTimeIntervalHourMinSec2(appRecorder.recordingDuration))
			v.updateRecordingView(appRecorder.recordingData, appRecorder.recordingDuration)
		} else if (appRecorder.isRecording) {
			v.showRecordingStart()
			v.showRecordingProgress(TimeUtils.formatTimeIntervalHourMinSec2(appRecorder.recordingDuration))
			v.keepScreenOn(prefs.isKeepScreenOn)
			v.updateRecordingView(appRecorder.recordingData, appRecorder.recordingDuration)
		} else {
			v.showRecordingStop()
			v.keepScreenOn(false)
		}
		v.hideRecordProcessing()
		updateInformation(
				prefs.getSettingRecordingFormat(),
				prefs.getSettingSampleRate(),
				0
		)

		localRepository.setOnRecordsLostListener(object : OnRecordsLostListener {
			override fun onLostRecords(list: MutableList<Record>) {
				view?.showRecordsLostMessage(list)
			}
		})
	}

	override fun unbindView() {
		if (view != null) {
			playerCallback?.let { audioPlayer.removePlayerCallback(it) }
			appRecorderCallback?.let { appRecorder.removeRecordingCallback(it) }
			localRepository.setOnRecordsLostListener(null)
			view = null
		}
	}

	override fun clear() {
		if (view != null) {
			unbindView()
		}
		localRepository.close()
		audioPlayer.release()
		appRecorder.release()
		loadingTasks.close()
		recordingsTasks.close()
		recordDataSource.clearActiveRecord()
	}

	override fun checkFirstRun() {
		if (prefs.isFirstRun()) {
			view?.startWelcomeScreen()
		}
	}

	override fun storeInPrivateDir(context: Context) {
		if (prefs.isStoreDirPublic) {
			prefs.isStoreDirPublic = false
			fileRepository.updateRecordingDir(context, prefs)
		}
	}

	override fun setAudioRecorder(recorder: RecorderContract.Recorder) {
		appRecorder.setRecorder(recorder)
	}

	override fun pauseUnpauseRecording(context: Context) {
		try {
			if (fileRepository.hasAvailableSpace(context)) {
				if (appRecorder.isPaused) {
					appRecorder.resumeRecording()
				} else if (appRecorder.isRecording) {
					appRecorder.pauseRecording()
				}
			} else {
				view?.showError(R.string.error_no_available_space)
			}
		} catch (e: IllegalArgumentException) {
			view?.showError(R.string.error_failed_access_to_storage)
		}
	}

	override fun stopRecording() {
		if (appRecorder.isRecording) {
			view?.let { v ->
				v.showProgress()
				v.waveFormToStart()
			}
			audioPlayer.seek(0)
			appRecorder.stopRecording()
		}
	}

	override fun startPlayback() {
		if (audioPlayer.isPlaying()) {
			audioPlayer.pause()
		} else if (audioPlayer.isPaused()) {
			audioPlayer.unpause()
		} else {
			loadingTasks.postRunnable {
				val record = recordDataSource.getActiveRecord()
				if (record != null) {
					AndroidUtils.runOnUIThread {
						audioPlayer.play(record.path)
					}
				}
			}
		}
	}

	override fun onPlaybackClick(context: Context, isStorageAvailable: Boolean) {
		loadingTasks.postRunnable {
			val record = recordDataSource.getActiveRecord()
			if (record != null) {
				AndroidUtils.runOnUIThread {
					//此方法开始或暂停播放
					if (FileUtil.isFileInExternalStorage(context, record.path)) {
						if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
							view?.showRecordFileNotAvailable(record.path)
						} else if (isStorageAvailable) {
							startPlayback()
						}
					} else {
						startPlayback()
					}
				}
			}
		}
	}

	override fun seekPlayback(mills: Long) {
		audioPlayer.seek(mills)
	}

	override fun stopPlayback() {
		audioPlayer.stop()
	}

	override fun renameRecord(id: Long, newName: String, extension: String) {
		if (id < 0 || newName.isEmpty()) {
			AndroidUtils.runOnUIThread {
				view?.showError(R.string.error_failed_to_rename)
			}
			return
		}
		view?.showProgress()
		val name = FileUtil.removeUnallowedSignsFromName(newName)
		recordingsTasks.postRunnable {
			val record = localRepository.getRecord(id.toInt())
			if (record != null) {
				val nameWithExt = name + AppConstants.EXTENSION_SEPARATOR + extension
				val file = File(record.path)
				val renamed = File(file.parentFile!!.absolutePath + File.separator + nameWithExt)

				if (renamed.exists()) {
					AndroidUtils.runOnUIThread {
						view?.showError(R.string.error_file_exists)
					}
				} else {
					if (fileRepository.renameFile(record.path, name, extension)) {
						val recordUpdated = Record(
								record.id,
								name,
								record.duration,
								record.created,
								record.added,
								record.removed,
								renamed.absolutePath,
								record.format,
								record.size,
								record.sampleRate,
								record.channelCount,
								record.bitrate,
								record.isBookmarked,
								record.isWaveformProcessed,
								record.amps)
						if (localRepository.updateRecord(recordUpdated)) {
							recordDataSource.clearActiveRecord()
							AndroidUtils.runOnUIThread {
								view?.let { v ->
									v.hideProgress()
									v.showName(name)
								}
							}
						} else {
							AndroidUtils.runOnUIThread {
								view?.showError(R.string.error_failed_to_rename)
							}
							//本地库路径更新失败时恢复文件名
							if (renamed.exists()) {
								//尝试重命名 3 次
								if (!renamed.renameTo(file)) {
									if (!renamed.renameTo(file)) {
										renamed.renameTo(file)
									}
								}
							}
						}
					} else {
						AndroidUtils.runOnUIThread {
							view?.showError(R.string.error_failed_to_rename)
						}
					}
				}
				AndroidUtils.runOnUIThread {
					view?.hideProgress()
				}
			} else {
				AndroidUtils.runOnUIThread {
					view?.showError(R.string.error_failed_to_rename)
				}
			}
		}
	}

	override fun decodeRecord(id: Long) {
		loadingTasks.postRunnable {
			val rec = localRepository.getRecord(prefs.activeRecord.toInt())
			if (view != null && rec != null && rec.duration / 1000 < AppConstants.DECODE_DURATION && !rec.isWaveformProcessed) {
				view!!.decodeRecord(rec.id)
			}
		}
	}

	override fun loadActiveRecord() {
		if (!appRecorder.isRecording) {
			view?.showProgress()
			loadingTasks.postRunnable {
				val rec = recordDataSource.getActiveRecord()
				if (rec != null) {
					songDuration = rec.duration
					AndroidUtils.runOnUIThread {
						val v = view
						if (v != null) {
							if (audioPlayer.isPaused()) {
								val duration = songDuration / 1000
								if (duration > 0) {
									val playProgressMills = audioPlayer.getPauseTime()
									v.onPlayProgress(playProgressMills, (1000 * playProgressMills / duration).toInt())
									v.showWaveForm(rec.amps, songDuration, playProgressMills)
								}
							} else {
								v.showWaveForm(rec.amps, songDuration, 0)
							}

							v.showName(rec.name)
							v.showDuration(TimeUtils.formatTimeIntervalHourMinSec2(songDuration / 1000))
							v.showOptionsMenu()
							v.hideProgress()
							updateInformation(rec.format, rec.sampleRate, rec.size, rec.bitrate)
						}
					}
				} else {
					AndroidUtils.runOnUIThread {
						val v = view
						if (v != null) {
							v.hideProgress()
							v.showWaveForm(IntArray(0), 0, 0)
							v.showName("")
							v.showDuration(TimeUtils.formatTimeIntervalHourMinSec2(0))
							v.hideOptionsMenu()
						}
					}
				}
			}
		}
	}

	@Deprecated("Remove soon")
	override fun checkPublicStorageRecords() {
		if (!prefs.isPublicStorageMigrated) {
			loadingTasks.postRunnable {
				val lastTimeCheck = prefs.getLastPublicStorageMigrationAsked()
				val curTime = System.currentTimeMillis()
				if (curTime - lastTimeCheck > AppConstants.MIGRATE_PUBLIC_STORAGE_WARNING_COOLDOWN_MILLS &&
						localRepository.hasRecordsWithPath(fileRepository.publicDir.absolutePath)) {
					prefs.setLastPublicStorageMigrationAsked(curTime)
					AndroidUtils.runOnUIThread {
						view?.showMigratePublicStorageWarning()
					}
				}
			}
		}
	}

	override fun setAskToRename(value: Boolean) {
		prefs.setAskToRenameAfterStopRecording(value)
	}

	override fun updateRecordingDir(context: Context) {
		fileRepository.updateRecordingDir(context, prefs)
	}

	override fun setStoragePrivate(context: Context) {
		prefs.isStoreDirPublic = false
		fileRepository.updateRecordingDir(context, prefs)
	}

	override fun onShareRecordClick() {
		loadingTasks.postRunnable {
			val record = recordDataSource.getActiveRecord()
			if (record != null) {
				AndroidUtils.runOnUIThread {
					view?.shareRecord(record)
				}
			}
		}
	}

	override fun onRenameRecordClick() {
		loadingTasks.postRunnable {
			val record = recordDataSource.getActiveRecord()
			if (record != null) {
				AndroidUtils.runOnUIThread {
					view?.askRecordingNewName(record.id.toLong(), File(record.path), false)
				}
			}
		}
	}

	override fun onOpenFileClick() {
		loadingTasks.postRunnable {
			val record = recordDataSource.getActiveRecord()
			if (record != null) {
				AndroidUtils.runOnUIThread {
					view?.openFile(record)
				}
			}
		}
	}

	override fun onSaveAsClick() {
		loadingTasks.postRunnable {
			val record = recordDataSource.getActiveRecord()
			if (record != null) {
				AndroidUtils.runOnUIThread {
					view?.downloadRecord(record)
				}
			}
		}
	}

	override fun onDeleteClick() {
		loadingTasks.postRunnable {
			val record = recordDataSource.getActiveRecord()
			if (record != null) {
				AndroidUtils.runOnUIThread {
					view?.askDeleteRecord(record.name)
				}
			}
		}
	}

	private fun updateInformation(format: String, sampleRate: Int, size: Long) {
		updateInformation(format, sampleRate, size, 0)
	}

	private fun updateInformation(format: String, sampleRate: Int, size: Long, bitrate: Int) {
		val v = view ?: return
		var info = settingsMapper.formatSize(size) + AppConstants.SEPARATOR +
				settingsMapper.convertFormatsToString(format) + AppConstants.SEPARATOR +
				settingsMapper.convertSampleRateToString(sampleRate)
		if (AppConstants.FORMAT_MP3 == format && bitrate > 0) {
			info += AppConstants.SEPARATOR + settingsMapper.formatBitrate(bitrate / 1000)
		}
		v.showInformation(info)
	}

	override fun isStorePublic(): Boolean {
		return prefs.isStoreDirPublic
	}

	override fun deleteActiveRecord() {
		audioPlayer.stop()
		recordingsTasks.postRunnable {
			val rec = recordDataSource.getActiveRecord()
			if (rec != null && localRepository.deleteRecord(rec.id)) {
				prefs.activeRecord = -1
				AndroidUtils.runOnUIThread {
					val v = view
					if (v != null) {
						v.showWaveForm(IntArray(0), 0, 0)
						v.showName("")
						v.showDuration(TimeUtils.formatTimeIntervalHourMinSec2(0))
						v.showMessage(R.string.record_moved_into_trash)
						v.hideOptionsMenu()
						v.onPlayProgress(0, 0)
						v.hideProgress()
						recordDataSource.clearActiveRecord()
						updateInformation(
								prefs.getSettingRecordingFormat(),
								prefs.getSettingSampleRate(),
								0
						)
					}
				}
			}
		}
	}

	override fun onRecordInfo() {
		loadingTasks.postRunnable {
			val record = recordDataSource.getActiveRecord()
			if (record != null) {
				AndroidUtils.runOnUIThread {
					view?.showRecordInfo(Mapper.toRecordInfo(record))
				}
			}
		}
	}

	override fun disablePlaybackProgressListener() {
		listenPlaybackProgress = false
	}

	override fun enablePlaybackProgressListener() {
		listenPlaybackProgress = true
	}

	override fun importAudioFile(context: Context, uri: Uri) {
		view?.showImportStart()
		showImportProgress = true

		importTasks.postRunnable {
			try {
				val scheme = uri.scheme
				if (scheme == null || scheme != "content") {
					throw SecurityException("Only content:// URIs are supported for import")
				}
				val parcelFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
						?: throw IOException("Cannot open file descriptor")
				val fileDescriptor = parcelFileDescriptor.fileDescriptor
				val name = FileUtil.sanitizeFileName(extractFileName(context, uri))
						?: throw IOException("Invalid file name extracted from URI")

				val newFile = fileRepository.provideRecordFile(name)
				if (FileUtil.copyFile(fileDescriptor, newFile)) {
					val info: RecordInfo = AudioDecoder.readRecordInfo(newFile)

					//两步导入：1) 先入库空波形 2) 后台再处理并更新波形
					val r = Record(
							Record.NO_ID,
							FileUtil.removeFileExtension(newFile.name),
							if (info.duration >= 0) info.duration else 0,
							newFile.lastModified(),
							Date().time,
							Long.MAX_VALUE,
							newFile.absolutePath,
							info.format,
							info.size,
							info.sampleRate,
							info.channelCount,
							info.bitrate,
							false,
							false,
							IntArray(ARApplication.longWaveformSampleCount))
					val rec = localRepository.insertRecord(r)
					if (rec != null) {
						prefs.activeRecord = rec.id.toLong()
						songDuration = info.duration
						AndroidUtils.runOnUIThread {
							val v = view
							if (v != null) {
								audioPlayer.stop()
								v.showWaveForm(rec.amps, songDuration, 0)
								v.showName(rec.name)
								v.showDuration(TimeUtils.formatTimeIntervalHourMinSec2(songDuration / 1000))
								v.hideProgress()
								v.hideImportProgress()
								v.showOptionsMenu()
								updateInformation(rec.format, rec.sampleRate, rec.size, rec.bitrate)
							}
						}
						decodeRecord(rec.id.toLong())
					}
				}
			} catch (e: SecurityException) {
				Timber.e(e)
				AndroidUtils.runOnUIThread {
					view?.showError(R.string.error_permission_denied)
				}
			} catch (e: IOException) {
				Timber.e(e)
				AndroidUtils.runOnUIThread {
					view?.showError(R.string.error_unable_to_read_sound_file)
				}
			} catch (e: OutOfMemoryError) {
				Timber.e(e)
				AndroidUtils.runOnUIThread {
					view?.showError(R.string.error_unable_to_read_sound_file)
				}
			} catch (e: IllegalStateException) {
				Timber.e(e)
				AndroidUtils.runOnUIThread {
					view?.showError(R.string.error_unable_to_read_sound_file)
				}
			} catch (ex: CantCreateFileException) {
				AndroidUtils.runOnUIThread {
					view?.showError(ErrorParser.parseException(ex))
				}
			}
			AndroidUtils.runOnUIThread {
				view?.hideImportProgress()
			}
			showImportProgress = false
		}
	}

	private fun migrateDb3() {
		processingTasks.postRunnable {
			//更新 records 表
			val ids = localRepository.getAllItemsIds()
			for (i in ids.indices) {
				val rec = localRepository.getRecord(ids[i])
				if (rec != null) {
					val info = AudioDecoder.readRecordInfo(File(rec.path))
					localRepository.updateRecord(Record(
							rec.id,
							FileUtil.removeFileExtension(rec.name),
							rec.duration,
							rec.created,
							rec.added,
							rec.removed,
							rec.path,
							info.format,
							info.size,
							info.sampleRate,
							info.channelCount,
							info.bitrate,
							rec.isBookmarked,
							rec.isWaveformProcessed,
							rec.amps))
				}
			}
			//更新回收站表
			val trashIds = localRepository.getTrashRecordsIds()
			for (i in trashIds.indices) {
				val trashRecord = localRepository.getTrashRecord(trashIds[i])
				if (trashRecord != null) {
					val info = AudioDecoder.readRecordInfo(File(trashRecord.path))
					localRepository.updateTrashRecord(Record(
							trashRecord.id,
							FileUtil.removeFileExtension(trashRecord.name),
							trashRecord.duration,
							trashRecord.created,
							trashRecord.added,
							trashRecord.removed,
							trashRecord.path,
							info.format,
							info.size,
							info.sampleRate,
							info.channelCount,
							info.bitrate,
							trashRecord.isBookmarked,
							trashRecord.isWaveformProcessed,
							trashRecord.amps))
				}
			}
			prefs.migrateDb3Finished()
		}
	}

	private fun extractFileName(context: Context, uri: Uri): String? {
		val cursor: Cursor? = context.contentResolver.query(uri, null, null, null, null)
		try {
			if (cursor != null && cursor.moveToFirst()) {
				val name = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME))
				//TODO: 找到更好的提取文件扩展名的方式
				if (!name.contains(".")) {
					return name + ".m4a"
				}
				return name
			}
		} finally {
			cursor?.close()
		}
		return null
	}
}
