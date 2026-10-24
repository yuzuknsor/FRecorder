/*
 * Copyright 2019 Dmytro Ponomarenko
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
package com.vdo.frecorder.app.trash

import com.vdo.frecorder.Contract
import com.vdo.frecorder.app.info.RecordInfo
import com.vdo.frecorder.app.lostrecords.RecordItem

/** 回收站页契约。Kotlin 化（原 TrashContract.java）。 */
interface TrashContract {

	interface View : Contract.View {
		fun showRecords(items: List<RecordItem>)
		fun showRecordInfo(info: RecordInfo)
		fun recordDeleted(resId: Int)
		fun recordRestored(resId: Int)
		fun allRecordsRemoved()
		fun showEmpty()
		fun hideEmpty()
	}

	interface UserActionsListener : Contract.UserActionsListener<TrashContract.View> {
		fun onRecordInfo(info: RecordInfo)
		fun deleteRecordFromTrash(id: Int, path: String)
		fun deleteAllRecordsFromTrash()
		fun restoreRecordFromTrash(id: Int)
	}
}
