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

package com.vdo.frecorder.app.settings

import android.content.Context
import android.media.AudioDeviceInfo
import com.vdo.frecorder.Contract
import java.io.File

/** 设置页契约。Kotlin 化（原 SettingsContract.java）。 */
class SettingsContract {

	interface View : Contract.View {

		fun showStoreInPublicDir(b: Boolean)

		fun showDirectorySetting(b: Boolean)

		fun showMigratePublicStorage(b: Boolean)

		fun showKeepScreenOn(b: Boolean)

		fun showAskToRenameAfterRecordingStop(b: Boolean)

		fun showRecordingBitrate(bitrate: Int)

		fun showRecordingSampleRate(rate: Int)

		fun showRecordingFormat(formatKey: String)

		fun showNamingFormat(namingKey: String)

		fun showChannelCount(count: Int)

		fun showOutputFormat(outputFormatKey: String)

		fun showBitDepth(bitDepth: Int)

		fun showAudioSourceSetting(selectedDeviceId: Int, devices: List<AudioDeviceInfo>)

		fun showGainBoostLevel(level: Int)

		fun showNoiseReductionEnabled(enabled: Boolean)

		fun showAllRecordsDeleted()

		fun showFailDeleteAllRecords()

		fun showTotalRecordsDuration(duration: String)
		fun showRecordsCount(count: Int)
		fun showAvailableSpace(space: String)

		fun showBitrateSelector()
		fun hideBitrateSelector()

		fun showDialogPublicDirInfo()

		fun showDialogPrivateDirInfo()

		fun updateRecordingInfo(format: String)

		fun showSizePerMin(size: String)
		fun showInformation(info: String)

		fun showRecordsLocation(location: String)
		fun hideRecordsLocation()
		fun openRecordsLocation(file: File)

		fun enableAudioSettings()
		fun disableAudioSettings()
	}

	interface UserActionsListener : Contract.UserActionsListener<SettingsContract.View> {

		fun loadSettings()

		fun storeInPublicDir(context: Context, b: Boolean)

		fun keepScreenOn(b: Boolean)

		fun askToRenameAfterRecordingStop(b: Boolean)

		fun setSettingRecordingBitrate(bitrate: Int)

		fun setSettingSampleRate(rate: Int)

		fun setSettingChannelCount(count: Int)

		fun setSettingThemeColor(colorKey: String)

		fun setSettingNamingFormat(namingKey: String)

		fun setSettingRecordingFormat(formatKey: String)

		fun setSettingOutputFormat(outputFormatKey: String)

		fun setSettingBitDepth(bitDepth: Int)

		fun setSettingAudioSource(deviceId: Int)

		fun setGainBoostLevel(level: Int)

		fun setNoiseReductionEnabled(enabled: Boolean)

		fun refreshAudioDevices()

		fun deleteAllRecords()

		fun resetSettings()

		fun onRecordsLocationClick()
	}
}
