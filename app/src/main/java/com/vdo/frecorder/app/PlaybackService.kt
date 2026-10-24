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
import android.os.Build
import android.os.IBinder
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.vdo.frecorder.ARApplication
import com.vdo.frecorder.AppConstants
import com.vdo.frecorder.ColorMap
import com.vdo.frecorder.R
import com.vdo.frecorder.app.main.MainActivity
import com.vdo.frecorder.audio.player.AudioPlayerNew
import com.vdo.frecorder.audio.player.PlayerContractNew
import com.vdo.frecorder.exception.AppException
import com.vdo.frecorder.util.TimeUtils
import com.vdo.frecorder.util.isUsingNightModeResources
import timber.log.Timber

/** 播放前台服务（通知栏控制）。Kotlin 化（原 PlaybackService.java）。 */
class PlaybackService : Service() {

	private var notificationManager: NotificationManagerCompat? = null
	private var contentPendingIntent: PendingIntent? = null
	private var remoteViewsSmall: RemoteViews? = null
	private var remoteViewsBig: RemoteViews? = null
	private var recordName = ""
	private var started = false

	private lateinit var audioPlayer: PlayerContractNew.Player
	private var playerCallback: PlayerContractNew.PlayerCallback? = null
	private lateinit var colorMap: ColorMap

	override fun onBind(intent: Intent?): IBinder? {
		return null
	}

