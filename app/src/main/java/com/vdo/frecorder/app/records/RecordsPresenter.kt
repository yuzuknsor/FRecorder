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

package com.vdo.frecorder.app.records

import com.vdo.frecorder.AppConstants
import com.vdo.frecorder.BackgroundQueue
import com.vdo.frecorder.Mapper
import com.vdo.frecorder.R
import com.vdo.frecorder.app.AppRecorder
import com.vdo.frecorder.app.AppRecorderCallback
import com.vdo.frecorder.app.info.RecordInfo
import com.vdo.frecorder.audio.monitor.AudioMonitor
import com.vdo.frecorder.audio.player.PlayerContractNew
import com.vdo.frecorder.data.FileRepository
import com.vdo.frecorder.data.Prefs
import com.vdo.frecorder.data.database.LocalRepository
import com.vdo.frecorder.data.database.OnRecordsLostListener
import com.vdo.frecorder.data.database.Record
import com.vdo.frecorder.exception.AppException
import com.vdo.frecorder.exception.ErrorParser
import com.vdo.frecorder.util.AndroidUtils
import com.vdo.frecorder.util.FileUtil
import com.vdo.frecorder.util.TimeUtils
import timber.log.Timber
import java.io.File

/** 录音列表页逻辑。Kotlin 化（原 RecordsPresenter.java）。 */
class RecordsPresenter(
		private val localRepository: LocalRepository,
		private val fileRepository: FileRepository,
		private val loadingTasks: BackgroundQueue,
		private val recordingsTasks: BackgroundQueue,
		private val audioPlayer: PlayerContractNew.Player,
		private val appRecorder: AppRecorder,
		private val prefs: Prefs
) : RecordsContract.UserActionsListener {

	private var view: RecordsContract.View? = null

	private var playerCallback: PlayerContractNew.PlayerCallback? = null
	private var appRecorderCallback: AppRecorderCallback? = null

	private var activeRecord: Record? = null
	private var showBookmarks = false
	private var listenPlaybackProgress = true

	override fun bindView(v: RecordsContract.View) {
		view = v

		if (appRecorderCallback == null) {
			appRecorderCallback = object : AppRecorderCallback {
				override fun onRecordingStarted(file: File) {}

				override fun onRecordingPaused() {}

				override fun onRecordingResumed() {}

				override fun onRecordingProgress(mills: Long, amp: Int) {}

				override fun onRecordingStopped(file: File, rec: Record) {
					loadRecords()
				}

				override fun onError(e: AppException) {
					view?.showError(ErrorParser.parseException(e))
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
					view?.let { v ->
						v.startPlaybackService()
						v.showPlayStart()
					}
				}

				override fun onPlayProgress(mills: Long) {
					if (view != null && listenPlaybackProgress) {
						val rec = activeRecord
						if (rec != null) {
							val duration = rec.duration / 1000
							if (duration > 0) {
								view!!.onPlayProgress(mills, (1000 * mills / duration).toInt())
							}
						}
					}
				}

				override fun onStopPlay() {
					view?.showPlayStop()
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
			view?.let { v ->
				v.showPlayerPanel()
				v.showPlayStart()
			}
		} else if (audioPlayer.isPaused()) {
			view?.let { v ->
				v.showPlayerPanel()
				v.showPlayPause()
			}
		}
		view?.showSortType(prefs.getRecordsOrder())

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
	}

	override fun onResumeView() {
		loadingTasks.postRunnable {
			val count = localRepository.getTrashRecordsCount()
			AndroidUtils.runOnUIThread {
				val v = view
				if (v != null) {
					if (count > 0) {
						v.showTrashBtn()
					} else {
						v.hideTrashBtn()
					}
				}
			}
		}
	}

	override fun startPlayback() {
		if (!appRecorder.isRecording()) {
			val rec = activeRecord
			if (rec != null) {
				if (audioPlayer.isPlaying()) {
					audioPlayer.pause()
				} else if (audioPlayer.isPaused()) {
					audioPlayer.unpause()
				} else {
					audioPlayer.play(rec.path)
				}
			}
		}
	}

	override fun pausePlayback() {
		audioPlayer.pause()
	}

	override fun seekPlayback(mills: Long) {
		audioPlayer.seek(mills)
	}

	override fun stopPlayback() {
		if (audioPlayer.isPlaying() || audioPlayer.isPaused()) {
			audioPlayer.stop()
		}
	}

	override fun playNext() {
	}

	override fun playPrev() {
	}

	override fun deleteActiveRecord() {
		val rec = activeRecord
		if (rec != null) {
			deleteRecord(rec.id.toLong(), rec.path)
		}
	}

	override fun deleteRecord(id: Long, path: String) {
		val rec = activeRecord
		if (rec != null && rec.id.toLong() == id) {
			audioPlayer.stop()
		}
		recordingsTasks.postRunnable {
			if (localRepository.deleteRecord(id.toInt())) {
				if (rec != null && rec.id.toLong() == id) {
					prefs.activeRecord = -1
				}
				AndroidUtils.runOnUIThread {
					val v = view
					if (v != null) {
						v.showTrashBtn()
						v.onDeleteRecord(id)
						v.showMessage(R.string.record_moved_into_trash)
						if (rec != null && rec.id.toLong() == id) {
							v.hidePlayPanel()
							activeRecord = null
						}
					}
				}
			}
		}
	}

	override fun deleteRecords(ids: List<Long>) {
		recordingsTasks.postRunnable {
			for (id in ids) {
				if (localRepository.deleteRecord(id.toInt())) {
					AndroidUtils.runOnUIThread {
						view?.let { v ->
							v.showTrashBtn()
							v.onDeleteRecord(id)
						}
					}
				}
			}
			AndroidUtils.runOnUIThread {
				view?.let { v ->
					v.cancelMultiSelect()
					v.showMessage(R.string.selected_records_moved_into_trash)
				}
			}
		}
	}

	override fun renameRecord(id: Long, n: String, extension: String) {
		if (id < 0 || n.isEmpty()) {
			AndroidUtils.runOnUIThread {
				view?.showError(R.string.error_failed_to_rename)
			}
			return
		}
		view?.showProgress()
		val name = FileUtil.removeUnallowedSignsFromName(n)
		recordingsTasks.postRunnable {
			val rec2 = localRepository.getRecord(id.toInt())
			if (rec2 != null) {
				val nameWithExt = name + AppConstants.EXTENSION_SEPARATOR + extension
				val file = File(rec2.path)
				val renamed = File(file.parentFile!!.absolutePath + File.separator + nameWithExt)

				if (renamed.exists()) {
					AndroidUtils.runOnUIThread {
						view?.showError(R.string.error_file_exists)
					}
				} else {
					if (fileRepository.renameFile(rec2.path, name, extension)) {
						val renamedRecord = Record(
								rec2.id,
								name,
								rec2.duration,
								rec2.created,
								rec2.added,
								rec2.removed,
								renamed.absolutePath,
								rec2.format,
								rec2.size,
								rec2.sampleRate,
								rec2.channelCount,
								rec2.bitrate,
								rec2.isBookmarked,
								rec2.isWaveformProcessed,
								rec2.amps)
						if (localRepository.updateRecord(renamedRecord)) {
							AndroidUtils.runOnUIThread {
								val v = view
								if (v != null) {
									v.hideProgress()
									loadRecords()
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
			} else {
				AndroidUtils.runOnUIThread {
					view?.showError(R.string.error_failed_to_rename)
				}
			}
			AndroidUtils.runOnUIThread {
				view?.hideProgress()
			}
		}
	}

	override fun loadRecords() {
		val v = view ?: return
		v.showProgress()
		v.showPanelProgress()
		loadingTasks.postRunnable {
			val order = prefs.getRecordsOrder()
			val recordList = localRepository.getRecords(0, order)
			val rec = localRepository.getRecord(prefs.activeRecord.toInt())
			activeRecord = rec
			AndroidUtils.runOnUIThread {
				val vv = view
				if (vv != null) {
					vv.showRecords(Mapper.recordsToListItems(recordList), order)
					if (audioPlayer.isPaused() || audioPlayer.isPlaying()) {
						if (rec != null) {
							if (audioPlayer.isPaused()) {
								val duration = rec.duration / 1000
								if (duration > 0) {
									val playProgressMills = audioPlayer.getPauseTime()
									vv.onPlayProgress(playProgressMills, (1000 * playProgressMills / duration).toInt())
									vv.showWaveForm(rec.amps, rec.duration, playProgressMills)
								}
							} else {
								vv.showWaveForm(rec.amps, rec.duration, 0)
							}
							vv.showDuration(TimeUtils.formatTimeIntervalHourMinSec2(rec.duration / 1000))
							vv.showRecordName(rec.name)
							if (rec.isBookmarked) {
								vv.bookmarksSelected()
							} else {
								vv.bookmarksUnselected()
							}
							if (audioPlayer.isPlaying() || audioPlayer.isPaused()) {
								vv.showActiveRecord(rec.id)
							}
						}
					}

					vv.hideProgress()
					vv.hidePanelProgress()
					vv.bookmarksUnselected()
					if (recordList.isEmpty()) {
						vv.showEmptyList()
					}
				}
			}
		}
	}

	override fun updateRecordsOrder(order: Int) {
		prefs.setRecordOrder(order)
		view?.showSortType(order)
		loadRecords()
	}

	override fun loadRecordsPage(page: Int) {
		if (view != null && !showBookmarks) {
			view!!.showProgress()
			view!!.showPanelProgress()
			loadingTasks.postRunnable {
				val order = prefs.getRecordsOrder()
				val recordList = localRepository.getRecords(page, order)
				AndroidUtils.runOnUIThread {
					val v = view
					if (v != null) {
						v.addRecords(Mapper.recordsToListItems(recordList), order)
						v.hideProgress()
						v.hidePanelProgress()
						v.bookmarksUnselected()
					}
				}
			}
		}
	}

	override fun decodeActiveRecord() {
		val v = view
		if (v != null && activeRecord != null) {
			v.decodeRecord(activeRecord!!.id)
		}
	}

	fun loadBookmarks() {
		if (!showBookmarks) {
			loadRecords()
		} else {
			val v = view ?: return
			v.showProgress()
			v.showPanelProgress()
			loadingTasks.postRunnable {
				val recordList = localRepository.getBookmarks()
				AndroidUtils.runOnUIThread {
					val vv = view
					if (vv != null) {
						vv.showRecords(Mapper.recordsToListItems(recordList), AppConstants.SORT_DATE)
						vv.hideProgress()
						vv.hidePanelProgress()
						vv.bookmarksSelected()
						if (recordList.isEmpty()) {
							vv.showEmptyBookmarksList()
						}
					}
				}
			}
		}
	}

	override fun applyBookmarksFilter() {
		showBookmarks = !showBookmarks
		loadBookmarks()
	}

	override fun checkBookmarkActiveRecord() {
		recordingsTasks.postRunnable {
			val rec = activeRecord
			if (rec != null) {
				val success: Boolean
				if (rec.isBookmarked) {
					success = localRepository.removeFromBookmarks(rec.id)
				} else {
					success = localRepository.addToBookmarks(rec.id)
				}
				if (success) {
					rec.setBookmark(!rec.isBookmarked)

					AndroidUtils.runOnUIThread {
						val v = view
						if (v != null) {
							if (rec.isBookmarked) {
								v.addedToBookmarks(rec.id, true)
							} else {
								v.removedFromBookmarks(rec.id, true)
							}
						}
					}
				}
			}
		}
	}

	override fun addToBookmark(id: Int) {
		recordingsTasks.postRunnable {
			val r = localRepository.getRecord(id)
			if (r != null) {
				if (localRepository.addToBookmarks(r.id)) {
					AndroidUtils.runOnUIThread {
						view?.addedToBookmarks(r.id, activeRecord != null && r.id.toLong() == activeRecord!!.id.toLong())
					}
				}
			}
		}
	}

	override fun removeFromBookmarks(id: Int) {
		recordingsTasks.postRunnable {
			val r = localRepository.getRecord(id)
			if (r != null) {
				localRepository.removeFromBookmarks(r.id)
				AndroidUtils.runOnUIThread {
					view?.removedFromBookmarks(r.id, activeRecord != null && r.id.toLong() == activeRecord!!.id.toLong())
				}
			}
		}
	}

	override fun setActiveRecord(id: Long, callback: RecordsContract.Callback) {
		if (id >= 0 && !appRecorder.isRecording()) {
			prefs.activeRecord = id
			view?.showPanelProgress()
			loadingTasks.postRunnable {
				val rec = localRepository.getRecord(id.toInt())
				activeRecord = rec
				if (rec != null) {
					AndroidUtils.runOnUIThread {
						val v = view
						if (v != null) {
							v.showWaveForm(rec.amps, rec.duration, 0)
							v.showDuration(TimeUtils.formatTimeIntervalHourMinSec2(rec.duration / 1000))
							v.showRecordName(rec.name)
							callback.onSuccess()
							if (rec.isBookmarked) {
								v.addedToBookmarks(rec.id, true)
							} else {
								v.removedFromBookmarks(rec.id, true)
							}
							v.hidePanelProgress()
							v.showPlayerPanel()
						}
					}
				} else {
					AndroidUtils.runOnUIThread {
						callback.onError(Exception("Record is NULL!"))
						view?.hidePanelProgress()
					}
				}
			}
		}
	}

	override fun onRenameClick() {
		view?.showRename(activeRecord)
	}

	override fun getActiveRecordId(): Long {
		return prefs.activeRecord
	}

	override fun getActiveRecordPath(): String? {
		return activeRecord?.path
	}

	override fun getRecordName(): String {
		return activeRecord?.name ?: "Record"
	}

	override fun onRecordInfo(info: RecordInfo) {
		view?.showRecordInfo(info)
	}

	override fun disablePlaybackProgressListener() {
		listenPlaybackProgress = false
	}

	override fun enablePlaybackProgressListener() {
		listenPlaybackProgress = true
	}
}
