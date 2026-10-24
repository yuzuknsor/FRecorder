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

package com.vdo.frecorder.app

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.media.AudioDeviceInfo
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.view.View
import android.widget.RemoteViews
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.vdo.frecorder.ARApplication
import com.vdo.frecorder.AppConstants
import com.vdo.frecorder.BackgroundQueue
import com.vdo.frecorder.ColorMap
import com.vdo.frecorder.R
import com.vdo.frecorder.app.main.MainActivity
import com.vdo.frecorder.audio.AudioDeviceManager
import com.vdo.frecorder.audio.RecordingDeviceOverride
import com.vdo.frecorder.audio.player.AudioPlayerNew
import com.vdo.frecorder.audio.player.PlayerContractNew
import com.vdo.frecorder.audio.recorder.RecorderContract
import com.vdo.frecorder.audio.recorder.WavRecorder
import com.vdo.frecorder.data.FileRepository
import com.vdo.frecorder.data.Prefs
import com.vdo.frecorder.data.RecordDataSource
import com.vdo.frecorder.data.database.LocalRepository
import com.vdo.frecorder.data.database.Record
import com.vdo.frecorder.exception.AppException
import com.vdo.frecorder.exception.ErrorParser
import com.vdo.frecorder.exception.RecorderInitException
import com.vdo.frecorder.util.AndroidUtils
import com.vdo.frecorder.util.TimeUtils
import com.vdo.frecorder.util.isUsingNightModeResources
import timber.log.Timber
import java.io.IOException

/** 录音前台服务（通知栏控制与录音启动）。Kotlin 化（原 RecordingService.java）。 */
class RecordingService : Service() {

	private var notificationManager: NotificationManager? = null
	private var remoteViewsSmall: RemoteViews? = null
	private var remoteViewsBig: RemoteViews? = null
	private var contentPendingIntent: PendingIntent? = null

	private lateinit var appRecorder: AppRecorder
	private lateinit var audioPlayer: PlayerContractNew.Player
	private lateinit var recordingsTasks: BackgroundQueue
	private lateinit var localRepository: LocalRepository
	private lateinit var prefs: Prefs
	private lateinit var recordDataSource: RecordDataSource
	private lateinit var recorder: RecorderContract.Recorder
	private var appRecorderCallback: AppRecorderCallback? = null
	private lateinit var colorMap: ColorMap
	private var started = false
	private lateinit var fileRepository: FileRepository
	private lateinit var audioDeviceManager: AudioDeviceManager

	override fun onBind(intent: Intent?): IBinder? {
		throw UnsupportedOperationException("Not yet implemented")
	}

	override fun onCreate() {
		super.onCreate()
		appRecorder = ARApplication.injector.provideAppRecorder(applicationContext)
		audioPlayer = ARApplication.injector.provideAudioPlayer()
		if (audioPlayer is AudioPlayerNew) {
			(audioPlayer as AudioPlayerNew).setContext(applicationContext)
		}
		recordingsTasks = ARApplication.injector.provideRecordingTasksQueue()
		localRepository = ARApplication.injector.provideLocalRepository(applicationContext)
		prefs = ARApplication.injector.providePrefs(applicationContext)
		recorder = ARApplication.injector.provideAudioRecorder(applicationContext)
		recordDataSource = ARApplication.injector.provideRecordDataSource(applicationContext)

		colorMap = ARApplication.injector.provideColorMap(applicationContext)
		fileRepository = ARApplication.injector.provideFileRepository(applicationContext)
		audioDeviceManager = ARApplication.injector.provideAudioDeviceManager(applicationContext)

		appRecorderCallback = object : AppRecorderCallback {
			var checkHasSpace = true

			override fun onRecordingStarted(file: java.io.File) {
				updateNotificationResume()
			}

			override fun onRecordingPaused() {
				updateNotificationPause()
			}

			override fun onRecordingResumed() {
				updateNotificationResume()
			}

			override fun onRecordingStopped(file: java.io.File, rec: Record) {
				if (rec.duration / 1000 < AppConstants.DECODE_DURATION && !rec.isWaveformProcessed) {
					DecodeService.startNotification(applicationContext, rec.id)
				}
				stopForegroundService()
			}

			override fun onRecordingProgress(mills: Long, amp: Int) {
				try {
					if (mills % 10000 < 1000) {
						if (checkHasSpace && !fileRepository.hasAvailableSpace(applicationContext)) {
							stopRecording()
							AndroidUtils.runOnUIThread {
								Toast.makeText(applicationContext, R.string.error_no_available_space, Toast.LENGTH_LONG).show()
							}
							showNoSpaceNotification()
						}
						checkHasSpace = false
					} else {
						checkHasSpace = true
					}
				} catch (e: IllegalArgumentException) {
					stopRecording()
					AndroidUtils.runOnUIThread {
						Toast.makeText(applicationContext, R.string.error_failed_access_to_storage, Toast.LENGTH_LONG).show()
					}
					showNoSpaceNotification()
				}
			}

			override fun onError(throwable: AppException) {
				showError(ErrorParser.parseException(throwable))
				stopForegroundService()
			}
		}
		appRecorder.addRecordingCallback(appRecorderCallback)
	}

