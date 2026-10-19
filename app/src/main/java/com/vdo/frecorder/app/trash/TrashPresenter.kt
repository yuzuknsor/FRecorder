/*
 * Copyright 2020 Dmytro Ponomarenko
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.vdo.frecorder.app.trash

import com.vdo.frecorder.BackgroundQueue
import com.vdo.frecorder.Mapper
import com.vdo.frecorder.R
import com.vdo.frecorder.app.info.RecordInfo
import com.vdo.frecorder.app.lostrecords.RecordItem
import com.vdo.frecorder.data.FileRepository
import com.vdo.frecorder.data.database.LocalRepository
import com.vdo.frecorder.exception.ErrorParser
import com.vdo.frecorder.exception.FailedToRestoreRecord
import com.vdo.frecorder.util.AndroidUtils

/** 回收站页逻辑。Kotlin 化（原 TrashPresenter.java）。 */
class TrashPresenter(
		private val loadingTasks: BackgroundQueue,
		private val recordingsTasks: BackgroundQueue,
		private val fileRepository: FileRepository,
		private val localRepository: LocalRepository
) : TrashContract.UserActionsListener {

	private var view: TrashContract.View? = null

	override fun bindView(v: TrashContract.View) {
		view = v

		loadingTasks.postRunnable {
			val list: List<RecordItem> = Mapper.toRecordItemList(localRepository.getTrashRecords())
			AndroidUtils.runOnUIThread {
				view?.let { vw ->
					if (list.isEmpty()) {
						vw.showEmpty()
					} else {
						vw.showRecords(list)
						vw.hideEmpty()
					}
				}
			}
		}
	}

	override fun unbindView() {
		view = null
	}

	override fun clear() {
		unbindView()
	}

	override fun onRecordInfo(info: RecordInfo) {
		view?.showRecordInfo(info)
	}

	override fun deleteRecordFromTrash(id: Int, path: String) {
		recordingsTasks.postRunnable {
			if (fileRepository.deleteRecordFile(path)) {
				removeFromTrash(id)
			} else if (fileRepository.deleteRecordFile(path)) {
				//再试一次
				removeFromTrash(id)
			} else {
				AndroidUtils.runOnUIThread {
					view?.showMessage(R.string.error_failed_to_delete)
				}
			}
		}
	}

	private fun removeFromTrash(id: Int) {
		localRepository.removeFromTrash(id)
		AndroidUtils.runOnUIThread {
			view?.let { v ->
				v.showMessage(R.string.record_deleted_successfully)
				v.recordDeleted(id)
			}
		}
	}

	override fun deleteAllRecordsFromTrash() {
		recordingsTasks.postRunnable {
			val records = localRepository.getTrashRecords()
			for (record in records) {
				fileRepository.deleteRecordFile(record.path)
			}
			localRepository.emptyTrash()
			AndroidUtils.runOnUIThread {
				view?.let { v ->
					v.showMessage(R.string.all_records_deleted_successfully)
					v.allRecordsRemoved()
				}
			}
		}
	}

	override fun restoreRecordFromTrash(id: Int) {
		recordingsTasks.postRunnable {
			try {
				localRepository.restoreFromTrash(id)
				AndroidUtils.runOnUIThread {
					view?.let { v ->
						v.showMessage(R.string.record_restored_successfully)
						v.recordRestored(id)
					}
				}
			} catch (e: FailedToRestoreRecord) {
				AndroidUtils.runOnUIThread {
					view?.showMessage(ErrorParser.parseException(e))
				}
			}
		}
	}
}
