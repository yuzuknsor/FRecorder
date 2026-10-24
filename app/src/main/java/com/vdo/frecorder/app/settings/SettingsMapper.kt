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
import android.content.res.Resources
import com.vdo.frecorder.AppConstants
import com.vdo.frecorder.R
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** 设置项与资源/键值的映射工具。Kotlin 化（原 SettingsMapper.java）。 */
class SettingsMapper(context: Context) {

	private lateinit var resources: Resources
	private lateinit var formats: Array<String>
	private lateinit var formatsKeys: Array<String>
	private lateinit var sampleRates: Array<String>
	private lateinit var sampleRatesKeys: Array<String>
	private lateinit var rates: Array<String>
	private lateinit var rateKeys: Array<String>
	private lateinit var recChannels: Array<String>
	private lateinit var recChannelsKeys: Array<String>

	init {
		loadResources(context)

		val formatSymbols = DecimalFormatSymbols(Locale.getDefault())
		formatSymbols.decimalSeparator = '.'
		format = DecimalFormat("#.##", formatSymbols)
	}

	fun loadResources(context: Context) {
		resources = context.resources
		formats = resources.getStringArray(R.array.formats2)
		formatsKeys = arrayOf(
				AppConstants.FORMAT_WAV
		)
		sampleRates = resources.getStringArray(R.array.sample_rates2)
		sampleRatesKeys = arrayOf(
				SAMPLE_RATE_8000,
				SAMPLE_RATE_16000,
				SAMPLE_RATE_22050,
				SAMPLE_RATE_32000,
				SAMPLE_RATE_44100,
				SAMPLE_RATE_48000
		)
		rates = resources.getStringArray(R.array.bit_rates2)
		rateKeys = arrayOf(
				BITRATE_48000,
				BITRATE_96000,
				BITRATE_128000,
				BITRATE_192000,
				BITRATE_256000,
				BITRATE_288000
		)
		recChannels = resources.getStringArray(R.array.channels)
		recChannelsKeys = arrayOf(
				CHANNEL_COUNT_STEREO,
				CHANNEL_COUNT_MONO
		)
	}

	fun formatBitrate(bitrate: Int): String {
		return resources.getString(R.string.value_kbps, bitrate)
	}

	fun convertSampleRateToString(sampleRate: Int): String {
		val key = sampleRateToKey(sampleRate)
		for (i in sampleRatesKeys.indices) {
			if (key == sampleRatesKeys[i]) {
				return sampleRates[i]
			}
		}
		return ""
	}

	fun convertBitratesToString(bitrate: Int): String {
		val key = bitrateToKey(bitrate)
		for (i in rateKeys.indices) {
			if (key == rateKeys[i]) {
				return rates[i]
			}
		}
		return ""
	}

	fun convertChannelsToString(count: Int): String {
		val key = channelCountToKey(count)
		for (i in recChannelsKeys.indices) {
			if (key == recChannelsKeys[i]) {
				return recChannels[i]
			}
		}
		return ""
	}

	fun convertFormatsToString(formatKey: String): String {
		for (i in formatsKeys.indices) {
			if (formatKey == formatsKeys[i]) {
				return formats[i]
			}
		}
		//处理设置数组之外的格式（如输出格式 MP3、FLAC）
		if (formatKey.isNotEmpty()) {
			return formatKey.uppercase(Locale.ROOT)
		}
		return ""
	}

	fun formatSize(size: Long): String {
		return resources.getString(R.string.size, format.format(size.toFloat() / (1024 * 1024)))
	}