	override fun onCreate() {
		super.onCreate()

		audioPlayer = ARApplication.injector.provideAudioPlayer()
		if (audioPlayer is AudioPlayerNew) {
			(audioPlayer as AudioPlayerNew).setContext(applicationContext)
		}
		colorMap = ARApplication.injector.provideColorMap(applicationContext)

		if (playerCallback == null) {
			playerCallback = object : PlayerContractNew.PlayerCallback {
				override fun onError(throwable: AppException) {
					stopForegroundService()
				}

				override fun onStopPlay() {
					stopForegroundService()
				}

				override fun onSeek(mills: Long) {}

				override fun onPausePlay() {
					onPausePlayback()
				}

				override fun onPlayProgress(mills: Long) {}

				override fun onStartPlay() {
					onStartPlayback()
				}
			}
			audioPlayer.addPlayerCallback(playerCallback!!)
		}
	}

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		if (intent != null) {
			val action = intent.action
			if (!action.isNullOrEmpty()) {
				when (action) {
					ACTION_START_PLAYBACK_SERVICE -> if (!started && intent.hasExtra(EXTRAS_KEY_RECORD_NAME)) {
						recordName = intent.getStringExtra(EXTRAS_KEY_RECORD_NAME) ?: ""
						startForegroundService()
					}
					ACTION_PAUSE_PLAYBACK -> if (audioPlayer.isPlaying()) {
						audioPlayer.pause()
					} else if (audioPlayer.isPaused()) {
						audioPlayer.unpause()
					}
					ACTION_CLOSE -> {
						audioPlayer.stop()
						stopForegroundService()
					}
				}
			}
		}
		return super.onStartCommand(intent, flags, startId)
	}

	@SuppressLint("WrongConstant")
	private fun startForegroundService() {
		notificationManager = NotificationManagerCompat.from(this)

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			createNotificationChannel(CHANNEL_ID, CHANNEL_NAME)
		}

		val isNightMode = isUsingNightModeResources(applicationContext)

		remoteViewsSmall = RemoteViews(packageName, R.layout.layout_play_notification_small)
		remoteViewsSmall!!.setOnClickPendingIntent(R.id.btn_pause, getPendingSelfIntent(applicationContext, ACTION_PAUSE_PLAYBACK))
		remoteViewsSmall!!.setOnClickPendingIntent(R.id.btn_close, getPendingSelfIntent(applicationContext, ACTION_CLOSE))
		remoteViewsSmall!!.setTextViewText(R.id.txt_name, recordName)
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
			remoteViewsSmall!!.setInt(R.id.container, "setBackgroundColor", @Suppress("DEPRECATION") this.resources.getColor(colorMap.primaryColorRes))
			remoteViewsSmall!!.setInt(R.id.app_logo, "setVisibility", View.VISIBLE)
		} else {
			remoteViewsSmall!!.setInt(R.id.container, "setBackgroundColor", @Suppress("DEPRECATION") this.resources.getColor(R.color.transparent))
			remoteViewsSmall!!.setInt(R.id.app_logo, "setVisibility", View.GONE)
			if (isNightMode) {
				remoteViewsSmall!!.setInt(R.id.txt_name, "setTextColor", @Suppress("DEPRECATION") this.resources.getColor(R.color.text_primary_light))
				remoteViewsSmall!!.setInt(R.id.txt_app_label, "setTextColor", @Suppress("DEPRECATION") this.resources.getColor(R.color.text_primary_light))
				remoteViewsSmall!!.setInt(R.id.txt_playback_progress, "setTextColor", @Suppress("DEPRECATION") this.resources.getColor(R.color.text_secondary_light))
				remoteViewsSmall!!.setInt(R.id.btn_close, "setImageResource", R.drawable.ic_stop)
				remoteViewsSmall!!.setInt(R.id.btn_pause, "setImageResource", R.drawable.ic_pause_light)
			} else {
				remoteViewsSmall!!.setInt(R.id.txt_name, "setTextColor", @Suppress("DEPRECATION") this.resources.getColor(R.color.text_primary_dark))
				remoteViewsSmall!!.setInt(R.id.txt_app_label, "setTextColor", @Suppress("DEPRECATION") this.resources.getColor(R.color.text_primary_dark))
				remoteViewsSmall!!.setInt(R.id.txt_playback_progress, "setTextColor", @Suppress("DEPRECATION") this.resources.getColor(R.color.text_secondary_dark))
				remoteViewsSmall!!.setInt(R.id.btn_close, "setImageResource", R.drawable.ic_stop_dark)
				remoteViewsSmall!!.setInt(R.id.btn_pause, "setImageResource", R.drawable.ic_pause_dark)
			}
		}

		remoteViewsBig = RemoteViews(packageName, R.layout.layout_play_notification_big)
		remoteViewsBig!!.setOnClickPendingIntent(R.id.btn_pause, getPendingSelfIntent(applicationContext, ACTION_PAUSE_PLAYBACK))
		remoteViewsBig!!.setOnClickPendingIntent(R.id.btn_close, getPendingSelfIntent(applicationContext, ACTION_CLOSE))
		remoteViewsBig!!.setTextViewText(R.id.txt_name, recordName)
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
			remoteViewsBig!!.setInt(R.id.container, "setBackgroundColor", @Suppress("DEPRECATION") this.resources.getColor(colorMap.primaryColorRes))
			remoteViewsBig!!.setInt(R.id.app_logo, "setVisibility", View.VISIBLE)
		} else {
			remoteViewsBig!!.setInt(R.id.container, "setBackgroundColor", @Suppress("DEPRECATION") this.resources.getColor(R.color.transparent))
			remoteViewsBig!!.setInt(R.id.app_logo, "setVisibility", View.GONE)
			if (isNightMode) {
				remoteViewsBig!!.setInt(R.id.txt_name, "setTextColor", @Suppress("DEPRECATION") this.resources.getColor(R.color.text_primary_light))
				remoteViewsBig!!.setInt(R.id.txt_app_label, "setTextColor", @Suppress("DEPRECATION") this.resources.getColor(R.color.text_primary_light))
				remoteViewsBig!!.setInt(R.id.btn_close, "setImageResource", R.drawable.ic_stop)
				remoteViewsBig!!.setInt(R.id.btn_pause, "setImageResource", R.drawable.ic_pause_light)
			} else {
				remoteViewsBig!!.setInt(R.id.txt_name, "setTextColor", @Suppress("DEPRECATION") this.resources.getColor(R.color.text_primary_dark))
				remoteViewsBig!!.setInt(R.id.txt_app_label, "setTextColor", @Suppress("DEPRECATION") this.resources.getColor(R.color.text_primary_dark))
				remoteViewsBig!!.setInt(R.id.btn_close, "setImageResource", R.drawable.ic_stop_dark)
				remoteViewsBig!!.setInt(R.id.btn_pause, "setImageResource", R.drawable.ic_pause_dark)
			}
		}

		//点击通知跳转主界面
		val intent = Intent(applicationContext, MainActivity::class.java)
		intent.flags = Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP
		contentPendingIntent = PendingIntent.getActivity(applicationContext, 0, intent, AppConstants.PENDING_INTENT_FLAGS)
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
			startForeground(NOTIF_ID, buildNotification())
		} else {
			startForeground(NOTIF_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
		}
		started = true
	}

	private fun buildNotification(): Notification {
		val builder = NotificationCompat.Builder(this, CHANNEL_ID)

		builder.setWhen(System.currentTimeMillis())
		builder.setContentTitle(resources.getString(R.string.app_name))
		builder.setSmallIcon(R.drawable.ic_play_circle)
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
			builder.setPriority(NotificationManagerCompat.IMPORTANCE_LOW)
		} else {
			builder.priority = Notification.PRIORITY_LOW
		}
		builder.setContentIntent(contentPendingIntent)
		builder.setCustomContentView(remoteViewsSmall)
		builder.setCustomBigContentView(remoteViewsBig)
		builder.setOngoing(true)
		builder.setOnlyAlertOnce(true)
		builder.setDefaults(0)
		builder.setSound(null)
		return builder.build()
	}

	fun stopForegroundService() {
		playerCallback?.let { audioPlayer.removePlayerCallback(it) }
		@Suppress("DEPRECATION")
		stopForeground(true)
		stopSelf()
		started = false
	}

	@SuppressLint("WrongConstant")
	protected fun getPendingSelfIntent(context: Context, action: String): PendingIntent {
		val intent = Intent(context, StopPlaybackReceiver::class.java)
		intent.action = action
		return PendingIntent.getBroadcast(context, 10, intent, AppConstants.PENDING_INTENT_FLAGS)
	}

	@RequiresApi(Build.VERSION_CODES.O)
	private fun createNotificationChannel(channelId: String, channelName: String) {
		val channel = notificationManager!!.getNotificationChannel(channelId)
		if (channel == null) {
			val chan = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH)
			chan.setLightColor(Color.BLUE)
			chan.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
			chan.setSound(null, null)
			chan.enableLights(false)
			chan.enableVibration(false)

			notificationManager!!.createNotificationChannel(chan)
		} else {
			Timber.d("Channel already exists: %s", CHANNEL_ID)
		}
	}

	private fun updateNotification(mills: Long) {
		if (started && remoteViewsSmall != null) {
			remoteViewsSmall!!.setTextViewText(R.id.txt_playback_progress,
					resources.getString(R.string.playback, TimeUtils.formatTimeIntervalHourMinSec2(mills)))

			remoteViewsBig!!.setTextViewText(R.id.txt_playback_progress,
					resources.getString(R.string.playback, TimeUtils.formatTimeIntervalHourMinSec2(mills)))

			notificationManager!!.notify(NOTIF_ID, buildNotification())
		}
	}

	fun onPausePlayback() {
		if (started && remoteViewsSmall != null) {
			val isNightMode = isUsingNightModeResources(applicationContext)
			if (isNightMode || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
				remoteViewsBig?.setImageViewResource(R.id.btn_pause, R.drawable.ic_play_light)
				remoteViewsSmall!!.setImageViewResource(R.id.btn_pause, R.drawable.ic_play_light)
			} else {
				remoteViewsBig?.setImageViewResource(R.id.btn_pause, R.drawable.ic_play_dark)
				remoteViewsSmall!!.setImageViewResource(R.id.btn_pause, R.drawable.ic_play_dark)
			}
			notificationManager!!.notify(NOTIF_ID, buildNotification())
		}
	}

	fun onStartPlayback() {
		if (started && remoteViewsSmall != null) {
			val isNightMode = isUsingNightModeResources(applicationContext)
			if (isNightMode || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
				remoteViewsBig?.setImageViewResource(R.id.btn_pause, R.drawable.ic_pause_light)
				remoteViewsSmall!!.setImageViewResource(R.id.btn_pause, R.drawable.ic_pause_light)
			} else {
				remoteViewsBig?.setImageViewResource(R.id.btn_pause, R.drawable.ic_pause_dark)
				remoteViewsSmall!!.setImageViewResource(R.id.btn_pause, R.drawable.ic_pause_dark)
			}
			notificationManager!!.notify(NOTIF_ID, buildNotification())
		}
	}

	class StopPlaybackReceiver : BroadcastReceiver() {

		override fun onReceive(context: Context, intent: Intent) {
			val stopIntent = Intent(context, PlaybackService::class.java)
			stopIntent.action = intent.action
			context.startService(stopIntent)
		}
	}

	companion object {
		private const val CHANNEL_NAME = "Default"
		private const val CHANNEL_ID = "com.vdo.frecorder.NotificationId"

		const val ACTION_START_PLAYBACK_SERVICE = "ACTION_START_PLAYBACK_SERVICE"

		const val ACTION_PAUSE_PLAYBACK = "ACTION_PAUSE_PLAYBACK"

		const val ACTION_CLOSE = "ACTION_CLOSE"

		const val EXTRAS_KEY_RECORD_NAME = "record_name"

		private const val NOTIF_ID = 101

		@JvmStatic
		fun startServiceForeground(context: Context, name: String) {
			val intent = Intent(context, PlaybackService::class.java)
			intent.action = ACTION_START_PLAYBACK_SERVICE
			intent.putExtra(EXTRAS_KEY_RECORD_NAME, name)
			context.startService(intent)
		}
	}
}
