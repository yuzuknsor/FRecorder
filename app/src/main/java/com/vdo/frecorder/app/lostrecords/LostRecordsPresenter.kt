/*
 * Copyright 2020 Dmytro Ponomarenko
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

package com.vdo.frecorder.app.lostrecords

import com.vdo.frecorder.BackgroundQueue
import com.vdo.frecorder.Mapper
import com.vdo.frecorder.app.info.RecordInfo
import com.vdo.frecorder.data.Prefs
import com.vdo.frecorder.data.database.LocalRepository
import com.vdo.frecorder.data.database.OnRecordsLostListener
import com.vdo.frecorder.data.database.Record
import com.vdo.frecorder.util.AndroidUtils

/** 丢失录音页逻辑。Kotlin 化（原 LostRecordsPresenter.java）。 */
class LostRecordsPresenter(
		private val loadingTasks: BackgroundQueue,
		private val recordingsTasks: BackgroundQueue,
		private val localRepository: LocalRepository,
		private val prefs: Prefs
) : LostRecordsContract.UserActionsListener {

	private var view: LostRecordsContract.View? = null

	override fun bindView(v: LostRecordsContract.View) {
		view = v

		localRepository.setOnRecordsLostListener(object : OnRecordsLostListener {
			override fun onLostRecords(lostRecords: MutableList<Record>) {
				AndroidUtils.runOnUIThread {
					val list = Mapper.toRecordItemList(lostRecords)
					val current = view
					if (current != null) {
						if (list.isEmpty()) {
							current.showEmpty()
						} else {
							current.showLostRecords(list)
							current.hideEmpty()
						}
					}
				}
			}
		})
		loadingTasks.postRunnable { localRepository.getAllRecords() }
	}

	override fun unbindView() {
		localRepository.setOnRecordsLostListener(null)
		view = null
	}

	override fun clear() {
		unbindView()
	}

	override fun deleteRecords(list: List<RecordItem>) {
		recordingsTasks.postRunnable {
			for (rec in list) {
				localRepository.deleteRecord(rec.id)
				if (prefs.activeRecord == rec.id.toLong()) {
					prefs.activeRecord = -1
				}
			}
			AndroidUtils.runOnUIThread {
				view?.showEmpty()
			}
		}
	}

	override fun onRecordInfo(info: RecordInfo) {
		view?.showRecordInfo(info)
	}

	override fun deleteRecord(rec: RecordItem) {
		recordingsTasks.postRunnable {
			localRepository.deleteRecord(rec.id)
			if (prefs.activeRecord == rec.id.toLong()) {
				prefs.activeRecord = -1
			}
			AndroidUtils.runOnUIThread {
				view?.onDeletedRecord(rec.id)
			}
		}
	}
}
