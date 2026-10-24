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
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
import android.graphics.Color
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
import com.vdo.frecorder.util.OnCopyListListener
import com.vdo.frecorder.util.downloadFiles
import com.vdo.frecorder.util.isUsingNightModeResources
import timber.log.Timber
import java.io.File

/** 下载（另存为）前台服务。Kotlin 化（原 DownloadService.java）。 */
class DownloadService : Service() {

	private var notificationManager: NotificationManagerCompat? = null
	private var remoteViewsSmall: RemoteViews? = null
	private var remoteViewsBig: RemoteViews? = null
	private var contentPendingIntent: PendingIntent? = null
	private var downloadingRecordName = ""
	private var copyTasks: BackgroundQueue? = null
	private lateinit var colorMap: ColorMap
	private var isCancel = false

	override fun onBind(intent: Intent?): IBinder? {
		return null
	}

	override fun onCreate() {
		super.onCreate()

		colorMap = ARApplication.injector.provideColorMap(applicationContext)
		copyTasks = ARApplication.injector.provideCopyTasksQueue()
	}

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		if (intent != null) {
			val action = intent.action
			if (!action.isNullOrEmpty()) {
				when (action) {
					ACTION_START_DOWNLOAD_SERVICE -> if (intent.hasExtra(EXTRAS_KEY_DOWNLOAD_INFO)) {
						startDownload(intent.getStringArrayListExtra(EXTRAS_KEY_DOWNLOAD_INFO))
					}
					ACTION_STOP_DOWNLOAD_SERVICE -> stopService()
					ACTION_CANCEL_DOWNLOAD -> {
						isCancel = true
						stopService()
					}
				}
			}
		}
		return super.onStartCommand(intent, flags, startId)
	}

	fun startDownload(list: ArrayList<String>?) {
		if (list == null || list.isEmpty()) {
			stopService()
		} else {
			val files: MutableList<File> = ArrayList()
			for (i in list.indices) {
				files.add(File(list[i]))
			}
			startNotification()
			copyFiles(files)
		}
	}

	private fun copyFiles(list: List<File>) {
		isCancel = false
		copyTasks!!.postRunnable {
			var prevTime: Long = 0
			downloadFiles(applicationContext, list, object : OnCopyListListener {
				override val isCancel: Boolean
					get() = this@DownloadService.isCancel

				override fun onStartCopy(name: String) {
					updateNotificationText(name)
				}

				override fun onCopyProgress(percent: Int) {
					var percent = percent
					val curTime = System.currentTimeMillis()
					if (percent >= 95) {
						percent = 100
					}
					if (percent == 100 || curTime > prevTime + 200) {
						updateNotification(percent)
						prevTime = curTime
					}
				}

				override fun onCanceled() {
					Toast.makeText(applicationContext, R.string.downloading_cancel, Toast.LENGTH_LONG).show()
					stopService()
				}

				override fun onCopyFinish(message: String?) {
					Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
					stopService()
				}

				override fun onError(message: String?) {
					Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
					stopService()
				}
			})
		}
	}

	@SuppressLint("WrongConstant")
	private fun startNotification() {
		notificationManager = NotificationManagerCompat.from(this)

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			createNotificationChannel(CHANNEL_ID, CHANNEL_NAME)
		}

		val isNightMode = isUsingNightModeResources(applicationContext)

		remoteViewsSmall = RemoteViews(packageName, R.layout.layout_progress_notification)
		remoteViewsSmall!!.setOnClickPendingIntent(R.id.btn_close, getPendingSelfIntent(applicationContext, ACTION_CANCEL_DOWNLOAD))
		remoteViewsSmall!!.setTextViewText(R.id.txt_name, resources.getString(R.string.downloading, downloadingRecordName))
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
			remoteViewsSmall!!.setInt(R.id.container, "setBackgroundColor", @Suppress("DEPRECATION") this.resources.getColor(colorMap.primaryColorRes))
			remoteViewsSmall!!.setInt(R.id.app_logo, "setVisibility", View.VISIBLE)
		} else {
			remoteViewsSmall!!.setInt(R.id.container, "setBackgroundColor", @Suppress("DEPRECATION") this.resources.getColor(R.color.transparent))
			remoteViewsSmall!!.setInt(R.id.app_logo, "setVisibility", View.GONE)
			if (isNightMode) {
				remoteViewsSmall!!.setInt(R.id.txt_app_label, "setTextColor", @Suppress("DEPRECATION") this.resources.getColor(R.color.text_primary_light))
				remoteViewsSmall!!.setInt(R.id.txt_name, "setTextColor", @Suppress("DEPRECATION") this.resources.getColor(R.color.text_secondary_light))
				remoteViewsSmall!!.setInt(R.id.btn_close, "setImageResource", R.drawable.ic_round_close)
			} else {
				remoteViewsSmall!!.setInt(R.id.txt_app_label, "setTextColor", @Suppress("DEPRECATION") this.resources.getColor(R.color.text_primary_dark))
				remoteViewsSmall!!.setInt(R.id.txt_name, "setTextColor", @Suppress("DEPRECATION") this.resources.getColor(R.color.text_secondary_dark))
				remoteViewsSmall!!.setInt(R.id.btn_close, "setImageResource", R.drawable.ic_round_close_dark)
			}
		}

		remoteViewsBig = RemoteViews(packageName, R.layout.layout_progress_notification)
		remoteViewsBig!!.setOnClickPendingIntent(R.id.btn_close, getPendingSelfIntent(applicationContext, ACTION_CANCEL_DOWNLOAD))
		remoteViewsBig!!.setTextViewText(R.id.txt_name, resources.getString(R.string.downloading, downloadingRecordName))
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
			remoteViewsBig!!.setInt(R.id.container, "setBackgroundColor", @Suppress("DEPRECATION") this.resources.getColor(colorMap.primaryColorRes))
			remoteViewsBig!!.setInt(R.id.app_logo, "setVisibility", View.VISIBLE)
		} else {
			remoteViewsBig!!.setInt(R.id.container, "setBackgroundColor", @Suppress("DEPRECATION") this.resources.getColor(R.color.transparent))
			remoteViewsBig!!.setInt(R.id.app_logo, "setVisibility", View.GONE)
			if (isNightMode) {
				remoteViewsBig!!.setInt(R.id.txt_app_label, "setTextColor", @Suppress("DEPRECATION") this.resources.getColor(R.color.text_primary_light))
				remoteViewsBig!!.setInt(R.id.txt_name, "setTextColor", @Suppress("DEPRECATION") this.resources.getColor(R.color.text_secondary_light))
				remoteViewsBig!!.setInt(R.id.btn_close, "setImageResource", R.drawable.ic_round_close)
			} else {
				remoteViewsBig!!.setInt(R.id.txt_app_label, "setTextColor", @Suppress("DEPRECATION") this.resources.getColor(R.color.text_primary_dark))
				remoteViewsBig!!.setInt(R.id.txt_name, "setTextColor", @Suppress("DEPRECATION") this.resources.getColor(R.color.text_secondary_dark))
				remoteViewsBig!!.setInt(R.id.btn_close, "setImageResource", R.drawable.ic_round_close_dark)
			}
		}

		//点击通知跳转主界面
		val intent = Intent(applicationContext, MainActivity::class.java)
		intent.flags = Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP
		contentPendingIntent = PendingIntent.getActivity(applicationContext, 0, intent, AppConstants.PENDING_INTENT_FLAGS)
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
			startForeground(NOTIF_ID, buildNotification())
		} else {
			startForeground(NOTIF_ID, buildNotification(), FOREGROUND_SERVICE_TYPE_DATA_SYNC)
		}
	}

	private fun buildNotification(): Notification {
		val builder = NotificationCompat.Builder(this, CHANNEL_ID)

		builder.setWhen(System.currentTimeMillis())
		builder.setContentTitle(resources.getString(R.string.app_name))
		builder.setSmallIcon(R.drawable.ic_save_alt)
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
			builder.setPriority(NotificationManagerCompat.IMPORTANCE_DEFAULT)
		} else {
			builder.priority = NotificationCompat.PRIORITY_DEFAULT
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

	fun stopService() {
		@Suppress("DEPRECATION")
		stopForeground(true)
		stopSelf()
	}

	@SuppressLint("WrongConstant")
	protected fun getPendingSelfIntent(context: Context, action: String): PendingIntent {
		val intent = Intent(context, StopDownloadReceiver::class.java)
		intent.action = action
		return PendingIntent.getBroadcast(context, 10, intent, AppConstants.PENDING_INTENT_FLAGS)
	}

	@RequiresApi(Build.VERSION_CODES.O)
	private fun createNotificationChannel(channelId: String, channelName: String) {
		val channel = notificationManager!!.getNotificationChannel(channelId)
		if (channel == null) {
			val chan = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_DEFAULT)
			chan.setLightColor(Color.BLUE)
			chan.lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
			chan.setSound(null, null)
			chan.enableLights(false)
			chan.enableVibration(false)

			notificationManager!!.createNotificationChannel(chan)
		} else {
			Timber.v("Channel already exists: %s", CHANNEL_ID)
		}
	}

	private fun updateNotification(percent: Int) {
		remoteViewsSmall!!.setProgressBar(R.id.progress, 100, percent, false)
		remoteViewsBig!!.setProgressBar(R.id.progress, 100, percent, false)
		notificationManager!!.notify(NOTIF_ID, buildNotification())
	}

	private fun updateNotificationText(text: String) {
		downloadingRecordName = text
		remoteViewsSmall!!.setTextViewText(
				R.id.txt_name,
				resources.getString(R.string.downloading, downloadingRecordName)
		)
		remoteViewsBig!!.setTextViewText(
				R.id.txt_name,
				resources.getString(R.string.downloading, downloadingRecordName)
		)
		notificationManager!!.notify(NOTIF_ID, buildNotification())
	}

	class StopDownloadReceiver : BroadcastReceiver() {

		override fun onReceive(context: Context, intent: Intent) {
			val stopIntent = Intent(context, DownloadService::class.java)
			stopIntent.action = intent.action
			context.startService(stopIntent)
		}
	}

	companion object {
		private const val CHANNEL_NAME = "Default"
		private const val CHANNEL_ID = "com.vdo.frecorder.Download.Notification"

		const val ACTION_START_DOWNLOAD_SERVICE = "ACTION_START_DOWNLOAD_SERVICE"
		const val ACTION_STOP_DOWNLOAD_SERVICE = "ACTION_STOP_DOWNLOAD_SERVICE"
		const val ACTION_CANCEL_DOWNLOAD = "ACTION_CANCEL_DOWNLOAD"

		const val EXTRAS_KEY_DOWNLOAD_INFO = "key_download_info"

		private const val NOTIF_ID = 103

		@JvmStatic
		fun startNotification(context: Context, downloadInfo: String) {
			val list = arrayListOf(downloadInfo)
			startNotification(context, list)
		}

		@JvmStatic
		fun startNotification(context: Context, downloadInfoList: ArrayList<String>) {
			val intent = Intent(context, DownloadService::class.java)
			intent.action = ACTION_START_DOWNLOAD_SERVICE
			intent.putStringArrayListExtra(EXTRAS_KEY_DOWNLOAD_INFO, downloadInfoList)
			context.startService(intent)
		}
	}
}
