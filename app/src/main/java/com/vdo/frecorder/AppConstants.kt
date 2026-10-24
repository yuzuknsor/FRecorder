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

package com.vdo.frecorder

import android.app.PendingIntent
import android.os.Build

/** 全局常量。Kotlin 化（原 AppConstants.java）。 */
class AppConstants private constructor() {

	companion object {

		val PENDING_INTENT_FLAGS: Int =
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0

		const val REQUESTS_RECEIVER = "dmitriy.ponomarenko.ua@gmail.com"

		const val APPLICATION_NAME = "FRecorder"
		const val RECORDS_DIR = "records"
		const val SEPARATOR = ", "
		const val EXTENSION_SEPARATOR = "."
		const val BASE_RECORD_NAME = "Record-"
		const val TRASH_MARK_EXTENSION = "del"

		const val THEME_BLACK = "black"
		const val THEME_TEAL = "teal"
		const val THEME_BLUE = "blue"
		const val THEME_PURPLE = "purple"
		const val THEME_PINK = "pink"
		const val THEME_ORANGE = "orange"
		const val THEME_RED = "red"
		const val THEME_BROWN = "brown"
		const val THEME_BLUE_GREY = "blue_gray"

		val SUPPORTED_EXT = arrayOf("mp3", "wav", "3gpp", "3gp", "amr", "aac", "m4a", "mp4", "ogg", "flac")

		//输出格式常量（最终保存的文件格式）
		const val OUTPUT_FORMAT_WAV = "wav"
		const val OUTPUT_FORMAT_MP3 = "mp3"
		const val OUTPUT_FORMAT_FLAC = "flac"

		//WAV 输出的位深常量
		const val BIT_DEPTH_16 = 16
		const val BIT_DEPTH_24 = 24
		const val DEFAULT_BIT_DEPTH = BIT_DEPTH_24

		const val FORMAT_M4A = "m4a"
		const val FORMAT_WAV = "wav"
		const val FORMAT_3GP = "3gp"
		const val FORMAT_3GPP = "3gpp"
		const val FORMAT_MP3 = "mp3"
		const val FORMAT_AMR = "amr"
		const val FORMAT_AAC = "aac"
		const val FORMAT_MP4 = "mp4"
		const val FORMAT_OGG = "ogg"
		const val FORMAT_FLAC = "flac"

		const val NAME_FORMAT_RECORD = "record"
		const val NAME_FORMAT_TIMESTAMP = "timestamp"
		const val NAME_FORMAT_DATE = "date"
		const val NAME_FORMAT_DATE_US = "date_us"
		const val NAME_FORMAT_DATE_ISO8601 = "date_iso8601"

		const val MAX_RECORD_NAME_LENGTH = 50

		const val NAMING_COUNTED = 0
		const val NAMING_DATE = 1

		const val RECORDING_FORMAT_M4A = 0
		const val RECORDING_FORMAT_WAV = 1

		const val DEFAULT_PER_PAGE = 50

		const val RECORD_IN_TRASH_MAX_DURATION = 5184000000L // 1000 X 60 X 60 X 24 X 60 = 60 天
		const val MIN_REMAIN_RECORDING_TIME = 10000L // 1000 X 10 = 10 秒
		const val DECODE_DURATION = 7200000L // 2 X 60 X 60 X 1000 = 2 小时

		//---------- 波形可视化常量 ----------

		/** 短录音（短于 LONG_RECORD_THRESHOLD_SECONDS）每秒对应的密度像素数 */
		const val SHORT_RECORD_DP_PER_SECOND = 25

		/** 长录音（长于 LONG_RECORD_THRESHOLD_SECONDS）波形长度，以屏幕数计 */
		const val WAVEFORM_WIDTH = 1.5f //一个半屏幕宽的波形

		/** 区分长短录音的秒数阈值，两者可视化算法略有不同 */
		const val LONG_RECORD_THRESHOLD_SECONDS = 20

		/** 波形可见部分的网格线数量（即屏幕上可见的线数），用于长录音可视化 */
		const val GRID_LINES_COUNT = 10

		//----------------------------------

		const val TIME_FORMAT_24H = 11
		const val TIME_FORMAT_12H = 12

		//录音与播放
		const val PLAYBACK_SAMPLE_RATE = 44100
		const val RECORD_SAMPLE_RATE_44100 = 44100
		const val RECORD_SAMPLE_RATE_8000 = 8000
		const val RECORD_SAMPLE_RATE_16000 = 16000
		const val RECORD_SAMPLE_RATE_22050 = 22050
		const val RECORD_SAMPLE_RATE_32000 = 32000
		const val RECORD_SAMPLE_RATE_48000 = 48000

		const val RECORD_ENCODING_BITRATE_12000 = 12000 //3gp 格式码率
		const val RECORD_ENCODING_BITRATE_24000 = 24000
		const val RECORD_ENCODING_BITRATE_48000 = 48000
		const val RECORD_ENCODING_BITRATE_96000 = 96000
		const val RECORD_ENCODING_BITRATE_128000 = 128000
		const val RECORD_ENCODING_BITRATE_192000 = 192000
		const val RECORD_ENCODING_BITRATE_256000 = 256000
		const val RECORD_ENCODING_BITRATE_288000 = 288000
		const val RECORD_ENCODING_BITRATE_320000 = 320000

		const val SORT_DATE = 1
		const val SORT_NAME = 2
		const val SORT_DURATION = 3
		const val SORT_DATE_DESC = 4
		const val SORT_NAME_DESC = 5
		const val SORT_DURATION_DESC = 6

		const val RECORD_AUDIO_MONO = 1
		const val RECORD_AUDIO_STEREO = 2

		//音频输入源常量
		const val AUDIO_SOURCE_DEFAULT_MIC = -1
		const val DEFAULT_AUDIO_SOURCE = AUDIO_SOURCE_DEFAULT_MIC

		//增益档位常量
		const val GAIN_BOOST_OFF = 0
		const val GAIN_BOOST_6DB = 1
		const val GAIN_BOOST_12DB = 2
		const val GAIN_BOOST_MULTIPLIER_6DB = 2.0f   // 10^(6/20) ≈ 1.995
		const val GAIN_BOOST_MULTIPLIER_12DB = 4.0f  // 10^(12/20) ≈ 3.981
		const val DEFAULT_GAIN_BOOST_LEVEL = GAIN_BOOST_OFF

		//高通滤波常量
		const val HPF_OFF = 0
		const val HPF_80 = 1
		const val HPF_120 = 2
		const val HPF_FREQ_80 = 80.0f
		const val HPF_FREQ_120 = 120.0f
		const val DEFAULT_HPF_MODE = HPF_OFF

		//低通滤波常量
		const val LPF_OFF = 0
		const val LPF_9500 = 1
		const val LPF_15000 = 2
		const val LPF_FREQ_9500 = 9500.0f
		const val LPF_FREQ_15000 = 15000.0f
		const val DEFAULT_LPF_MODE = LPF_OFF

		//噪声门常量
		const val DEFAULT_NOISE_GATE_ENABLED = false
		const val NOISE_GATE_THRESHOLD_RMS = 400
		const val NOISE_GATE_ATTACK_MS = 0.1f
		const val NOISE_GATE_HOLD_MS = 300.0f
		const val NOISE_GATE_RELEASE_MS = 500.0f
		const val NOISE_GATE_HYSTERESIS = 0.5f

		//降噪常量（默认关闭）
		const val DEFAULT_NOISE_REDUCTION_ENABLED = false
		const val DEFAULT_NOISE_REDUCTION_DB = 12.0f
		const val DEFAULT_NOISE_REDUCTION_SENSITIVITY = 12.0f
		const val DEFAULT_NOISE_REDUCTION_FREQ_SMOOTHING = 3
		const val DEFAULT_NOISE_PROFILE_SECONDS = 1.0f
		const val RECORD_MAX_DURATION = 14400000 // 240 分钟 4 小时

		const val DEFAULT_THEME_COLOR = THEME_BLACK
		const val DEFAULT_RECORDING_FORMAT = FORMAT_WAV
		const val DEFAULT_OUTPUT_FORMAT = OUTPUT_FORMAT_WAV
		const val DEFAULT_NAME_FORMAT = NAME_FORMAT_RECORD
		const val DEFAULT_RECORD_SAMPLE_RATE = RECORD_SAMPLE_RATE_48000
		const val DEFAULT_RECORD_ENCODING_BITRATE = RECORD_ENCODING_BITRATE_128000
		const val DEFAULT_CHANNEL_COUNT = RECORD_AUDIO_STEREO

		/** 录音进度可视化的时间间隔 */
		const val RECORDING_VISUALIZATION_INTERVAL = 13 //mills
		const val PLAYBACK_VISUALIZATION_INTERVAL = (2.1 * RECORDING_VISUALIZATION_INTERVAL).toInt() //mills

		const val RECORD_BYTES_PER_SECOND = RECORD_ENCODING_BITRATE_48000 / 8 //比特每秒换算为字节每秒
		const val MIGRATE_PUBLIC_STORAGE_WARNING_COOLDOWN_MILLS = 12 * 60 * 60 * 1000 //12 小时
	}
}
