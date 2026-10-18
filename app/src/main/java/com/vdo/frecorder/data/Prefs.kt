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

package com.vdo.frecorder.data

/**
 * 全部用户设置与轻量状态。读写实现见 PrefsImpl。
 */
interface Prefs {

    fun isFirstRun(): Boolean
    fun firstRunExecuted()

    var isStoreDirPublic: Boolean

    // 作用域存储（scoped storage）需要的开关
    fun isShowDirectorySetting(): Boolean

    fun isAskToRenameAfterStopRecording(): Boolean
    fun hasAskToRenameAfterStopRecordingSetting(): Boolean
    fun setAskToRenameAfterStopRecording(b: Boolean)

    var isPublicStorageMigrated: Boolean
    fun getLastPublicStorageMigrationAsked(): Long
    fun setLastPublicStorageMigrationAsked(time: Long)

    var activeRecord: Long

    fun getRecordCounter(): Long
    fun incrementRecordCounter()

    var isKeepScreenOn: Boolean

    fun setRecordOrder(order: Int)
    fun getRecordsOrder(): Int

    val isMigratedSettings: Boolean
    fun migrateSettings()

    fun isMigratedDb3(): Boolean
    fun migrateDb3Finished()

    fun setSettingThemeColor(colorKey: String)
    fun getSettingThemeColor(): String

    fun setSettingNamingFormat(nameKay: String)
    fun getSettingNamingFormat(): String

    fun setSettingRecordingFormat(formatKey: String)
    fun getSettingRecordingFormat(): String

    fun setSettingSampleRate(sampleRate: Int)
    fun getSettingSampleRate(): Int

    fun setSettingBitrate(rate: Int)
    fun getSettingBitrate(): Int

    fun setSettingChannelCount(count: Int)
    fun getSettingChannelCount(): Int

    fun setSettingOutputFormat(outputFormat: String)
    fun getSettingOutputFormat(): String

    fun setSettingBitDepth(bitDepth: Int)
    fun getSettingBitDepth(): Int

    fun setSettingAudioSource(deviceId: Int)
    fun getSettingAudioSource(): Int

    fun setGainBoostLevel(level: Int)
    fun getGainBoostLevel(): Int

    fun setNoiseReductionEnabled(enabled: Boolean)
    fun isNoiseReductionEnabled(): Boolean

    fun setNoiseReductionDb(db: Float)
    fun getNoiseReductionDb(): Float

    fun setNoiseReductionSensitivity(sensitivity: Float)
    fun getNoiseReductionSensitivity(): Float

    fun setNoiseReductionFreqSmoothing(bands: Int)
    fun getNoiseReductionFreqSmoothing(): Int

    fun setNoiseProfileSeconds(seconds: Float)
    fun getNoiseProfileSeconds(): Float

    fun setHpfMode(mode: Int)
    fun getHpfMode(): Int

    fun setLpfMode(mode: Int)
    fun getLpfMode(): Int

    fun setNoiseGateEnabled(enabled: Boolean)
    fun isNoiseGateEnabled(): Boolean

    fun resetSettings()
}
