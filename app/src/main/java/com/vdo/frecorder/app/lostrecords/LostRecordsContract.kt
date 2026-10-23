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
package com.vdo.frecorder.app.lostrecords

import com.vdo.frecorder.Contract
import com.vdo.frecorder.app.info.RecordInfo

/** 丢失录音页契约。Kotlin 化（原 LostRecordsContract.java）。 */
interface LostRecordsContract {

	interface View : Contract.View {
		fun showLostRecords(items: List<RecordItem>)
		fun showRecordInfo(info: RecordInfo)
		fun onDeletedRecord(id: Int)
		fun showEmpty()
		fun hideEmpty()
	}

	interface UserActionsListener : Contract.UserActionsListener<View> {
		fun onRecordInfo(info: RecordInfo)
		fun deleteRecord(record: RecordItem)
		fun deleteRecords(list: List<RecordItem>)
	}
}
