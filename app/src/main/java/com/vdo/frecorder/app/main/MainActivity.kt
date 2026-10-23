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

import android.Manifest
import android.animation.Animator
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.MenuInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.NonNull
import com.vdo.frecorder.ARApplication
import com.vdo.frecorder.AppConstants
import com.vdo.frecorder.IntArrayList
import com.vdo.frecorder.R
import com.vdo.frecorder.audio.AudioDecoder
import com.vdo.frecorder.audio.AudioDeviceManager
import com.vdo.frecorder.audio.monitor.AudioMonitor
import com.vdo.frecorder.audio.player.AudioPlayerNew
import com.vdo.frecorder.audio.player.PlayerContractNew
import com.vdo.frecorder.audio.recorder.RecorderContract
import com.vdo.frecorder.audio.recorder.WavRecorder
import com.vdo.frecorder.app.DecodeService
import com.vdo.frecorder.app.DecodeServiceListener
import com.vdo.frecorder.app.DownloadService
import com.vdo.frecorder.app.PlaybackService
import com.vdo.frecorder.app.RecordingService
import com.vdo.frecorder.app.info.ActivityInformation
import com.vdo.frecorder.app.info.RecordInfo
import com.vdo.frecorder.app.moverecords.MoveRecordsActivity
import com.vdo.frecorder.app.quickmic.QuickMicActivity
import com.vdo.frecorder.app.records.RecordsActivity
import com.vdo.frecorder.app.settings.SettingsActivity
import com.vdo.frecorder.app.shortcuts.AppShortcuts
import com.vdo.frecorder.app.welcome.WelcomeActivity
import com.vdo.frecorder.app.widget.RecordingWaveformView
import com.vdo.frecorder.app.widget.WaveformViewNew
import com.vdo.frecorder.data.FileRepository
import com.vdo.frecorder.data.Prefs
import com.vdo.frecorder.data.database.Record
import com.vdo.frecorder.exception.CantCreateFileException
import com.vdo.frecorder.exception.ErrorParser
import com.vdo.frecorder.util.AndroidUtils
import com.vdo.frecorder.util.AnimationUtil
import com.vdo.frecorder.util.FileUtil
import com.vdo.frecorder.util.TimeUtils
import timber.log.Timber
import java.io.File

/** 主界面。Kotlin 化（原 MainActivity.java）。 */
class MainActivity : Activity(), MainContract.View, View.OnClickListener {

	// TODO: 长录音无波形问题
	// TODO: Bluetooth 麦克风支持

	private lateinit var waveformView: WaveformViewNew
	private lateinit var recordingWaveformView: RecordingWaveformView
	private lateinit var txtProgress: TextView
	private lateinit var txtDuration: TextView
	private lateinit var txtZeroTime: TextView
	private lateinit var txtName: TextView
	private lateinit var txtRecordInfo: TextView
	private lateinit var btnPlay: ImageButton
	private lateinit var btnStop: ImageButton
	private lateinit var btnRecord: Button
	private lateinit var btnRecordingStop: Button
	private lateinit var btnShare: ImageButton
	private lateinit var btnImport: ImageButton
	private lateinit var btnMonitor: ImageButton
	private lateinit var btnHpf: ImageButton
	private lateinit var btnLpf: ImageButton
	private lateinit var btnNoiseGate: ImageButton
	private lateinit var btnMicBoost: ImageButton
	private lateinit var btnNoiseReduction: ImageButton
	private var isMonitoringActive = false
	private lateinit var progressBar: ProgressBar
	private lateinit var playProgress: SeekBar
	private lateinit var pnlImportProgress: LinearLayout
	private lateinit var pnlRecordProcessing: LinearLayout
	private lateinit var ivPlaceholder: ImageView
	private var noiseReductionDialog: AlertDialog? = null

	private lateinit var presenter: MainContract.UserActionsListener
	private lateinit var colorMap: com.vdo.frecorder.ColorMap
	private lateinit var fileRepository: FileRepository
	private lateinit var onThemeColorChangeListener: com.vdo.frecorder.ColorMap.OnThemeColorChangeListener

	private val connection: ServiceConnection = object : ServiceConnection {
		override fun onServiceConnected(className: ComponentName, service: IBinder) {
			val binder = service as DecodeService.LocalBinder
			val decodeService = binder.getService()
			decodeService.setDecodeListener(object : DecodeServiceListener {
				override fun onStartProcessing() {
					runOnUiThread { showRecordProcessing() }
				}

				override fun onFinishProcessing() {
					runOnUiThread {
						hideRecordProcessing()
						presenter.loadActiveRecord()
					}
				}
			})
		}

		override fun onServiceDisconnected(arg0: ComponentName) {
			hideRecordProcessing()
		}

		override fun onBindingDied(name: ComponentName) {
			hideRecordProcessing()
		}
	}

	private var space = 75f

	override fun onCreate(savedInstanceState: Bundle?) {
		colorMap = ARApplication.injector.provideColorMap(applicationContext)
		setTheme(colorMap.appThemeResource)
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_main)

