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
import android.net.Uri
import com.vdo.frecorder.Contract
import com.vdo.frecorder.IntArrayList
import com.vdo.frecorder.app.info.RecordInfo
import com.vdo.frecorder.audio.recorder.RecorderContract
import com.vdo.frecorder.data.database.Record
import java.io.File

/** 主界面契约。Kotlin 化（原 MainContract.java）。 */
interface MainContract {

	interface View : Contract.View {

		fun keepScreenOn(on: Boolean)
		fun showRecordingStart()
		fun showRecordingStop()
		fun showRecordingPause()
		fun showRecordingResume()
		fun onRecordingProgress(mills: Long, amp: Int)
		fun startWelcomeScreen()

		fun askRecordingNewName(id: Long, file: File, showCheckbox: Boolean)

		fun startRecordingService()

		fun startPlaybackService(name: String)

		fun showPlayStart(animate: Boolean)
		fun showPlayPause()
		fun showPlayStop()
		fun onPlayProgress(mills: Long, percent: Int)

		fun showImportStart()
		fun hideImportProgress()

		fun showOptionsMenu()
		fun hideOptionsMenu()

		fun showRecordProcessing()
		fun hideRecordProcessing()

		fun showWaveForm(waveForm: IntArray, duration: Long, playbackMills: Long)
		fun waveFormToStart()
		fun showDuration(duration: String)
		fun showRecordingProgress(progress: String)
		fun showName(name: String)
		fun showInformation(info: String)
		fun decodeRecord(id: Int)

		fun askDeleteRecord(name: String)

		fun showRecordInfo(info: RecordInfo)

		fun updateRecordingView(data: IntArrayList, durationMills: Long)

		fun showRecordsLostMessage(list: List<Record>)

		fun shareRecord(record: Record)

		fun openFile(record: Record)

		fun downloadRecord(record: Record)

		fun showMigratePublicStorageWarning()

		fun showRecordFileNotAvailable(path: String)

		fun onMonitoringDisabled()
	}

	interface UserActionsListener : Contract.UserActionsListener<View> {

		fun checkFirstRun()

		fun storeInPrivateDir(context: Context)

		fun setAudioRecorder(recorder: RecorderContract.Recorder)

		fun pauseUnpauseRecording(context: Context)
		fun stopRecording()

		fun startPlayback()
		fun onPlaybackClick(context: Context, isStorageAvailable: Boolean)
		fun seekPlayback(mills: Long)
		fun stopPlayback()

		fun renameRecord(id: Long, name: String, extension: String)

		fun decodeRecord(id: Long)

		fun loadActiveRecord()

		fun checkPublicStorageRecords()

		fun setAskToRename(value: Boolean)

		fun importAudioFile(context: Context, uri: Uri)

		fun updateRecordingDir(context: Context)

		fun setStoragePrivate(context: Context)

		fun onShareRecordClick()

		fun onRenameRecordClick()

		fun onOpenFileClick()

		fun onSaveAsClick()

		fun onDeleteClick()

		fun isStorePublic(): Boolean

		fun deleteActiveRecord()

		fun onRecordInfo()

		fun disablePlaybackProgressListener()

		fun enablePlaybackProgressListener()
	}
}