	companion object {
		private var singleton: SettingsMapper? = null

		private lateinit var format: DecimalFormat

		const val SAMPLE_RATE_8000 = "8000"
		const val SAMPLE_RATE_16000 = "16000"
		const val SAMPLE_RATE_22050 = "22050"
		const val SAMPLE_RATE_32000 = "32000"
		const val SAMPLE_RATE_44100 = "44100"
		const val SAMPLE_RATE_48000 = "48000"

		const val BITRATE_12000 = "12000"
		const val BITRATE_48000 = "48000"
		const val BITRATE_96000 = "96000"
		const val BITRATE_128000 = "128000"
		const val BITRATE_192000 = "192000"
		const val BITRATE_256000 = "256000"
		const val BITRATE_288000 = "288000"

		const val CHANNEL_COUNT_STEREO = "stereo"
		const val CHANNEL_COUNT_MONO = "mono"

		const val BIT_DEPTH_16_KEY = "16"
		const val BIT_DEPTH_24_KEY = "24"

		const val GAIN_BOOST_OFF_KEY = "off"
		const val GAIN_BOOST_6DB_KEY = "6db"
		const val GAIN_BOOST_12DB_KEY = "12db"

		@JvmStatic
		fun getInstance(context: Context): SettingsMapper {
			if (singleton == null) {
				singleton = SettingsMapper(context)
			}
			return singleton!!
		}

		@JvmStatic
		fun positionToColorKey(position: Int): String {
			return when (position) {
				0 -> AppConstants.THEME_BLUE_GREY
				1 -> AppConstants.THEME_BLACK
				2 -> AppConstants.THEME_TEAL
				3 -> AppConstants.THEME_BLUE
				4 -> AppConstants.THEME_PURPLE
				5 -> AppConstants.THEME_PINK
				6 -> AppConstants.THEME_ORANGE
				7 -> AppConstants.THEME_RED
				8 -> AppConstants.THEME_BROWN
				else -> AppConstants.DEFAULT_THEME_COLOR
			}
		}

		@JvmStatic
		fun colorKeyToPosition(colorKey: String?): Int {
			return when (colorKey) {
				AppConstants.THEME_BLACK -> 1
				AppConstants.THEME_TEAL -> 2
				AppConstants.THEME_BLUE -> 3
				AppConstants.THEME_PURPLE -> 4
				AppConstants.THEME_PINK -> 5
				AppConstants.THEME_ORANGE -> 6
				AppConstants.THEME_RED -> 7
				AppConstants.THEME_BROWN -> 8
				else -> 0
			}
		}

		@JvmStatic
		fun namingFormatToPosition(namingFormat: String?): Int {
			return when (namingFormat) {
				AppConstants.NAME_FORMAT_TIMESTAMP -> 4
				AppConstants.NAME_FORMAT_DATE_ISO8601 -> 3
				AppConstants.NAME_FORMAT_DATE_US -> 2
				AppConstants.NAME_FORMAT_DATE -> 1
				else -> 0
			}
		}

		@JvmStatic
		fun positionToNamingFormat(position: Int): String {
			return when (position) {
				0 -> AppConstants.NAME_FORMAT_RECORD
				1 -> AppConstants.NAME_FORMAT_DATE
				2 -> AppConstants.NAME_FORMAT_DATE_US
				3 -> AppConstants.NAME_FORMAT_DATE_ISO8601
				4 -> AppConstants.NAME_FORMAT_TIMESTAMP
				else -> AppConstants.DEFAULT_NAME_FORMAT
			}
		}

		@JvmStatic
		fun keyToSampleRate(sampleRateKey: String?): Int {
			return when (sampleRateKey) {
				SAMPLE_RATE_8000 -> AppConstants.RECORD_SAMPLE_RATE_8000
				SAMPLE_RATE_16000 -> AppConstants.RECORD_SAMPLE_RATE_16000
				SAMPLE_RATE_22050 -> AppConstants.RECORD_SAMPLE_RATE_22050
				SAMPLE_RATE_32000 -> AppConstants.RECORD_SAMPLE_RATE_32000
				SAMPLE_RATE_44100 -> AppConstants.RECORD_SAMPLE_RATE_44100
				SAMPLE_RATE_48000 -> AppConstants.RECORD_SAMPLE_RATE_48000
				else -> AppConstants.DEFAULT_RECORD_SAMPLE_RATE
			}
		}

		@JvmStatic
		fun sampleRateToKey(sampleRate: Int): String {
			return when (sampleRate) {
				AppConstants.RECORD_SAMPLE_RATE_8000 -> SAMPLE_RATE_8000
				AppConstants.RECORD_SAMPLE_RATE_16000 -> SAMPLE_RATE_16000
				AppConstants.RECORD_SAMPLE_RATE_22050 -> SAMPLE_RATE_22050
				AppConstants.RECORD_SAMPLE_RATE_32000 -> SAMPLE_RATE_32000
				AppConstants.RECORD_SAMPLE_RATE_48000 -> SAMPLE_RATE_48000
				else -> SAMPLE_RATE_44100
			}
		}

		@JvmStatic
		fun keyToBitrate(bitrateKey: String?): Int {
			return when (bitrateKey) {
				BITRATE_48000 -> AppConstants.RECORD_ENCODING_BITRATE_48000
				BITRATE_96000 -> AppConstants.RECORD_ENCODING_BITRATE_96000
				BITRATE_128000 -> AppConstants.RECORD_ENCODING_BITRATE_128000
				BITRATE_192000 -> AppConstants.RECORD_ENCODING_BITRATE_192000
				BITRATE_256000 -> AppConstants.RECORD_ENCODING_BITRATE_256000
				BITRATE_288000 -> AppConstants.RECORD_ENCODING_BITRATE_288000
				else -> AppConstants.DEFAULT_RECORD_ENCODING_BITRATE
			}
		}

		@JvmStatic
		fun bitrateToKey(bitrate: Int): String {
			return when (bitrate) {
				AppConstants.RECORD_ENCODING_BITRATE_12000 -> BITRATE_12000
				AppConstants.RECORD_ENCODING_BITRATE_48000 -> BITRATE_48000
				AppConstants.RECORD_ENCODING_BITRATE_96000 -> BITRATE_96000
				AppConstants.RECORD_ENCODING_BITRATE_192000 -> BITRATE_192000
				AppConstants.RECORD_ENCODING_BITRATE_256000 -> BITRATE_256000
				AppConstants.RECORD_ENCODING_BITRATE_288000 -> BITRATE_288000
				else -> BITRATE_128000
			}
		}

		@JvmStatic
		fun keyToChannelCount(key: String?): Int {
			return when (key) {
				CHANNEL_COUNT_MONO -> AppConstants.RECORD_AUDIO_MONO
				CHANNEL_COUNT_STEREO -> AppConstants.RECORD_AUDIO_STEREO
				else -> AppConstants.DEFAULT_CHANNEL_COUNT
			}
		}

		@JvmStatic
		fun channelCountToKey(count: Int): String {
			return when (count) {
				AppConstants.RECORD_AUDIO_MONO -> CHANNEL_COUNT_MONO
				else -> CHANNEL_COUNT_STEREO
			}
		}

		@JvmStatic
		fun positionToOutputFormat(position: Int): Int {
			return when (position) {
				1 -> 1 // MP3
				2 -> 2 // FLAC
				else -> 0 // WAV
			}
		}

		@JvmStatic
		fun outputFormatPositionToKey(position: Int): String {
			return when (position) {
				1 -> AppConstants.OUTPUT_FORMAT_MP3
				2 -> AppConstants.OUTPUT_FORMAT_FLAC
				else -> AppConstants.OUTPUT_FORMAT_WAV
			}
		}

		@JvmStatic
		fun outputFormatKeyToPosition(key: String?): Int {
			return when (key) {
				AppConstants.OUTPUT_FORMAT_MP3 -> 1
				AppConstants.OUTPUT_FORMAT_FLAC -> 2
				else -> 0
			}
		}

		@JvmStatic
		fun bitDepthToPosition(bitDepth: Int): Int {
			return when (bitDepth) {
				AppConstants.BIT_DEPTH_24 -> 1
				else -> 0
			}
		}

		@JvmStatic
		fun positionToBitDepth(position: Int): Int {
			return when (position) {
				1 -> AppConstants.BIT_DEPTH_24
				else -> AppConstants.BIT_DEPTH_16
			}
		}

		@JvmStatic
		fun bitDepthToKey(bitDepth: Int): String {
			return when (bitDepth) {
				AppConstants.BIT_DEPTH_24 -> BIT_DEPTH_24_KEY
				else -> BIT_DEPTH_16_KEY
			}
		}

		@JvmStatic
		fun keyToBitDepth(key: String?): Int {
			return when (key) {
				BIT_DEPTH_24_KEY -> AppConstants.BIT_DEPTH_24
				else -> AppConstants.BIT_DEPTH_16
			}
		}

		@JvmStatic
		fun keyToGainBoostLevel(key: String?): Int {
			return when (key) {
				GAIN_BOOST_6DB_KEY -> AppConstants.GAIN_BOOST_6DB
				GAIN_BOOST_12DB_KEY -> AppConstants.GAIN_BOOST_12DB
				else -> AppConstants.GAIN_BOOST_OFF
			}
		}

		@JvmStatic
		fun gainBoostLevelToKey(level: Int): String {
			return when (level) {
				AppConstants.GAIN_BOOST_6DB -> GAIN_BOOST_6DB_KEY
				AppConstants.GAIN_BOOST_12DB -> GAIN_BOOST_12DB_KEY
				else -> GAIN_BOOST_OFF_KEY
			}
		}
	}
}
