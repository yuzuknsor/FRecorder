/*
 * Copyright 2020 Dmytro Ponomarenko
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.vdo.frecorder.app.browser

import android.content.Context
import android.os.Build
import com.vdo.frecorder.ARApplication
import com.vdo.frecorder.AppConstants
import com.vdo.frecorder.BackgroundQueue
import com.vdo.frecorder.R
import com.vdo.frecorder.app.AppRecorder
import com.vdo.frecorder.app.AppRecorderCallback
import com.vdo.frecorder.app.info.RecordInfo
import com.vdo.frecorder.audio.AudioDecoder
import com.vdo.frecorder.data.FileRepository
import com.vdo.frecorder.data.Prefs
import com.vdo.frecorder.data.database.LocalRepository
import com.vdo.frecorder.data.database.Record
import com.vdo.frecorder.exception.AppException
import com.vdo.frecorder.exception.ErrorParser
import com.vdo.frecorder.util.AndroidUtils
import timber.log.Timber
import java.io.File
import java.util.Date

/** 文件浏览页逻辑。Kotlin 化（原 FileBrowserPresenter.java）。 */
class FileBrowserPresenter(
		prefs: Prefs,
		private val appRecorder: AppRecorder,
		private val importTasks: BackgroundQueue,
		private val loadingTasks: BackgroundQueue,
		private val recordingsTasks: BackgroundQueue,
		private val localRepository: LocalRepository,
		private val fileRepository: FileRepository
) : FileBrowserContract.UserActionsListener {

	private var view: FileBrowserContract.View? = null

	private var appRecorderCallback: AppRecorderCallback? = null

	private var selectedTab: Int = if (prefs.isStoreDirPublic) TAB_PUBLIC_DIR else TAB_PRIVATE_DIR

	override fun bindView(v: FileBrowserContract.View) {
		view = v

		if (appRecorderCallback == null) {
			appRecorderCallback = object : AppRecorderCallback {
				override fun onRecordingStarted(file: File) {}
				override fun onRecordingPaused() {}
				override fun onRecordingResumed() {}
				override fun onRecordingStopped(file: File, record: Record?) {}
				override fun onRecordingProgress(mills: Long, amp: Int) {}
				override fun onError(throwable: AppException) {
					Timber.e(throwable)
					view?.showError(ErrorParser.parseException(throwable))
				}
			}
		}
		appRecorderCallback?.let { appRecorder.addRecordingCallback(it) }
		v.showTabs(Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)
	}

	override fun unbindView() {
		localRepository.setOnRecordsLostListener(null)
		view = null
	}

	override fun clear() {
		unbindView()
	}

	override fun selectPrivateDir(context: Context) {
		if (selectedTab != TAB_PRIVATE_DIR) {
			selectedTab = TAB_PRIVATE_DIR
			loadFiles(context)
		}
	}

	override fun selectPublicDir(context: Context) {
		if (selectedTab != TAB_PUBLIC_DIR) {
			selectedTab = TAB_PUBLIC_DIR
			loadFiles(context)
		}
	}

	override fun loadFiles(context: Context) {
		updatePath(context)
		view?.showProgress()
		loadingTasks.postRunnable {
			val files: Array<File>? = if (selectedTab == TAB_PRIVATE_DIR) {
				fileRepository.getPrivateDirFiles(context)
			} else {
				fileRepository.getPublicDirFiles()
			}
			val items = ArrayList<RecordInfo>()
			if (files != null) {
				for (file in files) {
					val rec = localRepository.findRecordByPath(file.absolutePath)
					val r = AudioDecoder.readRecordInfo(file)
					r.isInDatabase = rec != null
					items.add(r)
				}
			}
			AndroidUtils.runOnUIThread {
				view?.let { v ->
					v.hideProgress()
					if (items.isEmpty()) {
						v.showEmpty()
					} else {
						v.showFileItems(items)
						v.hideEmpty()
					}
				}
			}
		}
	}

	override fun onRecordInfo(info: RecordInfo) {
		view?.showRecordInfo(info)
	}

	override fun deleteRecord(record: RecordInfo) {
		recordingsTasks.postRunnable {
			if (fileRepository.deleteRecordFile(record.location)) {
				AndroidUtils.runOnUIThread {
					view?.onDeletedRecord(record.location)
				}
			}
		}
	}

	override fun importAudioFile(context: Context, info: RecordInfo) {
		view?.showImportStart()

		importTasks.postRunnable {
			try {
				val file = File(info.location)

				//两步导入：1) 先插入空波形记录 2) 后台解码补波形
				val r = Record(
						Record.NO_ID,
						info.name,
						if (info.duration >= 0) info.duration else 0,
						file.lastModified(),
						Date().time,
						Long.MAX_VALUE,
						info.location,
						info.format,
						info.size,
						info.sampleRate,
						info.channelCount,
						info.bitrate,
						false,
						false,
						IntArray(ARApplication.longWaveformSampleCount)
				)
				val rec = localRepository.insertRecord(r)
				if (rec != null) {
					AndroidUtils.runOnUIThread {
						view?.let { v ->
							v.hideImportProgress()
							v.onImportedRecord(info.location)
						}
					}
					if (view != null && !rec.isWaveformProcessed && rec.duration / 1000 < AppConstants.DECODE_DURATION) {
						view?.decodeRecord(rec.id)
					}
				}
			} catch (e: SecurityException) {
				Timber.e(e)
				AndroidUtils.runOnUIThread {
					view?.showError(R.string.error_permission_denied)
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
			}
		}
	}

	private fun updatePath(context: Context) {
		view?.let { v ->
			if (selectedTab == TAB_PRIVATE_DIR) {
				val dir = fileRepository.getPrivateDir(context)
				if (dir != null) {
					v.updatePath(dir.absolutePath)
					v.showSelectedPrivateDir()
				}
			} else {
				v.updatePath(fileRepository.publicDir.absolutePath)
				v.showSelectedPublicDir()
			}
		}
	}

	companion object {
		const val TAB_PRIVATE_DIR = 1
		const val TAB_PUBLIC_DIR = 2
	}
}