	fun showNoSpaceNotification() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			createNotificationChannel(CHANNEL_ID_ERRORS, CHANNEL_NAME_ERRORS)
		}
		val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
				.setSmallIcon(R.drawable.ic_record_rec)
				.setContentTitle(applicationContext.getString(R.string.app_name))
				.setContentText(applicationContext.getString(R.string.error_no_available_space))
				.setContentIntent(createContentIntent())
				.setVibrate(longArrayOf(1000, 1000, 1000, 1000, 1000))
				.setLights(Color.RED, 500, 500)
				.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
				.setAutoCancel(true)
				.setPriority(NotificationCompat.PRIORITY_MAX)

		val notificationManager = NotificationManagerCompat.from(applicationContext)
		notificationManager.notify(303, builder.build())
	}

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		if (intent != null) {
			val action = intent.action
			if (!action.isNullOrEmpty()) {
				when (action) {
					ACTION_START_RECORDING_SERVICE -> if (!started) {
						startForegroundService()
						if (intent.hasExtra(EXTRAS_KEY_RECORD_PATH)) {
							startRecording(intent.getStringExtra(EXTRAS_KEY_RECORD_PATH) ?: "")
						} else {
							showError(ErrorParser.parseException(RecorderInitException()))
							stopForegroundService()
						}
					}
					ACTION_STOP_RECORDING_SERVICE -> stopForegroundService()
					ACTION_STOP_RECORDING -> stopRecording()
					ACTION_PAUSE_RECORDING -> if (appRecorder.isPaused()) {
						appRecorder.resumeRecording()
					} else {
						appRecorder.pauseRecording()
					}
				}
			}
		}
		return super.onStartCommand(intent, flags, startId)
	}

	private fun stopRecording() {
		appRecorder.stopRecording()
	}

	private fun startForegroundService() {
		notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			createNotificationChannel(CHANNEL_ID, CHANNEL_NAME)
		}

		val isNightMode = isUsingNightModeResources(applicationContext)

		remoteViewsSmall = RemoteViews(packageName, R.layout.layout_record_notification_small)
		remoteViewsSmall!!.setOnClickPendingIntent(R.id.btn_recording_stop, getPendingSelfIntent(applicationContext, ACTION_STOP_RECORDING))
		remoteViewsSmall!!.setOnClickPendingIntent(R.id.btn_recording_pause, getPendingSelfIntent(applicationContext, ACTION_PAUSE_RECORDING))
		remoteViewsSmall!!.setTextViewText(R.id.txt_recording_progress, resources.getString(R.string.recording_is_on))
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
			remoteViewsSmall!!.setInt(R.id.container, "setBackgroundColor", @Suppress("DEPRECATION") this.resources.getColor(colorMap.primaryColorRes))
			remoteViewsSmall!!.setInt(R.id.app_logo, "setVisibility", View.VISIBLE)
		} else {
			remoteViewsSmall!!.setInt(R.id.container, "setBackgroundColor", @Suppress("DEPRECATION") this.resources.getColor(R.color.transparent))
			remoteViewsSmall!!.setInt(R.id.app_logo, "setVisibility", View.GONE)
			if (isNightMode) {
				remoteViewsSmall!!.setInt(R.id.txt_name, "setTextColor", @Suppress("DEPRECATION") this.resources.getColor(R.color.text_primary_light))
				remoteViewsSmall!!.setInt(R.id.txt_app_label, "setTextColor", @Suppress("DEPRECATION") this.resources.getColor(R.color.text_primary_light))
				remoteViewsSmall!!.setInt(R.id.txt_recording_progress, "setTextColor", @Suppress("DEPRECATION") this.resources.getColor(R.color.text_primary_light))
				remoteViewsSmall!!.setInt(R.id.btn_recording_stop, "setImageResource", R.drawable.ic_stop)
				remoteViewsSmall!!.setInt(R.id.btn_recording_pause, "setImageResource", R.drawable.ic_pause_light)
			} else {
				remoteViewsSmall!!.setInt(R.id.txt_name, "setTextColor", @Suppress("DEPRECATION") this.resources.getColor(R.color.text_primary_dark))
				remoteViewsSmall!!.setInt(R.id.txt_app_label, "setTextColor", @Suppress("DEPRECATION") this.resources.getColor(R.color.text_primary_dark))
				remoteViewsSmall!!.setInt(R.id.txt_recording_progress, "setTextColor", @Suppress("DEPRECATION") this.resources.getColor(R.color.text_primary_dark))
				remoteViewsSmall!!.setInt(R.id.btn_recording_stop, "setImageResource", R.drawable.ic_stop_dark)
				remoteViewsSmall!!.setInt(R.id.btn_recording_pause, "setImageResource", R.drawable.ic_pause_dark)
			}
		}

		remoteViewsBig = RemoteViews(packageName, R.layout.layout_record_notification_big)
		remoteViewsBig!!.setOnClickPendingIntent(R.id.btn_recording_stop, getPendingSelfIntent(applicationContext, ACTION_STOP_RECORDING))
		remoteViewsBig!!.setOnClickPendingIntent(R.id.btn_recording_pause, getPendingSelfIntent(applicationContext, ACTION_PAUSE_RECORDING))
		remoteViewsBig!!.setTextViewText(R.id.txt_recording_progress, TimeUtils.formatTimeIntervalMinSecMills(0))
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
			remoteViewsBig!!.setInt(R.id.container, "setBackgroundColor", @Suppress("DEPRECATION") this.resources.getColor(colorMap.primaryColorRes))
			remoteViewsBig!!.setInt(R.id.app_logo, "setVisibility", View.VISIBLE)
		} else {
			remoteViewsBig!!.setInt(R.id.container, "setBackgroundColor", @Suppress("DEPRECATION") this.resources.getColor(R.color.transparent))
			remoteViewsBig!!.setInt(R.id.app_logo, "setVisibility", View.GONE)
			if (isNightMode) {
				remoteViewsBig!!.setInt(R.id.txt_app_label, "setTextColor", @Suppress("DEPRECATION") this.resources.getColor(R.color.text_primary_light))
				remoteViewsBig!!.setInt(R.id.txt_recording_progress, "setTextColor", @Suppress("DEPRECATION") this.resources.getColor(R.color.text_primary_light))
				remoteViewsBig!!.setInt(R.id.btn_recording_stop, "setImageResource", R.drawable.ic_stop)
				remoteViewsBig!!.setInt(R.id.btn_recording_pause, "setImageResource", R.drawable.ic_pause_light)
			} else {
				remoteViewsBig!!.setInt(R.id.txt_app_label, "setTextColor", @Suppress("DEPRECATION") this.resources.getColor(R.color.text_primary_dark))
				remoteViewsBig!!.setInt(R.id.txt_recording_progress, "setTextColor", @Suppress("DEPRECATION") this.resources.getColor(R.color.text_primary_dark))
				remoteViewsBig!!.setInt(R.id.btn_recording_stop, "setImageResource", R.drawable.ic_stop_dark)
				remoteViewsBig!!.setInt(R.id.btn_recording_pause, "setImageResource", R.drawable.ic_pause_dark)
			}
		}

		contentPendingIntent = createContentIntent()
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
			startForeground(NOTIF_ID, buildNotification())
		} else {
			startForeground(NOTIF_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
		}
		started = true
	}

	private fun buildNotification(): Notification {
		val builder = NotificationCompat.Builder(this, CHANNEL_ID)

		builder.setWhen(System.currentTimeMillis())
		builder.setSmallIcon(R.drawable.ic_record_rec)
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
			builder.setPriority(NotificationCompat.PRIORITY_LOW)
		} else {
			builder.priority = Notification.PRIORITY_LOW
		}
		builder.setContentIntent(contentPendingIntent)
		builder.setCustomContentView(remoteViewsSmall)
		builder.setCustomBigContentView(remoteViewsBig)
		builder.setOnlyAlertOnce(true)
		builder.setDefaults(0)
		builder.setSound(null)
		return builder.build()
	}

	@SuppressLint("WrongConstant")
	private fun createContentIntent(): PendingIntent {
		//点击通知跳转主界面
		val intent = Intent(applicationContext, MainActivity::class.java)
		intent.flags = Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP
		return PendingIntent.getActivity(applicationContext, 0, intent, AppConstants.PENDING_INTENT_FLAGS)
	}

	private fun stopForegroundService() {
		appRecorderCallback?.let { appRecorder.removeRecordingCallback(it) }
		@Suppress("DEPRECATION")
		stopForeground(true)
		stopSelf()
		started = false
	}

	@SuppressLint("WrongConstant")
	protected fun getPendingSelfIntent(context: Context, action: String): PendingIntent {
		val intent = Intent(context, StopRecordingReceiver::class.java)
		intent.action = action
		return PendingIntent.getBroadcast(context, 10, intent, AppConstants.PENDING_INTENT_FLAGS)
	}

	@RequiresApi(Build.VERSION_CODES.O)
	private fun createNotificationChannel(channelId: String, channelName: String) {
		val channel = notificationManager!!.getNotificationChannel(channelId)
		if (channel == null) {
			val chan = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
			chan.setLightColor(Color.BLUE)
			chan.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
			chan.setSound(null, null)
			chan.enableLights(false)
			chan.enableVibration(false)

			notificationManager!!.createNotificationChannel(chan)
		}
	}

	private fun updateNotificationPause() {
		if (started && remoteViewsSmall != null) {
			val isNightMode = isUsingNightModeResources(applicationContext)
			remoteViewsSmall!!.setTextViewText(R.id.txt_recording_progress, resources.getString(R.string.recording_paused))
			if (isNightMode || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
				remoteViewsSmall!!.setImageViewResource(R.id.btn_recording_pause, R.drawable.ic_recording_light)
			} else {
				remoteViewsSmall!!.setImageViewResource(R.id.btn_recording_pause, R.drawable.ic_recording_dark)
			}

			if (remoteViewsBig != null) {
				remoteViewsBig!!.setTextViewText(R.id.txt_recording_progress, resources.getString(R.string.recording_paused))
				if (isNightMode || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
					remoteViewsBig!!.setImageViewResource(R.id.btn_recording_pause, R.drawable.ic_recording_light)
				} else {
					remoteViewsBig!!.setImageViewResource(R.id.btn_recording_pause, R.drawable.ic_recording_dark)
				}
			}

			notificationManager!!.notify(NOTIF_ID, buildNotification())
		}
	}

	private fun updateNotificationResume() {
		if (started && remoteViewsSmall != null) {
			val isNightMode = isUsingNightModeResources(applicationContext)
			remoteViewsSmall!!.setTextViewText(R.id.txt_recording_progress, resources.getString(R.string.recording_is_on))
			if (isNightMode || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
				remoteViewsSmall!!.setImageViewResource(R.id.btn_recording_pause, R.drawable.ic_pause_light)
			} else {
				remoteViewsSmall!!.setImageViewResource(R.id.btn_recording_pause, R.drawable.ic_pause_dark)
			}

			if (remoteViewsBig != null) {
				remoteViewsBig!!.setTextViewText(R.id.txt_recording_progress, resources.getString(R.string.recording_is_on))
				if (isNightMode || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
					remoteViewsBig!!.setImageViewResource(R.id.btn_recording_pause, R.drawable.ic_pause_light)
				} else {
					remoteViewsBig!!.setImageViewResource(R.id.btn_recording_pause, R.drawable.ic_pause_dark)
				}
			}

			notificationManager!!.notify(NOTIF_ID, buildNotification())
		}
	}

	private fun updateNotification(mills: Long) {
		if (started && remoteViewsSmall != null) {
			remoteViewsSmall!!.setTextViewText(R.id.txt_recording_progress,
					resources.getString(R.string.recording, TimeUtils.formatTimeIntervalHourMinSec2(mills)))

			if (remoteViewsBig != null) {
				remoteViewsBig!!.setTextViewText(R.id.txt_recording_progress,
						resources.getString(R.string.recording, TimeUtils.formatTimeIntervalHourMinSec2(mills)))
			}

			notificationManager!!.notify(NOTIF_ID, buildNotification())
		}
	}

	private fun startRecording(path: String) {
		appRecorder.setRecorder(recorder)
		try {
			if (fileRepository.hasAvailableSpace(applicationContext)) {
				if (!appRecorder.isRecording()) {
					if (audioPlayer.isPlaying() || audioPlayer.isPaused()) {
						audioPlayer.stop()
					}

					//获取选定的输入设备。一次性覆盖（快捷内置麦克风录音）优先于保存的偏好
					val forcedDeviceId = RecordingDeviceOverride.consume()
					val selectedDeviceId = if (forcedDeviceId != RecordingDeviceOverride.NONE) forcedDeviceId else prefs.getSettingAudioSource()
					var audioDevice: AudioDeviceInfo? = null
					if (selectedDeviceId != AppConstants.AUDIO_SOURCE_DEFAULT_MIC) {
						audioDevice = audioDeviceManager.getDeviceById(selectedDeviceId)
						if (audioDevice == null) {
							//设备已不可用，回落默认麦克风
							if (forcedDeviceId == RecordingDeviceOverride.NONE) {
								prefs.setSettingAudioSource(AppConstants.AUDIO_SOURCE_DEFAULT_MIC)
							}
							Timber.w("Selected audio device not available, using default microphone")
						}
					}

					val finalAudioDevice = audioDevice
					val gainBoostLevel = prefs.getGainBoostLevel()

					//把降噪与滤波偏好设置到 WavRecorder
					if (recorder is WavRecorder) {
						(recorder as WavRecorder).setNoiseReductionEnabled(prefs.isNoiseReductionEnabled())
						(recorder as WavRecorder).setNoiseReductionDb(prefs.getNoiseReductionDb())
						(recorder as WavRecorder).setNoiseReductionSensitivity(prefs.getNoiseReductionSensitivity())
						(recorder as WavRecorder).setNoiseReductionFreqSmoothing(prefs.getNoiseReductionFreqSmoothing())
						(recorder as WavRecorder).setNoiseProfileSeconds(prefs.getNoiseProfileSeconds())
						(recorder as WavRecorder).setHpfMode(prefs.getHpfMode())
						(recorder as WavRecorder).setLpfMode(prefs.getLpfMode())
						(recorder as WavRecorder).setNoiseGateEnabled(prefs.isNoiseGateEnabled())
					}
					recordingsTasks.postRunnable {
						try {
							val record = localRepository.insertEmptyFile(path)
									?: throw NullPointerException("insertEmptyFile returned null")
							prefs.activeRecord = record.id.toLong()
							recordDataSource.recordingRecord = record
							AndroidUtils.runOnUIThread {
								appRecorder.startRecording(
										path,
										prefs.getSettingChannelCount(),
										prefs.getSettingSampleRate(),
										prefs.getSettingBitrate(),
										finalAudioDevice,
										gainBoostLevel
								)
							}
						} catch (e: IOException) {
							Timber.e(e)
							showError(R.string.error_failed_to_start_recording)
						} catch (e: OutOfMemoryError) {
							Timber.e(e)
							showError(R.string.error_failed_to_start_recording)
						} catch (e: IllegalStateException) {
							Timber.e(e)
							showError(R.string.error_failed_to_start_recording)
						} catch (e: NullPointerException) {
							Timber.e(e)
							showError(R.string.error_failed_to_start_recording)
						}
					}
				}
			} else {
				showError(R.string.error_no_available_space)
				stopForegroundService()
			}
		} catch (e: IllegalArgumentException) {
			showError(R.string.error_failed_access_to_storage)
			stopForegroundService()
		}
	}

	fun showError(resId: Int) {
		Toast.makeText(applicationContext, resId, Toast.LENGTH_LONG).show()
	}

	class StopRecordingReceiver : BroadcastReceiver() {
		override fun onReceive(context: Context, intent: Intent) {
			val stopIntent = Intent(context, RecordingService::class.java)
			stopIntent.action = intent.action
			context.startService(stopIntent)
		}
	}

	companion object {
		private const val CHANNEL_NAME = "Default"
		private const val CHANNEL_ID = "com.vdo.frecorder.NotificationId"

		private const val CHANNEL_NAME_ERRORS = "Errors"
		private const val CHANNEL_ID_ERRORS = "com.vdo.frecorder.Errors"

		const val EXTRAS_KEY_RECORD_PATH = "EXTRAS_KEY_RECORD_PATH"
		const val ACTION_START_RECORDING_SERVICE = "ACTION_START_RECORDING_SERVICE"

		const val ACTION_STOP_RECORDING_SERVICE = "ACTION_STOP_RECORDING_SERVICE"

		const val ACTION_STOP_RECORDING = "ACTION_STOP_RECORDING"
		const val ACTION_PAUSE_RECORDING = "ACTION_PAUSE_RECORDING"

		private const val NOTIF_ID = 101
	}
}
