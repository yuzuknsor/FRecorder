package com.vdo.frecorder

import android.os.Handler
import android.os.Looper
import android.os.Message
import timber.log.Timber
import java.util.concurrent.CountDownLatch

/** 串行后台任务队列（独立线程 + Looper）。Kotlin 化（原 BackgroundQueue.java）。 */
class BackgroundQueue(threadName: String) : Thread() {

	@Volatile
	private var handler: Handler? = null
	private val countDownLatch = CountDownLatch(1)

	init {
		name = threadName
		start()
	}

	fun postRunnable(runnable: Runnable) {
		postRunnable(runnable, 0)
	}

	fun postRunnable(runnable: Runnable, delay: Long) {
		try {
			countDownLatch.await()
			if (delay <= 0) {
				handler!!.post(runnable)
			} else {
				handler!!.postDelayed(runnable, delay)
			}
		} catch (e: Exception) {
			Timber.e(e)
		}
	}

	fun cancelRunnable(runnable: Runnable) {
		try {
			countDownLatch.await()
			handler!!.removeCallbacks(runnable)
		} catch (e: Exception) {
			Timber.e(e)
		}
	}

	fun cleanupQueue() {
		try {
			countDownLatch.await()
			handler!!.removeCallbacksAndMessages(null)
		} catch (e: Exception) {
			Timber.e(e)
		}
	}

	open fun handleMessage(inputMessage: Message) {
	}

	fun close() {
		handler!!.looper.quit()
	}

	override fun run() {
		Looper.prepare()
		handler = object : Handler() {
			override fun handleMessage(msg: Message) {
				this@BackgroundQueue.handleMessage(msg)
			}
		}
		countDownLatch.countDown()
		Looper.loop()
	}
}
