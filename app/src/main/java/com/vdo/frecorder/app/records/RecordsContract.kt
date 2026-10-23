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

import com.vdo.frecorder.Contract
import com.vdo.frecorder.app.info.RecordInfo
import com.vdo.frecorder.data.database.Record

/** 录音列表页契约。Kotlin 化（原 RecordsContract.java）。 */
interface RecordsContract {

	interface View : Contract.View {

		fun showPlayStart()
		fun showPlayPause()
		fun showPlayStop()
		fun onPlayProgress(mills: Long, percent: Int)

		fun showNextRecord()
		fun showPrevRecord()

		fun showTrashBtn()
		fun hideTrashBtn()

		fun showPlayerPanel()

		fun startPlaybackService()

		fun showWaveForm(waveForm: IntArray, duration: Long, playbackMills: Long)
		fun showDuration(duration: String)

		fun showRecords(records: List<ListItem>, order: Int)
		fun addRecords(records: List<ListItem>, order: Int)

		fun showEmptyList()
		fun showEmptyBookmarksList()

		fun showPanelProgress()
		fun hidePanelProgress()

		fun decodeRecord(id: Int)

		fun showRecordName(name: String)

		fun showRename(record: Record?)

		fun onDeleteRecord(id: Long)

		fun hidePlayPanel()

		fun addedToBookmarks(id: Int, isActive: Boolean)
		fun removedFromBookmarks(id: Int, isActive: Boolean)

		fun showSortType(type: Int)

		fun showActiveRecord(id: Int)

		fun bookmarksSelected()
		fun bookmarksUnselected()

		fun showRecordInfo(info: RecordInfo)

		fun showRecordsLostMessage(list: List<Record>)

		fun cancelMultiSelect()
	}

	interface UserActionsListener : Contract.UserActionsListener<View> {

		fun onResumeView()

		fun startPlayback()

		fun pausePlayback()

		fun seekPlayback(mills: Long)

		fun stopPlayback()

		fun playNext()

		fun playPrev()

		fun deleteActiveRecord()

		fun deleteRecord(id: Long, path: String)

		fun deleteRecords(ids: List<Long>)

		fun renameRecord(id: Long, name: String, extension: String)

		fun loadRecords()

		fun updateRecordsOrder(order: Int)

		fun loadRecordsPage(page: Int)

		fun decodeActiveRecord()

		fun applyBookmarksFilter()
		fun checkBookmarkActiveRecord()

		fun addToBookmark(id: Int)
		fun removeFromBookmarks(id: Int)

		fun setActiveRecord(id: Long, callback: Callback)

		fun onRenameClick()

		fun getActiveRecordId(): Long

		fun getActiveRecordPath(): String?

		fun getRecordName(): String

		fun onRecordInfo(info: RecordInfo)

		fun disablePlaybackProgressListener()

		fun enablePlaybackProgressListener()
	}

	interface Callback {
		fun onSuccess()
		fun onError(e: Exception)
	}
}