		waveformView = findViewById(R.id.record)
		recordingWaveformView = findViewById(R.id.recording_view)
		txtProgress = findViewById(R.id.txt_progress)
		txtDuration = findViewById(R.id.txt_duration)
		txtZeroTime = findViewById(R.id.txt_zero_time)
		txtName = findViewById(R.id.txt_name)
		txtRecordInfo = findViewById(R.id.txt_record_info)
		btnPlay = findViewById(R.id.btn_play)
		btnRecord = findViewById(R.id.btn_record)
		btnRecordingStop = findViewById(R.id.btn_record_stop)
		btnStop = findViewById(R.id.btn_stop)
		val btnRecordsList: ImageButton = findViewById(R.id.btn_records_list)
		val btnSettings: ImageButton = findViewById(R.id.btn_settings)
		btnShare = findViewById(R.id.btn_share)
		btnImport = findViewById(R.id.btn_import)
		btnMonitor = findViewById(R.id.btn_monitor)
		btnHpf = findViewById(R.id.btn_hpf)
		btnLpf = findViewById(R.id.btn_lpf)
		btnNoiseGate = findViewById(R.id.btn_noise_gate)
		btnMicBoost = findViewById(R.id.btn_mic_boost)
		btnNoiseReduction = findViewById(R.id.btn_noise_reduction)
		progressBar = findViewById(R.id.progress)
		playProgress = findViewById(R.id.play_progress)
		pnlImportProgress = findViewById(R.id.pnl_import_progress)
		pnlRecordProcessing = findViewById(R.id.pnl_record_processing)
		ivPlaceholder = findViewById(R.id.placeholder)
		ivPlaceholder.setImageResource(R.drawable.waveform)

		txtProgress.text = TimeUtils.formatTimeIntervalHourMinSec2(0)

		btnRecordingStop.visibility = View.GONE
		btnRecordingStop.isEnabled = false

		btnPlay.setOnClickListener(this)
		btnRecord.setOnClickListener(this)
		btnRecordingStop.setOnClickListener(this)
		btnStop.setOnClickListener(this)
		btnRecordsList.setOnClickListener(this)
		btnSettings.setOnClickListener(this)
		btnShare.setOnClickListener(this)
		btnImport.setOnClickListener(this)
		txtName.setOnClickListener(this)
		btnMonitor.setOnClickListener(this)
		btnHpf.setOnClickListener(this)
		btnLpf.setOnClickListener(this)
		btnNoiseGate.setOnClickListener(this)
		btnMicBoost.setOnClickListener(this)
		btnNoiseReduction.setOnClickListener(this)
		isMonitoringActive = AudioMonitor.getInstance().isMonitoring
		btnMonitor.alpha = if (isMonitoringActive) 1.0f else 0.5f
		updateFilterButtonAlpha()
		updateNoiseGateButtonAlpha()
		updateMicBoostButtonAlpha()
		updateNoiseReductionButtonAlpha()
		space = resources.getDimension(R.dimen.spacing_xnormal)

