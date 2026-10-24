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

package com.vdo.frecorder.app.info

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import com.vdo.frecorder.ARApplication
import com.vdo.frecorder.AppConstants
import com.vdo.frecorder.R
import com.vdo.frecorder.util.TimeUtils

/** 录音详情页。Kotlin 化（原 ActivityInformation.java）。 */
class ActivityInformation : Activity() {

	override fun onCreate(savedInstanceState: Bundle?) {
		val colorMap = ARApplication.injector.provideColorMap(applicationContext)
		setTheme(colorMap.appThemeResource)
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_info)

		val extras = intent.extras
		val txtName = findViewById<TextView>(R.id.txt_name)
		val txtFormat = findViewById<TextView>(R.id.txt_format)
		val txtDuration = findViewById<TextView>(R.id.txt_duration)
		val txtSize = findViewById<TextView>(R.id.txt_size)
		val txtLocation = findViewById<TextView>(R.id.txt_location)
		val txtCreated = findViewById<TextView>(R.id.txt_created)
		val txtSampleRate = findViewById<TextView>(R.id.txt_sample_rate)
		val txtChannelsCount = findViewById<TextView>(R.id.txt_channels_count)
		val txtBitrate = findViewById<TextView>(R.id.txt_bitrate)
		val lblBitrate = findViewById<TextView>(R.id.lbl_bitrate)

		if (extras != null) {
			if (extras.containsKey(KEY_INFO)) {
				@Suppress("DEPRECATION")
				val info = extras.getParcelable<RecordInfo>(KEY_INFO)
				if (info != null) {
					txtName.text = info.name
					txtFormat.text = info.format
					txtDuration.text = TimeUtils.formatTimeIntervalHourMinSec2(info.duration / 1000)
					txtSize.text = ARApplication.injector.provideSettingsMapper(applicationContext).formatSize(info.size)
					txtLocation.text = info.location
					txtCreated.text = TimeUtils.formatDateTimeLocale(info.created)
					txtSampleRate.text = getString(R.string.value_hz, info.sampleRate)
					when (info.channelCount) {
						1 -> txtChannelsCount.setText(R.string.mono)
						2 -> txtChannelsCount.setText(R.string.stereo)
					}
					if (info.format == AppConstants.FORMAT_WAV || info.format == AppConstants.FORMAT_FLAC) {
						txtBitrate.visibility = View.GONE
						lblBitrate.visibility = View.GONE
					} else {
						txtBitrate.visibility = View.VISIBLE
						lblBitrate.visibility = View.VISIBLE
						txtBitrate.text = getString(R.string.value_kbps, info.bitrate / 1000)
					}
				}
			}
		}

		findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
	}

	companion object {
		private const val KEY_INFO = "key_info"

		@JvmStatic
		fun getStartIntent(context: Context, info: RecordInfo): Intent {
			val intent = Intent(context, ActivityInformation::class.java)
			intent.putExtra(KEY_INFO, info)
			return intent
		}
	}
}
