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

import android.content.Context
import android.os.Build
import com.vdo.frecorder.AppConstants

/**
 * SharedPreferences 封装。Kotlin 化（原 PrefsImpl.java），键名与默认值保持不变。
 */
class PrefsImpl private constructor(context: Context) : Prefs {

	private val sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

	private inline fun edit(crossinline block: (android.content.SharedPreferences.Editor) -> Unit) {
		val editor = sharedPreferences.edit()
		block(editor)
		editor.apply()
	}

	override fun isFirstRun(): Boolean =
			!sharedPreferences.contains(PREF_KEY_IS_FIRST_RUN) || sharedPreferences.getBoolean(PREF_KEY_IS_FIRST_RUN, false)

	override fun firstRunExecuted() {
		edit {
			it.putBoolean(PREF_KEY_IS_FIRST_RUN, false)
			it.putBoolean(PREF_KEY_IS_STORE_DIR_PUBLIC, false)
			it.putBoolean(PREF_KEY_IS_MIGRATED, true)
			it.putBoolean(PREF_KEY_IS_PUBLIC_STORAGE_MIGRATED, true)
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
				it.putBoolean(PREF_KEY_IS_SHOW_DIRECTORY_SETTING, false)
			}
		}
	}

	override var isStoreDirPublic: Boolean
		get() = sharedPreferences.contains(PREF_KEY_IS_STORE_DIR_PUBLIC) && sharedPreferences.getBoolean(PREF_KEY_IS_STORE_DIR_PUBLIC, false)
		set(b) = edit { it.putBoolean(PREF_KEY_IS_STORE_DIR_PUBLIC, b) }

	override fun isShowDirectorySetting(): Boolean =
			sharedPreferences.getBoolean(PREF_KEY_IS_SHOW_DIRECTORY_SETTING, true)

	override fun isAskToRenameAfterStopRecording(): Boolean =
			sharedPreferences.contains(PREF_KEY_IS_ASK_TO_RENAME_AFTER_STOP_RECORDING)
					&& sharedPreferences.getBoolean(PREF_KEY_IS_ASK_TO_RENAME_AFTER_STOP_RECORDING, true)

	override fun hasAskToRenameAfterStopRecordingSetting(): Boolean =
			sharedPreferences.contains(PREF_KEY_IS_ASK_TO_RENAME_AFTER_STOP_RECORDING)

	override fun setAskToRenameAfterStopRecording(b: Boolean) =
			edit { it.putBoolean(PREF_KEY_IS_ASK_TO_RENAME_AFTER_STOP_RECORDING, b) }

	override var isPublicStorageMigrated: Boolean
		get() = sharedPreferences.contains(PREF_KEY_IS_PUBLIC_STORAGE_MIGRATED)
				&& sharedPreferences.getBoolean(PREF_KEY_IS_PUBLIC_STORAGE_MIGRATED, false)
		set(b) = edit { it.putBoolean(PREF_KEY_IS_PUBLIC_STORAGE_MIGRATED, b) }

	override fun getLastPublicStorageMigrationAsked(): Long =
			sharedPreferences.getLong(PREF_KEY_LAST_PUBLIC_STORAGE_MIGRATION_ASKED, 0)

	override fun setLastPublicStorageMigrationAsked(time: Long) =
			edit { it.putLong(PREF_KEY_LAST_PUBLIC_STORAGE_MIGRATION_ASKED, time) }

	override var activeRecord: Long
		get() = sharedPreferences.getLong(PREF_KEY_ACTIVE_RECORD, -1)
		set(id) = edit { it.putLong(PREF_KEY_ACTIVE_RECORD, id) }

	override fun getRecordCounter(): Long = sharedPreferences.getLong(PREF_KEY_RECORD_COUNTER, 0)

	override fun incrementRecordCounter() = edit { it.putLong(PREF_KEY_RECORD_COUNTER, getRecordCounter() + 1) }

	private fun getThemeColor(): Int = sharedPreferences.getInt(PREF_KEY_THEME_COLORMAP_POSITION, 0)

	fun getRecordChannelCount(): Int =
			sharedPreferences.getInt(PREF_KEY_RECORD_CHANNEL_COUNT, AppConstants.RECORD_AUDIO_STEREO)

	override var isKeepScreenOn: Boolean
		get() = sharedPreferences.getBoolean(PREF_KEY_KEEP_SCREEN_ON, false)
		set(on) = edit { it.putBoolean(PREF_KEY_KEEP_SCREEN_ON, on) }

	fun getFormat(): Int = sharedPreferences.getInt(PREF_KEY_FORMAT, AppConstants.RECORDING_FORMAT_M4A)

	private fun getBitrate(): Int =
			sharedPreferences.getInt(PREF_KEY_BITRATE, AppConstants.RECORD_ENCODING_BITRATE_128000)

	private fun getSampleRate(): Int =
			sharedPreferences.getInt(PREF_KEY_SAMPLE_RATE, AppConstants.DEFAULT_RECORD_SAMPLE_RATE)

	override fun setRecordOrder(order: Int) = edit { it.putInt(PREF_KEY_RECORDS_ORDER, order) }

	override fun getRecordsOrder(): Int = sharedPreferences.getInt(PREF_KEY_RECORDS_ORDER, AppConstants.SORT_DATE)

	fun getNamingFormat(): Int = sharedPreferences.getInt(PREF_KEY_NAMING_FORMAT, AppConstants.NAMING_COUNTED)

	override val isMigratedSettings: Boolean
		get() = sharedPreferences.getBoolean(PREF_KEY_IS_MIGRATED, false)

	override fun migrateSettings() {
		val color = getThemeColor()
		val nameFormat = getNamingFormat()
		val sampleRate = getSampleRate()
		var bitrate = getBitrate()
		if (bitrate == AppConstants.RECORD_ENCODING_BITRATE_24000) {
			bitrate = AppConstants.RECORD_ENCODING_BITRATE_48000
		}
		val channelCount = getRecordChannelCount()

		val colorKey = when (color) {
			1 -> AppConstants.THEME_BLACK
			2 -> AppConstants.THEME_TEAL
			3 -> AppConstants.THEME_BLUE
			4 -> AppConstants.THEME_PURPLE
			5 -> AppConstants.THEME_PINK
			6 -> AppConstants.THEME_ORANGE
			7 -> AppConstants.THEME_RED
			8 -> AppConstants.THEME_BROWN
			0, 9 -> AppConstants.THEME_BLUE_GREY
			else -> AppConstants.DEFAULT_THEME_COLOR
		}

		//旧格式（m4a/3gp）不再支持，一律迁到 WAV
		val recordingFormatKey = AppConstants.FORMAT_WAV
		val namingFormatKey = when (nameFormat) {
			AppConstants.NAMING_DATE -> AppConstants.NAME_FORMAT_DATE
			AppConstants.NAMING_COUNTED -> AppConstants.NAME_FORMAT_RECORD
			else -> AppConstants.DEFAULT_NAME_FORMAT
		}

		edit {
			it.putString(PREF_KEY_SETTING_THEME_COLOR, colorKey)
			it.putString(PREF_KEY_SETTING_NAMING_FORMAT, namingFormatKey)
			it.putString(PREF_KEY_SETTING_RECORDING_FORMAT, recordingFormatKey)
			it.putInt(PREF_KEY_SETTING_SAMPLE_RATE, sampleRate)
			it.putInt(PREF_KEY_SETTING_BITRATE, bitrate)
			it.putInt(PREF_KEY_SETTING_CHANNEL_COUNT, channelCount)
			it.putBoolean(PREF_KEY_IS_MIGRATED, true)
		}
	}

	override fun isMigratedDb3(): Boolean = sharedPreferences.getBoolean(PREF_KEY_IS_MIGRATED_DB3, false)

	override fun migrateDb3Finished() = edit { it.putBoolean(PREF_KEY_IS_MIGRATED_DB3, true) }

	override fun setSettingThemeColor(colorKey: String) = edit { it.putString(PREF_KEY_SETTING_THEME_COLOR, colorKey) }

	override fun getSettingThemeColor(): String =
			sharedPreferences.getString(PREF_KEY_SETTING_THEME_COLOR, AppConstants.DEFAULT_THEME_COLOR)!!

	override fun setSettingNamingFormat(nameKey: String) =
			edit { it.putString(PREF_KEY_SETTING_NAMING_FORMAT, nameKey) }

	override fun getSettingNamingFormat(): String =
			sharedPreferences.getString(PREF_KEY_SETTING_NAMING_FORMAT, AppConstants.DEFAULT_NAME_FORMAT)!!

	override fun setSettingRecordingFormat(formatKey: String) =
			edit { it.putString(PREF_KEY_SETTING_RECORDING_FORMAT, formatKey) }

	override fun getSettingRecordingFormat(): String {
		val format = sharedPreferences.getString(PREF_KEY_SETTING_RECORDING_FORMAT, AppConstants.DEFAULT_RECORDING_FORMAT)
		//旧的不支持的格式（m4a/3gp）强制回 WAV
		return if (AppConstants.FORMAT_WAV != format) {
			setSettingRecordingFormat(AppConstants.FORMAT_WAV)
			AppConstants.FORMAT_WAV
		} else format!!
	}

	override fun setSettingSampleRate(sampleRate: Int) =
			edit { it.putInt(PREF_KEY_SETTING_SAMPLE_RATE, sampleRate) }

	override fun getSettingSampleRate(): Int =
			sharedPreferences.getInt(PREF_KEY_SETTING_SAMPLE_RATE, AppConstants.DEFAULT_RECORD_SAMPLE_RATE)

	override fun setSettingBitrate(rate: Int) = edit { it.putInt(PREF_KEY_SETTING_BITRATE, rate) }

	override fun getSettingBitrate(): Int =
			sharedPreferences.getInt(PREF_KEY_SETTING_BITRATE, AppConstants.DEFAULT_RECORD_ENCODING_BITRATE)

	override fun setSettingChannelCount(count: Int) =
			edit { it.putInt(PREF_KEY_SETTING_CHANNEL_COUNT, count) }

	override fun getSettingChannelCount(): Int =
			sharedPreferences.getInt(PREF_KEY_SETTING_CHANNEL_COUNT, AppConstants.DEFAULT_CHANNEL_COUNT)

	override fun setSettingOutputFormat(outputFormat: String) =
			edit { it.putString(PREF_KEY_SETTING_OUTPUT_FORMAT, outputFormat) }

	override fun getSettingOutputFormat(): String =
			sharedPreferences.getString(PREF_KEY_SETTING_OUTPUT_FORMAT, AppConstants.DEFAULT_OUTPUT_FORMAT)!!

	override fun setSettingBitDepth(bitDepth: Int) = edit { it.putInt(PREF_KEY_SETTING_BIT_DEPTH, bitDepth) }

	override fun getSettingBitDepth(): Int =
			sharedPreferences.getInt(PREF_KEY_SETTING_BIT_DEPTH, AppConstants.DEFAULT_BIT_DEPTH)

	override fun setSettingAudioSource(deviceId: Int) =
			edit { it.putInt(PREF_KEY_SETTING_AUDIO_SOURCE, deviceId) }

	override fun getSettingAudioSource(): Int =
			sharedPreferences.getInt(PREF_KEY_SETTING_AUDIO_SOURCE, AppConstants.DEFAULT_AUDIO_SOURCE)

	override fun setGainBoostLevel(level: Int) = edit { it.putInt(PREF_KEY_GAIN_BOOST_LEVEL, level) }

	override fun getGainBoostLevel(): Int =
			sharedPreferences.getInt(PREF_KEY_GAIN_BOOST_LEVEL, AppConstants.DEFAULT_GAIN_BOOST_LEVEL)

	override fun setNoiseReductionEnabled(enabled: Boolean) =
			edit { it.putBoolean(PREF_KEY_NOISE_REDUCTION_ENABLED, enabled) }

	override fun isNoiseReductionEnabled(): Boolean =
			sharedPreferences.getBoolean(PREF_KEY_NOISE_REDUCTION_ENABLED, AppConstants.DEFAULT_NOISE_REDUCTION_ENABLED)

	override fun setNoiseReductionDb(db: Float) = edit { it.putFloat(PREF_KEY_NOISE_REDUCTION_DB, db) }

	override fun getNoiseReductionDb(): Float =
			sharedPreferences.getFloat(PREF_KEY_NOISE_REDUCTION_DB, AppConstants.DEFAULT_NOISE_REDUCTION_DB)

	override fun setNoiseReductionSensitivity(sensitivity: Float) =
			edit { it.putFloat(PREF_KEY_NOISE_REDUCTION_SENSITIVITY, sensitivity) }

	override fun getNoiseReductionSensitivity(): Float =
			sharedPreferences.getFloat(PREF_KEY_NOISE_REDUCTION_SENSITIVITY, AppConstants.DEFAULT_NOISE_REDUCTION_SENSITIVITY)

	override fun setNoiseReductionFreqSmoothing(bands: Int) =
			edit { it.putInt(PREF_KEY_NOISE_REDUCTION_FREQ_SMOOTHING, bands) }

	override fun getNoiseReductionFreqSmoothing(): Int =
			sharedPreferences.getInt(PREF_KEY_NOISE_REDUCTION_FREQ_SMOOTHING, AppConstants.DEFAULT_NOISE_REDUCTION_FREQ_SMOOTHING)

	override fun setNoiseProfileSeconds(seconds: Float) =
			edit { it.putFloat(PREF_KEY_NOISE_PROFILE_SECONDS, seconds) }

	override fun getNoiseProfileSeconds(): Float =
			sharedPreferences.getFloat(PREF_KEY_NOISE_PROFILE_SECONDS, AppConstants.DEFAULT_NOISE_PROFILE_SECONDS)

	override fun setHpfMode(mode: Int) = edit { it.putInt(PREF_KEY_HPF_MODE, mode) }

	override fun getHpfMode(): Int = sharedPreferences.getInt(PREF_KEY_HPF_MODE, AppConstants.DEFAULT_HPF_MODE)

	override fun setLpfMode(mode: Int) = edit { it.putInt(PREF_KEY_LPF_MODE, mode) }

	override fun getLpfMode(): Int = sharedPreferences.getInt(PREF_KEY_LPF_MODE, AppConstants.DEFAULT_LPF_MODE)

	override fun setNoiseGateEnabled(enabled: Boolean) =
			edit { it.putBoolean(PREF_KEY_NOISE_GATE_ENABLED, enabled) }

	override fun isNoiseGateEnabled(): Boolean =
			sharedPreferences.getBoolean(PREF_KEY_NOISE_GATE_ENABLED, AppConstants.DEFAULT_NOISE_GATE_ENABLED)

	override fun resetSettings() {
		edit {
			it.putString(PREF_KEY_SETTING_RECORDING_FORMAT, AppConstants.DEFAULT_RECORDING_FORMAT)
			it.putInt(PREF_KEY_SETTING_SAMPLE_RATE, AppConstants.DEFAULT_RECORD_SAMPLE_RATE)
			it.putInt(PREF_KEY_SETTING_BITRATE, AppConstants.DEFAULT_RECORD_ENCODING_BITRATE)
			it.putInt(PREF_KEY_SETTING_CHANNEL_COUNT, AppConstants.DEFAULT_CHANNEL_COUNT)
			it.putInt(PREF_KEY_SETTING_AUDIO_SOURCE, AppConstants.DEFAULT_AUDIO_SOURCE)
			it.putInt(PREF_KEY_GAIN_BOOST_LEVEL, AppConstants.DEFAULT_GAIN_BOOST_LEVEL)
			it.putBoolean(PREF_KEY_NOISE_REDUCTION_ENABLED, AppConstants.DEFAULT_NOISE_REDUCTION_ENABLED)
			it.putFloat(PREF_KEY_NOISE_REDUCTION_DB, AppConstants.DEFAULT_NOISE_REDUCTION_DB)
			it.putFloat(PREF_KEY_NOISE_REDUCTION_SENSITIVITY, AppConstants.DEFAULT_NOISE_REDUCTION_SENSITIVITY)
			it.putInt(PREF_KEY_NOISE_REDUCTION_FREQ_SMOOTHING, AppConstants.DEFAULT_NOISE_REDUCTION_FREQ_SMOOTHING)
			it.putFloat(PREF_KEY_NOISE_PROFILE_SECONDS, AppConstants.DEFAULT_NOISE_PROFILE_SECONDS)
			it.putInt(PREF_KEY_HPF_MODE, AppConstants.DEFAULT_HPF_MODE)
			it.putInt(PREF_KEY_LPF_MODE, AppConstants.DEFAULT_LPF_MODE)
			it.putBoolean(PREF_KEY_NOISE_GATE_ENABLED, AppConstants.DEFAULT_NOISE_GATE_ENABLED)
			it.putString(PREF_KEY_SETTING_OUTPUT_FORMAT, AppConstants.DEFAULT_OUTPUT_FORMAT)
			it.putInt(PREF_KEY_SETTING_BIT_DEPTH, AppConstants.DEFAULT_BIT_DEPTH)
		}
	}

	companion object {
		private const val PREF_NAME = "com.vdo.frecorder.data.PrefsImpl"

		private const val PREF_KEY_IS_FIRST_RUN = "is_first_run"
		private const val PREF_KEY_IS_MIGRATED = "is_migrated"
		private const val PREF_KEY_IS_MIGRATED_DB3 = "is_migrated_db3"
		private const val PREF_KEY_IS_STORE_DIR_PUBLIC = "is_store_dir_public"
		private const val PREF_KEY_IS_SHOW_DIRECTORY_SETTING = "is_show_directory_setting"
		private const val PREF_KEY_IS_ASK_TO_RENAME_AFTER_STOP_RECORDING = "is_ask_rename_after_stop_recording"
		private const val PREF_KEY_ACTIVE_RECORD = "active_record"
		private const val PREF_KEY_RECORD_COUNTER = "record_counter"
		private const val PREF_KEY_THEME_COLORMAP_POSITION = "theme_color"
		private const val PREF_KEY_KEEP_SCREEN_ON = "keep_screen_on"
		private const val PREF_KEY_FORMAT = "pref_format"
		private const val PREF_KEY_BITRATE = "pref_bitrate"
		private const val PREF_KEY_SAMPLE_RATE = "pref_sample_rate"
		private const val PREF_KEY_RECORDS_ORDER = "pref_records_order"
		private const val PREF_KEY_NAMING_FORMAT = "pref_naming_format"
		private const val PREF_KEY_LAST_PUBLIC_STORAGE_MIGRATION_ASKED = "pref_last_public_storage_migration_asked"
		private const val PREF_KEY_IS_PUBLIC_STORAGE_MIGRATED = "pref_is_public_storage_migrated"
		private const val PREF_KEY_RECORD_CHANNEL_COUNT = "record_channel_count"
		private const val PREF_KEY_SETTING_THEME_COLOR = "setting_theme_color"
		private const val PREF_KEY_SETTING_RECORDING_FORMAT = "setting_recording_format"
		private const val PREF_KEY_SETTING_BITRATE = "setting_bitrate"
		private const val PREF_KEY_SETTING_SAMPLE_RATE = "setting_sample_rate"
		private const val PREF_KEY_SETTING_NAMING_FORMAT = "setting_naming_format"
		private const val PREF_KEY_SETTING_CHANNEL_COUNT = "setting_channel_count"
		private const val PREF_KEY_SETTING_AUDIO_SOURCE = "setting_audio_source"
		private const val PREF_KEY_GAIN_BOOST_LEVEL = "gain_boost_level"
		private const val PREF_KEY_NOISE_REDUCTION_ENABLED = "noise_reduction_enabled"
		private const val PREF_KEY_NOISE_REDUCTION_DB = "noise_reduction_db"
		private const val PREF_KEY_NOISE_REDUCTION_SENSITIVITY = "noise_reduction_sensitivity"
		private const val PREF_KEY_NOISE_REDUCTION_FREQ_SMOOTHING = "noise_reduction_freq_smoothing"
		private const val PREF_KEY_NOISE_PROFILE_SECONDS = "noise_profile_seconds"
		private const val PREF_KEY_HPF_MODE = "hpf_mode"
		private const val PREF_KEY_LPF_MODE = "lpf_mode"
		private const val PREF_KEY_NOISE_GATE_ENABLED = "noise_gate_enabled"
		private const val PREF_KEY_SETTING_OUTPUT_FORMAT = "setting_output_format"
		private const val PREF_KEY_SETTING_BIT_DEPTH = "setting_bit_depth"

		@Volatile
		private var instance: PrefsImpl? = null

		@JvmStatic
		fun getInstance(context: Context): PrefsImpl {
			return instance ?: synchronized(this) {
				instance ?: PrefsImpl(context.applicationContext).also { instance = it }
			}
		}
	}
}
