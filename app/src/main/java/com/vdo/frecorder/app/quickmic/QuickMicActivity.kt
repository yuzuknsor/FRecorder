/*
 * Copyright 2024 Dmytro Ponomarenko
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.vdo.frecorder.app.quickmic

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import android.widget.Toast
import com.vdo.frecorder.ARApplication
import com.vdo.frecorder.ColorMap
import com.vdo.frecorder.R
import com.vdo.frecorder.app.AppRecorder
import com.vdo.frecorder.app.RecordingService
import com.vdo.frecorder.app.records.RecordsActivity
import com.vdo.frecorder.audio.AudioDeviceManager
import com.vdo.frecorder.audio.RecordingDeviceOverride
import com.vdo.frecorder.data.FileRepository
import com.vdo.frecorder.exception.CantCreateFileException
import com.vdo.frecorder.util.TimeUtils

/**
 * 始终使用手机自带麦克风录音的快捷录音页，无视设置中选择的输入设备。
 *
 * 场景：通过 USB-C 转接头/耳机听歌的同时用手机麦克风录音。若不显式指定设备，
 * 框架会把录音路由到转接头上报的（可能不存在的）耳机麦克风。
 */
class QuickMicActivity : Activity(), View.OnClickListener {

	private var autoStartPending = false

	private lateinit var txtDevice: TextView
	private lateinit var txtStatus: TextView
	private lateinit var btnToggle: TextView

	private lateinit var appRecorder: AppRecorder
	private lateinit var audioDeviceManager: AudioDeviceManager
	private lateinit var fileRepository: FileRepository

	private val handler = Handler(Looper.getMainLooper())
	private val ticker: Runnable = object : Runnable {
		override fun run() {
			updateState()
			handler.postDelayed(this, 300)
		}
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		//跟随应用内统一配色（ColorMap），否则这个界面会用默认主题
		val colorMap: ColorMap = ARApplication.injector.provideColorMap(applicationContext)
		setTheme(colorMap.appThemeResource)
		setContentView(R.layout.activity_quick_mic)

		appRecorder = ARApplication.injector.provideAppRecorder(applicationContext)
		audioDeviceManager = ARApplication.injector.provideAudioDeviceManager(applicationContext)
		fileRepository = ARApplication.injector.provideFileRepository(applicationContext)

		txtDevice = findViewById(R.id.txt_device)
		txtStatus = findViewById(R.id.txt_status)
		btnToggle = findViewById(R.id.btn_toggle)

		findViewById<View>(R.id.btn_back).setOnClickListener(this)
		btnToggle.setOnClickListener(this)
		findViewById<View>(R.id.btn_view_records).setOnClickListener(this)

		autoStartPending = ACTION_QUICK_RECORD == intent.action
	}

	override fun onNewIntent(intent: Intent) {
		super.onNewIntent(intent)
		setIntent(intent)
		if (ACTION_QUICK_RECORD == intent.action) {
			autoStartPending = true
		}
	}

	override fun onResume() {
		super.onResume()
		showDeviceInfo()
		updateState()
		handler.post(ticker)

		if (autoStartPending) {
			autoStartPending = false
			if (!appRecorder.isRecording()) {
				if (hasRecordPermission()) {
					startRecording()
				} else {
					requestRecordPermission()
				}
			}
		}
	}

	override fun onPause() {
		super.onPause()
		handler.removeCallbacks(ticker)
	}

	private fun showDeviceInfo() {
		val mic: AudioDeviceInfo? = audioDeviceManager.getBuiltInMicDevice()
		if (mic != null) {
			txtDevice.text = getString(R.string.quick_mic_device,
					AudioDeviceManager.getDeviceDisplayName(this, mic))
		} else {
			txtDevice.setText(R.string.quick_mic_device_missing)
		}
	}

	private fun updateState() {
		val recording = appRecorder.isRecording()
		if (recording) {
			btnToggle.setText(R.string.quick_mic_stop)
			txtStatus.text = TimeUtils.formatTimeIntervalHourMinSec2(appRecorder.getRecordingDuration())
		} else {
			btnToggle.setText(R.string.quick_mic_start)
			txtStatus.setText(R.string.zero_time)
		}
	}

	override fun onClick(v: View) {
		val id = v.id
		if (id == R.id.btn_back) {
			finish()
		} else if (id == R.id.btn_toggle) {
			if (appRecorder.isRecording()) {
				stopRecording()
			} else {
				if (hasRecordPermission()) {
					startRecording()
				} else {
					requestRecordPermission()
				}
			}
		} else if (id == R.id.btn_view_records) {
			startActivity(RecordsActivity.getStartIntent(applicationContext))
		}
	}

	private fun hasRecordPermission(): Boolean {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			return checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
		}
		return true
	}

	private fun requestRecordPermission() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_CODE_RECORD_AUDIO)
		}
	}

	override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults)
		if (requestCode == REQ_CODE_RECORD_AUDIO) {
			if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
				startRecording()
			} else {
				Toast.makeText(this, R.string.error_permission_denied, Toast.LENGTH_LONG).show()
			}
		}
	}

	private fun startRecording() {
		val mic: AudioDeviceInfo? = audioDeviceManager.getBuiltInMicDevice()
		if (mic != null) {
			//一次性覆盖：RecordingService 使用该设备而非保存的偏好，用后即弃，不改动偏好
			RecordingDeviceOverride.force(mic.id)
		}
		try {
			val path = fileRepository.provideRecordFile().absolutePath
			val intent = Intent(applicationContext, RecordingService::class.java)
			intent.action = RecordingService.ACTION_START_RECORDING_SERVICE
			intent.putExtra(RecordingService.EXTRAS_KEY_RECORD_PATH, path)
			startService(intent)
			if (mic == null) {
				Toast.makeText(this, R.string.quick_mic_device_missing, Toast.LENGTH_LONG).show()
			}
		} catch (e: CantCreateFileException) {
			Toast.makeText(this, R.string.error_cant_create_file, Toast.LENGTH_LONG).show()
		}
	}

	private fun stopRecording() {
		val intent = Intent(applicationContext, RecordingService::class.java)
		intent.action = RecordingService.ACTION_STOP_RECORDING
		startService(intent)
	}

	override fun onDestroy() {
		super.onDestroy()
		handler.removeCallbacks(ticker)
	}

	companion object {
		private const val REQ_CODE_RECORD_AUDIO = 512
		const val ACTION_QUICK_RECORD = "com.vdo.frecorder.action.QUICK_MIC_RECORD"

		@JvmStatic
		fun getStartIntent(context: Context): Intent {
			val intent = Intent(context, QuickMicActivity::class.java)
			intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
			return intent
		}
	}
}
