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

package com.vdo.frecorder.app.browser

import android.content.Context
import com.vdo.frecorder.Contract
import com.vdo.frecorder.app.info.RecordInfo

/** 文件浏览器页契约。Kotlin 化（原 FileBrowserContract.java）。 */
interface FileBrowserContract {

	interface View : Contract.View {
		fun showFileItems(items: List<RecordInfo>)
		fun showSelectedPrivateDir()
		fun showSelectedPublicDir()
		fun showRecordInfo(info: RecordInfo)
		fun onDeletedRecord(path: String)
		fun onImportedRecord(path: String)
		fun updatePath(path: String)
		fun decodeRecord(id: Int)
		fun showTabs(value: Boolean)

		fun showEmpty()
		fun hideEmpty()

		fun showRecordProcessing()
		fun hideRecordProcessing()

		fun showImportStart()
		fun hideImportProgress()
	}

	interface UserActionsListener : Contract.UserActionsListener<FileBrowserContract.View> {
		fun selectPrivateDir(context: Context)
		fun selectPublicDir(context: Context)
		fun loadFiles(context: Context)
		fun onRecordInfo(info: RecordInfo)
		fun deleteRecord(record: RecordInfo)
		fun importAudioFile(context: Context, info: RecordInfo)
	}
}