		playProgress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
			override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
				if (fromUser) {
					val valPx = AndroidUtils.dpToPx(progress * waveformView.getWaveformLength() / 1000).toInt()
					waveformView.seekPx(valPx)
					//TODO: 找到更好的 px 转 mills 方式
					presenter.seekPlayback(waveformView.pxToMill(valPx))
				}
			}

			override fun onStartTrackingTouch(seekBar: SeekBar) {
				presenter.disablePlaybackProgressListener()
			}

			override fun onStopTrackingTouch(seekBar: SeekBar) {
				presenter.enablePlaybackProgressListener()
			}
		})

		presenter = ARApplication.injector.provideMainPresenter(applicationContext)
		fileRepository = ARApplication.injector.provideFileRepository(applicationContext)

		waveformView.setOnSeekListener(object : WaveformViewNew.OnSeekListener {
			override fun onStartSeek() {
				presenter.disablePlaybackProgressListener()
			}

			override fun onSeek(px: Int, mills: Long) {
				presenter.enablePlaybackProgressListener()
				//TODO: 找到更好的 px 转 mills 方式
				presenter.seekPlayback(waveformView.pxToMill(px))

				val length = waveformView.getWaveformLength()
				if (length > 0) {
					playProgress.progress = 1000 * AndroidUtils.pxToDp(px).toInt() / length
				}
				txtProgress.text = TimeUtils.formatTimeIntervalHourMinSec2(mills)
			}

			override fun onSeeking(px: Int, mills: Long) {
				val length = waveformView.getWaveformLength()
				if (length > 0) {
					playProgress.progress = 1000 * AndroidUtils.pxToDp(px).toInt() / length
				}
				txtProgress.text = TimeUtils.formatTimeIntervalHourMinSec2(mills)
			}
		})
		onThemeColorChangeListener = object : com.vdo.frecorder.ColorMap.OnThemeColorChangeListener {
			override fun onThemeColorChange(colorKey: String) {
				setTheme(colorMap.appThemeResource)
				recreate()
			}
		}
		colorMap.addOnThemeColorChangeListener(onThemeColorChangeListener)

		// 快捷方式（开始录制 / 内置麦克风）
		AppShortcuts.refresh(this)
		handleShortcutIntent(intent)
		checkNotificationPermission()
		checkRecordPermission2()
	}

	private fun handleShortcutIntent(intent: Intent?) {
		if (intent == null) {
			return
		}
		val action = intent.action
		if (AppShortcuts.ACTION_RUN == action) {
			if (checkRecordPermission2() && checkStoragePermission2()) {
				startRecordingService()
			}
		} else if (QuickMicActivity.ACTION_QUICK_RECORD == action) {
			val quickMic = Intent(this, QuickMicActivity::class.java)
			quickMic.action = QuickMicActivity.ACTION_QUICK_RECORD
			quickMic.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
			startActivity(quickMic)
		}
		// 消费掉，避免屏幕旋转重建时重复触发
		intent.action = null
	}

	override fun onNewIntent(intent: Intent) {
		super.onNewIntent(intent)
		setIntent(intent)
		// 快捷方式在应用已打开时不会走 onCreate，必须在这里处理
		handleShortcutIntent(intent)
	}

	override fun onStart() {
		super.onStart()
		presenter.bindView(this)
		val player = ARApplication.injector.provideAudioPlayer()
		if (player is AudioPlayerNew) {
			player.setContext(applicationContext)
		}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			//适配分区存储
			presenter.storeInPrivateDir(applicationContext)
		}
		presenter.checkFirstRun()
		presenter.setAudioRecorder(ARApplication.injector.provideAudioRecorder(applicationContext))
		presenter.updateRecordingDir(applicationContext)
		presenter.loadActiveRecord()

		// 监听按钮与 AudioMonitor 实际状态同步
		isMonitoringActive = AudioMonitor.getInstance().isMonitoring
		btnMonitor.alpha = if (isMonitoringActive) 1.0f else 0.5f

		// 设置降噪回调
		val rec = ARApplication.injector.provideAudioRecorder(applicationContext)
		if (rec is WavRecorder) {
			rec.setNoiseReductionListener(object : WavRecorder.NoiseReductionListener {
				override fun onNoiseReductionStart() {
					showNoiseReductionDialog()
				}

				override fun onNoiseReductionProgress(percent: Int) {
					updateNoiseReductionDialog(percent)
				}

				override fun onNoiseReductionEnd(success: Boolean) {
					dismissNoiseReductionDialog(success)
				}
			})
		}

		val intent = Intent(this, DecodeService::class.java)
		bindService(intent, connection, Context.BIND_AUTO_CREATE)
	}

	override fun onStop() {
		super.onStop()
		unbindService(connection)
		presenter.unbindView()
	}

	override fun onDestroy() {
		super.onDestroy()
		colorMap.removeOnThemeColorChangeListener(onThemeColorChangeListener)
	}

	override fun onClick(view: View) {
		val id = view.id
		if (id == R.id.btn_play) {
			presenter.onPlaybackClick(applicationContext, checkStoragePermissionPlayback())
		} else if (id == R.id.btn_record) {
			if (checkRecordPermission2()) {
				if (checkStoragePermission2()) {
					//开始或停止录音
					startRecordingService()
					presenter.pauseUnpauseRecording(applicationContext)
				}
			}
		} else if (id == R.id.btn_record_stop) {
			presenter.stopRecording()
		} else if (id == R.id.btn_stop) {
			presenter.stopPlayback()
		} else if (id == R.id.btn_records_list) {
			startActivity(RecordsActivity.getStartIntent(applicationContext))
		} else if (id == R.id.btn_settings) {
			startActivity(SettingsActivity.getStartIntent(applicationContext))
		} else if (id == R.id.btn_share) {
			showMenu(view)
		} else if (id == R.id.btn_import) {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
				startFileSelector()
			} else {
				if (checkStoragePermissionImport()) {
					startFileSelector()
				}
			}
		} else if (id == R.id.txt_name) {
			presenter.onRenameRecordClick()
		} else if (id == R.id.btn_monitor) {
			toggleMonitoring()
		} else if (id == R.id.btn_hpf) {
			toggleHpf()
		} else if (id == R.id.btn_lpf) {
			toggleLpf()
		} else if (id == R.id.btn_noise_gate) {
			toggleNoiseGate()
		} else if (id == R.id.btn_mic_boost) {
			toggleMicBoost()
		} else if (id == R.id.btn_noise_reduction) {
			toggleNoiseReduction()
		}
	}

	private fun startFileSelector() {
		val intentUpload = Intent()
		intentUpload.type = "audio/*"
		intentUpload.addCategory(Intent.CATEGORY_OPENABLE)
		intentUpload.action = Intent.ACTION_OPEN_DOCUMENT
		try {
			startActivityForResult(intentUpload, REQ_CODE_IMPORT_AUDIO)
		} catch (e: ActivityNotFoundException) {
			Timber.e(e)
			showError(R.string.cant_import_files)
		}
	}

	private fun toggleMonitoring() {
		val recorder = ARApplication.injector.provideAudioRecorder(applicationContext)
		if (recorder !is WavRecorder) {
			Toast.makeText(this, R.string.monitoring_wav_only, Toast.LENGTH_SHORT).show()
			return
		}
		val wavRecorder = recorder
		val monitor = AudioMonitor.getInstance()
		monitor.setContext(applicationContext)

		if (!isMonitoringActive) {
			// 即将开启监听——先检查回授风险
			val prefs: Prefs = ARApplication.injector.providePrefs(applicationContext)
			val deviceId = prefs.getSettingAudioSource()
			var audioDevice: AudioDeviceInfo? = null
			if (deviceId != AppConstants.AUDIO_SOURCE_DEFAULT_MIC) {
				val adm: AudioDeviceManager = ARApplication.injector.provideAudioDeviceManager(applicationContext)
				audioDevice = adm.getDeviceById(deviceId)
			}

			if (monitor.hasFeedbackRisk(audioDevice)) {
				AlertDialog.Builder(this)
						.setTitle(R.string.monitoring_feedback_warning_title)
						.setMessage(R.string.monitoring_feedback_warning)
						.setPositiveButton(R.string.btn_ok, null)
						.show()
				return
			}

			enableMonitoring(wavRecorder, monitor, prefs, audioDevice)
		} else {
			// 停止监听
			if (monitor.isStandalone) {
				monitor.stopStandalone()
			}
			wavRecorder.setMonitoringEnabled(false)
			isMonitoringActive = false
		}

		btnMonitor.alpha = if (isMonitoringActive) 1.0f else 0.5f
		Toast.makeText(this,
				if (isMonitoringActive) R.string.monitoring_on else R.string.monitoring_off,
				Toast.LENGTH_SHORT).show()
	}

	private fun enableMonitoring(wavRecorder: WavRecorder, monitor: AudioMonitor, prefs: Prefs, audioDevice: AudioDeviceInfo?) {
		isMonitoringActive = true
		monitor.setNoiseGateEnabled(prefs.isNoiseGateEnabled())
		monitor.setHpfMode(prefs.getHpfMode())
		monitor.setLpfMode(prefs.getLpfMode())
		monitor.setGainBoostLevel(prefs.getGainBoostLevel())
		if (wavRecorder.isRecording) {
			// 录音中——由录音循环喂给监听
			wavRecorder.setMonitoringEnabled(true)
		} else {
			// 未录音——启动独立监听
			wavRecorder.setMonitoringEnabled(true) // 录音开始时生效的标志
			val sr = prefs.getSettingSampleRate()
			val ch = prefs.getSettingChannelCount()
			monitor.startStandalone(sr, ch, audioDevice)
		}
	}

	private fun toggleHpf() {
		val recorder = ARApplication.injector.provideAudioRecorder(applicationContext)
		if (recorder !is WavRecorder) {
			Toast.makeText(this, R.string.filter_wav_only, Toast.LENGTH_SHORT).show()
			return
		}
		val prefs: Prefs = ARApplication.injector.providePrefs(applicationContext)
		val wavRecorder = recorder
		val next = (prefs.getHpfMode() + 1) % 3 // OFF -> 80 -> 120 -> OFF
		prefs.setHpfMode(next)
		wavRecorder.setHpfMode(next)
		AudioMonitor.getInstance().setHpfMode(next)
		updateFilterButtonAlpha()
		val msgRes = when (next) {
			AppConstants.HPF_80 -> R.string.hpf_80
			AppConstants.HPF_120 -> R.string.hpf_120
			else -> R.string.hpf_off
		}
		Toast.makeText(this, msgRes, Toast.LENGTH_SHORT).show()
	}

	private fun toggleLpf() {
		val recorder = ARApplication.injector.provideAudioRecorder(applicationContext)
		if (recorder !is WavRecorder) {
			Toast.makeText(this, R.string.filter_wav_only, Toast.LENGTH_SHORT).show()
			return
		}
		val prefs: Prefs = ARApplication.injector.providePrefs(applicationContext)
		val wavRecorder = recorder
		val next = (prefs.getLpfMode() + 1) % 3 // OFF -> 9500 -> 15000 -> OFF
		prefs.setLpfMode(next)
		wavRecorder.setLpfMode(next)
		AudioMonitor.getInstance().setLpfMode(next)
		updateFilterButtonAlpha()
		val msgRes = when (next) {
			AppConstants.LPF_9500 -> R.string.lpf_9500
			AppConstants.LPF_15000 -> R.string.lpf_15000
			else -> R.string.lpf_off
		}
		Toast.makeText(this, msgRes, Toast.LENGTH_SHORT).show()
	}

	private fun updateFilterButtonAlpha() {
		val prefs: Prefs = ARApplication.injector.providePrefs(applicationContext)
		btnHpf.alpha = if (prefs.getHpfMode() != AppConstants.HPF_OFF) 1.0f else 0.5f
		btnLpf.alpha = if (prefs.getLpfMode() != AppConstants.LPF_OFF) 1.0f else 0.5f
	}

	private fun toggleNoiseGate() {
		val prefs: Prefs = ARApplication.injector.providePrefs(applicationContext)
		val monitor = AudioMonitor.getInstance()
		val enabled = !prefs.isNoiseGateEnabled()
		prefs.setNoiseGateEnabled(enabled)
		monitor.setNoiseGateEnabled(enabled)
		val rec = ARApplication.injector.provideAudioRecorder(applicationContext)
		if (rec is WavRecorder) {
			rec.setNoiseGateEnabled(enabled)
		}
		updateNoiseGateButtonAlpha()
		Toast.makeText(this,
				if (enabled) R.string.noise_gate_on else R.string.noise_gate_off,
				Toast.LENGTH_SHORT).show()
	}

	private fun updateNoiseGateButtonAlpha() {
		val prefs: Prefs = ARApplication.injector.providePrefs(applicationContext)
		btnNoiseGate.alpha = if (prefs.isNoiseGateEnabled()) 1.0f else 0.5f
	}

	private fun toggleMicBoost() {
		val recorder = ARApplication.injector.provideAudioRecorder(applicationContext)
		if (recorder !is WavRecorder) {
			Toast.makeText(this, R.string.filter_wav_only, Toast.LENGTH_SHORT).show()
			return
		}
		val prefs: Prefs = ARApplication.injector.providePrefs(applicationContext)
		val wavRecorder = recorder
		val next = (prefs.getGainBoostLevel() + 1) % 3 // OFF -> 6dB -> 12dB -> OFF
		prefs.setGainBoostLevel(next)
		wavRecorder.setGainBoostLevel(next)
		AudioMonitor.getInstance().setGainBoostLevel(next)
		updateMicBoostButtonAlpha()
		val msgRes = when (next) {
			AppConstants.GAIN_BOOST_6DB -> R.string.mic_boost_6db
			AppConstants.GAIN_BOOST_12DB -> R.string.mic_boost_12db
			else -> R.string.mic_boost_off
		}
		Toast.makeText(this, msgRes, Toast.LENGTH_SHORT).show()
	}

	private fun updateMicBoostButtonAlpha() {
		val prefs: Prefs = ARApplication.injector.providePrefs(applicationContext)
		btnMicBoost.alpha = if (prefs.getGainBoostLevel() != AppConstants.GAIN_BOOST_OFF) 1.0f else 0.5f
	}

	private fun toggleNoiseReduction() {
		val prefs: Prefs = ARApplication.injector.providePrefs(applicationContext)
		val enabled = !prefs.isNoiseReductionEnabled()
		prefs.setNoiseReductionEnabled(enabled)
		val rec = ARApplication.injector.provideAudioRecorder(applicationContext)
		if (rec is WavRecorder) {
			rec.setNoiseReductionEnabled(enabled)
		}
		updateNoiseReductionButtonAlpha()
		Toast.makeText(this,
				if (enabled) R.string.noise_reduction_on else R.string.noise_reduction_off,
				Toast.LENGTH_SHORT).show()
	}

	private fun updateNoiseReductionButtonAlpha() {
		val prefs: Prefs = ARApplication.injector.providePrefs(applicationContext)
		btnNoiseReduction.alpha = if (prefs.isNoiseReductionEnabled()) 1.0f else 0.5f
	}

	private fun showNoiseReductionDialog() {
		if (noiseReductionDialog != null && noiseReductionDialog!!.isShowing) return
		val pb = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
		pb.isIndeterminate = false
		pb.max = 100
		pb.progress = 0
		pb.setPadding(48, 24, 48, 24)
		noiseReductionDialog = AlertDialog.Builder(this)
				.setTitle(R.string.noise_reduction_processing)
				.setView(pb)
				.setCancelable(false)
				.create()
		noiseReductionDialog!!.show()
	}

	private fun updateNoiseReductionDialog(percent: Int) {
		if (noiseReductionDialog != null && noiseReductionDialog!!.isShowing) {
			var pb = noiseReductionDialog!!.findViewById<ProgressBar>(android.R.id.progress)
			if (pb == null) {
				// 找到我们设置为内容视图的 ProgressBar
				val v = noiseReductionDialog!!.window!!.decorView
				if (v is android.view.ViewGroup) {
					pb = findProgressBar(v)
				}
			}
			pb?.progress = percent
		}
	}

	private fun findProgressBar(group: android.view.ViewGroup): ProgressBar? {
		for (i in 0 until group.childCount) {
			val child = group.getChildAt(i)
			if (child is ProgressBar) {
				return child
			} else if (child is android.view.ViewGroup) {
				val result = findProgressBar(child)
				if (result != null) return result
			}
		}
		return null
	}

	private fun dismissNoiseReductionDialog(success: Boolean) {
		if (noiseReductionDialog != null && noiseReductionDialog!!.isShowing) {
			noiseReductionDialog!!.dismiss()
			noiseReductionDialog = null
		}
		Toast.makeText(this,
				if (success) R.string.noise_reduction_done else R.string.noise_reduction_failed,
				Toast.LENGTH_SHORT).show()
	}

	@Deprecated("Deprecated in Java")
	override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
		super.onActivityResult(requestCode, resultCode, data)
		if (requestCode == REQ_CODE_IMPORT_AUDIO && resultCode == RESULT_OK) {
			presenter.importAudioFile(applicationContext, data!!.data!!)
		}
	}

	override fun keepScreenOn(on: Boolean) {
		if (on) {
			window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
		} else {
			window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
		}
	}

	override fun showProgress() {
		progressBar.visibility = View.VISIBLE
	}

	override fun hideProgress() {
		progressBar.visibility = View.GONE
	}

	override fun showError(message: String) {
		Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
	}

	override fun showError(resId: Int) {
		Toast.makeText(applicationContext, resId, Toast.LENGTH_LONG).show()
	}

	override fun showMessage(resId: Int) {
		Toast.makeText(applicationContext, resId, Toast.LENGTH_LONG).show()
	}

	override fun showRecordingStart() {
		txtName.isClickable = false
		txtName.isFocusable = false
		txtName.setCompoundDrawables(null, null, null, null)
		txtName.visibility = View.VISIBLE
		txtName.setText(R.string.recording_progress)
		txtZeroTime.visibility = View.INVISIBLE
		txtDuration.visibility = View.INVISIBLE
		btnRecord.setText(R.string.button_stop)
		btnPlay.isEnabled = false
		btnImport.isEnabled = false
		btnShare.isEnabled = false
		btnPlay.visibility = View.GONE
		btnImport.visibility = View.GONE
		btnShare.visibility = View.GONE
		playProgress.progress = 0
		playProgress.isEnabled = false
		txtDuration.setText(R.string.zero_time)
		waveformView.visibility = View.GONE
		recordingWaveformView.visibility = View.VISIBLE
		ivPlaceholder.visibility = View.GONE
	}

	override fun showRecordingStop() {
		txtName.isClickable = true
		txtName.isFocusable = true
		txtZeroTime.visibility = View.VISIBLE
		txtDuration.visibility = View.VISIBLE
		txtName.setCompoundDrawablesWithIntrinsicBounds(null, null, getDrawable(R.drawable.ic_pencil_small), null)
		btnRecord.setText(R.string.button_record)
		btnPlay.isEnabled = true
		btnImport.isEnabled = true
		btnShare.isEnabled = true
		btnPlay.visibility = View.VISIBLE
		btnImport.visibility = View.VISIBLE
		btnShare.visibility = View.VISIBLE
		playProgress.isEnabled = true
		btnRecordingStop.visibility = View.GONE
		btnRecordingStop.isEnabled = false
		waveformView.visibility = View.VISIBLE
		recordingWaveformView.visibility = View.GONE
		recordingWaveformView.reset()
		txtProgress.text = TimeUtils.formatTimeIntervalHourMinSec2(0)
		// 监听按钮与实际状态同步——独立监听可能仍在运行
		isMonitoringActive = AudioMonitor.getInstance().isMonitoring
		btnMonitor.alpha = if (isMonitoringActive) 1.0f else 0.5f
	}

	override fun onMonitoringDisabled() {
		runOnUiThread {
			isMonitoringActive = false
			btnMonitor.alpha = 0.5f
			// 同时重置 WavRecorder 标志，避免下次录音自动开启监听
			val recorder = ARApplication.injector.provideAudioRecorder(applicationContext)
			if (recorder is WavRecorder) {
				recorder.setMonitoringEnabled(false)
			}
		}
	}

	override fun showRecordingPause() {
		txtName.isClickable = false
		txtName.isFocusable = false
		txtName.setCompoundDrawables(null, null, null, null)
		txtName.setText(R.string.recording_paused)
		txtName.visibility = View.VISIBLE
		btnPlay.isEnabled = false
		btnImport.isEnabled = false
		btnShare.isEnabled = false
		btnPlay.visibility = View.GONE
		btnImport.visibility = View.GONE
		btnShare.visibility = View.GONE
		btnRecord.setText(R.string.button_resume)
		btnRecordingStop.visibility = View.VISIBLE
		btnRecordingStop.isEnabled = true
		playProgress.isEnabled = false
		ivPlaceholder.visibility = View.GONE
		recordingWaveformView.visibility = View.VISIBLE
	}

	override fun showRecordingResume() {
		txtName.isClickable = false
		txtName.isFocusable = false
		txtName.setCompoundDrawables(null, null, null, null)
		txtName.visibility = View.VISIBLE
		txtName.setText(R.string.recording_progress)
		txtZeroTime.visibility = View.INVISIBLE
		txtDuration.visibility = View.INVISIBLE
		btnRecord.setText(R.string.button_stop)
		btnPlay.isEnabled = false
		btnImport.isEnabled = false
		btnShare.isEnabled = false
		btnPlay.visibility = View.GONE
		btnImport.visibility = View.GONE
		btnShare.visibility = View.GONE
		btnRecordingStop.visibility = View.GONE
		btnRecordingStop.isEnabled = false
		playProgress.progress = 0
		playProgress.isEnabled = false
		txtDuration.setText(R.string.zero_time)
		ivPlaceholder.visibility = View.GONE
	}

	override fun askRecordingNewName(id: Long, file: File, showCheckbox: Boolean) {
		setRecordName(id, file, showCheckbox)
	}

	override fun onRecordingProgress(mills: Long, amp: Int) {
		runOnUiThread {
			txtProgress.text = TimeUtils.formatTimeIntervalHourMinSec2(mills)
			recordingWaveformView.addRecordAmp(amp, mills)
		}
	}

	override fun startWelcomeScreen() {
		startActivity(WelcomeActivity.getStartIntent(applicationContext))
		finish()
	}

	override fun startRecordingService() {
		try {
			val path = fileRepository.provideRecordFile().absolutePath
			val intent = Intent(applicationContext, RecordingService::class.java)
			intent.action = RecordingService.ACTION_START_RECORDING_SERVICE
			intent.putExtra(RecordingService.EXTRAS_KEY_RECORD_PATH, path)
			startService(intent)
		} catch (e: CantCreateFileException) {
			showError(ErrorParser.parseException(e))
		}
	}

	override fun startPlaybackService(name: String) {
		PlaybackService.startServiceForeground(applicationContext, name)
	}

	override fun showPlayStart(animate: Boolean) {
		btnRecord.isEnabled = false
		if (animate) {
			AnimationUtil.viewAnimationX(btnPlay, -space, object : Animator.AnimatorListener {
				override fun onAnimationStart(animation: Animator) {}

				override fun onAnimationEnd(animation: Animator) {
					btnStop.visibility = View.VISIBLE
					btnPlay.setImageResource(R.drawable.ic_pause)
				}

				override fun onAnimationCancel(animation: Animator) {}

				override fun onAnimationRepeat(animation: Animator) {}
			})
		} else {
			btnPlay.translationX = -space
			btnStop.visibility = View.VISIBLE
			btnPlay.setImageResource(R.drawable.ic_pause)
		}
	}

	override fun showPlayPause() {
		btnStop.visibility = View.VISIBLE
		btnPlay.translationX = -space
		btnPlay.setImageResource(R.drawable.ic_play)
	}

	override fun showPlayStop() {
		btnPlay.setImageResource(R.drawable.ic_play)
		waveformView.moveToStart()
		btnRecord.isEnabled = true
		playProgress.progress = 0
		txtProgress.text = TimeUtils.formatTimeIntervalHourMinSec2(0)
		AnimationUtil.viewAnimationX(btnPlay, 0f, object : Animator.AnimatorListener {
			override fun onAnimationStart(animation: Animator) {}

			override fun onAnimationEnd(animation: Animator) {
				btnStop.visibility = View.GONE
			}

			override fun onAnimationCancel(animation: Animator) {}

			override fun onAnimationRepeat(animation: Animator) {}
		})
	}

	override fun showWaveForm(waveForm: IntArray, duration: Long, playbackMills: Long) {
		if (waveForm.isNotEmpty()) {
			btnPlay.visibility = View.VISIBLE
			txtDuration.visibility = View.VISIBLE
			txtZeroTime.visibility = View.VISIBLE
			ivPlaceholder.visibility = View.GONE
			waveformView.visibility = View.VISIBLE
		} else {
			btnPlay.visibility = View.INVISIBLE
			txtDuration.visibility = View.INVISIBLE
			txtZeroTime.visibility = View.INVISIBLE
			ivPlaceholder.visibility = View.VISIBLE
			waveformView.visibility = View.INVISIBLE
		}
		waveformView.setWaveform(waveForm, duration / 1000, playbackMills)
	}

	override fun waveFormToStart() {
		waveformView.seekPx(0)
	}

	override fun showDuration(duration: String) {
		txtDuration.text = duration
	}

	override fun showRecordingProgress(progress: String) {
		txtProgress.text = progress
	}

	override fun showName(name: String) {
		txtName.visibility = if (name.isEmpty()) View.INVISIBLE else View.VISIBLE
		txtName.text = name
	}

	override fun showInformation(info: String) {
		runOnUiThread { txtRecordInfo.text = info }
	}

	override fun decodeRecord(id: Int) {
		DecodeService.startNotification(applicationContext, id)
	}

	override fun askDeleteRecord(name: String) {
		AndroidUtils.showDialogYesNo(
				this,
				R.drawable.ic_delete_forever_dark,
				getString(R.string.warning),
				getString(R.string.delete_record, name)
		) { presenter.deleteActiveRecord() }
	}

	override fun showRecordInfo(info: RecordInfo) {
		startActivity(ActivityInformation.getStartIntent(applicationContext, info))
	}

	override fun updateRecordingView(data: IntArrayList, durationMills: Long) {
		recordingWaveformView.setRecordingData(data, durationMills)
	}

	override fun showRecordsLostMessage(list: List<Record>) {
		AndroidUtils.showLostRecordsDialog(this, list)
	}

	override fun shareRecord(record: Record) {
		AndroidUtils.shareAudioFile(applicationContext, record.path, record.name, record.format)
	}

	override fun openFile(record: Record) {
		AndroidUtils.openAudioFile(applicationContext, record.path, record.name)
	}

	override fun downloadRecord(record: Record) {
		if (isPublicDir(record.path)) {
			if (checkStoragePermissionDownload()) {
				DownloadService.startNotification(applicationContext, record.path)
			}
		} else {
			DownloadService.startNotification(applicationContext, record.path)
		}
	}

	private fun isPublicDir(path: String): Boolean {
		return FileUtil.getAppDir()?.let { path.contains(it.absolutePath) } == true
	}

	override fun showMigratePublicStorageWarning() {
		AndroidUtils.showDialog(
				this,
				R.drawable.ic_warning_yellow,
				R.string.view_records,
				R.string.later,
				R.string.move_records_needed,
				R.string.move_records_info,
				false,
				{ startActivity(MoveRecordsActivity.getStartIntent(applicationContext, false)) },
				{}
		)
	}

	override fun showRecordFileNotAvailable(path: String) {
		AndroidUtils.showRecordFileNotAvailable(this, path)
	}

	override fun onPlayProgress(mills: Long, percent: Int) {
		playProgress.progress = percent
		waveformView.setPlayback(mills)
		txtProgress.text = TimeUtils.formatTimeIntervalHourMinSec2(mills)
	}

	override fun showImportStart() {
		btnImport.visibility = View.INVISIBLE
		pnlImportProgress.visibility = View.VISIBLE
	}

	override fun hideImportProgress() {
		pnlImportProgress.visibility = View.INVISIBLE
		btnImport.visibility = View.VISIBLE
	}

	override fun showOptionsMenu() {
		btnShare.visibility = View.VISIBLE
	}

	override fun hideOptionsMenu() {
		btnShare.visibility = View.INVISIBLE
	}

	override fun showRecordProcessing() {
		pnlRecordProcessing.visibility = View.VISIBLE
	}

	override fun hideRecordProcessing() {
		pnlRecordProcessing.visibility = View.INVISIBLE
	}

	private fun showMenu(v: View) {
		val popup = PopupMenu(v.context, v)
		popup.setOnMenuItemClickListener { item ->
			val id = item.itemId
			if (id == R.id.menu_share) {
				presenter.onShareRecordClick()
			} else if (id == R.id.menu_info) {
				presenter.onRecordInfo()
			} else if (id == R.id.menu_rename) {
				presenter.onRenameRecordClick()
			} else if (id == R.id.menu_open_with) {
				presenter.onOpenFileClick()
			} else if (id == R.id.menu_save_as) {
				AndroidUtils.showDialogYesNo(
						this,
						R.drawable.ic_save_alt_dark,
						getString(R.string.save_as),
						getString(R.string.record_will_be_copied_into_downloads)
				) { presenter.onSaveAsClick() }
			} else if (id == R.id.menu_delete) {
				presenter.onDeleteClick()
			}
			false
		}
		val inflater: MenuInflater = popup.menuInflater
		inflater.inflate(R.menu.menu_more, popup.menu)
		AndroidUtils.insertMenuItemIcons(v.context, popup)
		popup.show()
	}

	fun setRecordName(recordId: Long, file: File, showCheckbox: Boolean) {
		val info = AudioDecoder.readRecordInfo(file)
		AndroidUtils.showRenameDialog(this, info.name, showCheckbox, { newName ->
			if (!info.name.equals(newName, ignoreCase = true)) {
				presenter.renameRecord(recordId, newName, info.format)
			}
		}, {}) { _, isChecked -> presenter.setAskToRename(!isChecked) }
	}

	private fun checkStoragePermissionDownload(): Boolean {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
					&& checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
				requestPermissions(
						arrayOf(
								Manifest.permission.WRITE_EXTERNAL_STORAGE,
								Manifest.permission.READ_EXTERNAL_STORAGE),
						REQ_CODE_READ_EXTERNAL_STORAGE_DOWNLOAD)
				return false
			}
		}
		return true
	}

	private fun checkStoragePermissionImport(): Boolean {
		if (presenter.isStorePublic()) {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
				if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
						&& checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
					requestPermissions(
							arrayOf(
									Manifest.permission.WRITE_EXTERNAL_STORAGE,
									Manifest.permission.READ_EXTERNAL_STORAGE),
							REQ_CODE_READ_EXTERNAL_STORAGE_IMPORT)
					return false
				}
			}
		}
		return true
	}

	private fun checkStoragePermissionPlayback(): Boolean {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
					&& checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
				requestPermissions(
						arrayOf(
								Manifest.permission.WRITE_EXTERNAL_STORAGE,
								Manifest.permission.READ_EXTERNAL_STORAGE),
						REQ_CODE_READ_EXTERNAL_STORAGE_PLAYBACK)
				return false
			}
		}
		return true
	}

	private fun checkRecordPermission2(): Boolean {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
				requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_CODE_RECORD_AUDIO)
				return false
			}
		}
		return true
	}

	private fun checkNotificationPermission(): Boolean {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
				requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_CODE_POST_NOTIFICATIONS)
				return false
			}
		}
		return true
	}

	private fun checkStoragePermission2(): Boolean {
		if (presenter.isStorePublic()) {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
				if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
					AndroidUtils.showDialog(this, R.string.warning, R.string.need_write_permission,
							{ requestPermissions(
									arrayOf(
											Manifest.permission.WRITE_EXTERNAL_STORAGE,
											Manifest.permission.READ_EXTERNAL_STORAGE),
									REQ_CODE_WRITE_EXTERNAL_STORAGE) }, null)
					return false
				}
			}
		}
		return true
	}

	@Deprecated("Deprecated in Java")
	override fun onRequestPermissionsResult(requestCode: Int, @NonNull permissions: Array<String>, @NonNull grantResults: IntArray) {
		if (requestCode == REQ_CODE_REC_AUDIO_AND_WRITE_EXTERNAL && grantResults.isNotEmpty()
					&& grantResults[0] == PackageManager.PERMISSION_GRANTED
					&& grantResults[1] == PackageManager.PERMISSION_GRANTED
					&& grantResults[2] == PackageManager.PERMISSION_GRANTED) {
			startRecordingService()
		} else if (requestCode == REQ_CODE_RECORD_AUDIO && grantResults.isNotEmpty()
				&& grantResults[0] == PackageManager.PERMISSION_GRANTED) {
			if (checkStoragePermission2()) {
				startRecordingService()
			}
		} else if (requestCode == REQ_CODE_WRITE_EXTERNAL_STORAGE && grantResults.isNotEmpty()
				&& grantResults[0] == PackageManager.PERMISSION_GRANTED
				&& grantResults[1] == PackageManager.PERMISSION_GRANTED) {
			if (checkRecordPermission2()) {
				startRecordingService()
			}
		} else if (requestCode == REQ_CODE_READ_EXTERNAL_STORAGE_IMPORT && grantResults.isNotEmpty()
				&& grantResults[0] == PackageManager.PERMISSION_GRANTED
				&& grantResults[1] == PackageManager.PERMISSION_GRANTED) {
			startFileSelector()
		} else if (requestCode == REQ_CODE_READ_EXTERNAL_STORAGE_DOWNLOAD && grantResults.isNotEmpty()
				&& grantResults[0] == PackageManager.PERMISSION_GRANTED
				&& grantResults[1] == PackageManager.PERMISSION_GRANTED) {
			presenter.onSaveAsClick()
		} else if (requestCode == REQ_CODE_READ_EXTERNAL_STORAGE_PLAYBACK && grantResults.isNotEmpty()
				&& grantResults[0] == PackageManager.PERMISSION_GRANTED
				&& grantResults[1] == PackageManager.PERMISSION_GRANTED) {
			presenter.startPlayback()
		} else if (requestCode == REQ_CODE_WRITE_EXTERNAL_STORAGE && grantResults.isNotEmpty()
				&& (grantResults[0] == PackageManager.PERMISSION_DENIED
						|| grantResults[1] == PackageManager.PERMISSION_DENIED)) {
			presenter.setStoragePrivate(applicationContext)
			startRecordingService()
		} else if (requestCode == REQ_CODE_POST_NOTIFICATIONS && grantResults.isNotEmpty()
				&& grantResults[0] == PackageManager.PERMISSION_GRANTED) {
			//通知权限已授予，无需处理
		}
	}

	companion object {
		const val REQ_CODE_REC_AUDIO_AND_WRITE_EXTERNAL = 101
		const val REQ_CODE_RECORD_AUDIO = 303
		const val REQ_CODE_WRITE_EXTERNAL_STORAGE = 404
		const val REQ_CODE_READ_EXTERNAL_STORAGE_IMPORT = 405
		const val REQ_CODE_READ_EXTERNAL_STORAGE_PLAYBACK = 406
		const val REQ_CODE_READ_EXTERNAL_STORAGE_DOWNLOAD = 407
		const val REQ_CODE_POST_NOTIFICATIONS = 408
		const val REQ_CODE_IMPORT_AUDIO = 11

		@JvmStatic
		fun getStartIntent(context: Context): Intent {
			return Intent(context, MainActivity::class.java)
		}
	}
}
