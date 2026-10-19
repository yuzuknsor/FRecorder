/*
 * Copyright 2018 Dmytro Ponomarenko
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 */

package com.vdo.frecorder.audio.player

import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import com.vdo.frecorder.AppConstants.PLAYBACK_VISUALIZATION_INTERVAL
import com.vdo.frecorder.exception.AppException
import com.vdo.frecorder.exception.PermissionDeniedException
import com.vdo.frecorder.exception.PlayerDataSourceException
import com.vdo.frecorder.exception.PlayerInitException
import timber.log.Timber
import java.io.IOException

/**
 * 老播放器（MediaPlayer 直连）。Kotlin 化（原 AudioPlayer.java）。
 * @deprecated 用 AudioPlayerNew
 */
@Deprecated("Use AudioPlayerNew")
class AudioPlayer private constructor() : PlayerContract.Player, MediaPlayer.OnPreparedListener {

	private val actionsListeners = ArrayList<PlayerContract.PlayerCallback>()

	private var mediaPlayer: MediaPlayer? = null
	private var isPrepared = false
	private var isPause = false
	private var seekPos = 0L
	private var pausePos = 0L
	private var dataSource: String? = null
	private val handler = Handler()

	override fun addPlayerCallback(callback: PlayerContract.PlayerCallback?) {
		if (callback != null) {
			actionsListeners.add(callback)
		}
	}

	override fun removePlayerCallback(callback: PlayerContract.PlayerCallback?): Boolean {
		if (callback != null) {
			return actionsListeners.remove(callback)
		}
		return false
	}

	override fun setData(data: String?) {
		if (mediaPlayer != null && dataSource != null && dataSource == data) {
			//同一个源，什么都不做
		} else {
			dataSource = data
			restartPlayer()
		}
	}

	private fun restartPlayer() {
		val src = dataSource ?: return
		try {
			isPrepared = false
			val player = MediaPlayer()
			mediaPlayer = player
			player.setDataSource(src)
			player.setAudioStreamType(AudioManager.STREAM_MUSIC)
		} catch (e: IOException) {
			handleSetupError(e)
		} catch (e: IllegalArgumentException) {
			handleSetupError(e)
		} catch (e: IllegalStateException) {
			handleSetupError(e)
		} catch (e: SecurityException) {
			handleSetupError(e)
		}
	}

	private fun handleSetupError(e: Exception) {
		Timber.e(e)
		val msg = e.message
		if (msg != null && msg.contains("Permission denied")) {
			onError(PermissionDeniedException())
		} else {
			onError(PlayerDataSourceException())
		}
	}

	override fun playOrPause() {
		try {
			val player = mediaPlayer ?: return
			if (player.isPlaying) {
				pause()
			} else {
				isPause = false
				if (!isPrepared) {
					try {
						player.setOnPreparedListener(this)
						player.prepareAsync()
					} catch (ex: IllegalStateException) {
						Timber.e(ex)
						restartPlayer()
						mediaPlayer?.let { mp ->
							mp.setOnPreparedListener(this)
							try {
								mp.prepareAsync()
							} catch (e: IllegalStateException) {
								Timber.e(e)
								restartPlayer()
							}
						}
					}
				} else {
					player.start()
					player.seekTo(pausePos.toInt())
					onStartPlay()
					player.setOnCompletionListener {
						stop()
						onStopPlay()
					}
					schedulePlaybackTimeUpdate()
				}
				pausePos = 0
			}
		} catch (e: IllegalStateException) {
			Timber.e(e, "Player is not initialized!")
		}
	}

	override fun onPrepared(mp: MediaPlayer) {
		val player = mediaPlayer
		if (player != null && player !== mp) {
			player.stop()
			player.release()
			mediaPlayer = mp
		}
		onPreparePlay()
		isPrepared = true
		mp.start()
		mp.seekTo(seekPos.toInt())
		onStartPlay()
		mp.setOnCompletionListener {
			stop()
			onStopPlay()
		}
		schedulePlaybackTimeUpdate()
	}

	override fun seek(mills: Long) {
		seekPos = mills
		if (isPause) {
			pausePos = mills
		}
		try {
			val player = mediaPlayer
			if (player != null && player.isPlaying) {
				player.seekTo(seekPos.toInt())
				onSeek(seekPos)
			}
		} catch (e: IllegalStateException) {
			Timber.e(e, "Player is not initialized!")
		}
	}

	override fun pause() {
		stopPlaybackTimeUpdate()
		val player = mediaPlayer ?: return
		if (player.isPlaying) {
			player.pause()
			onPausePlay()
			seekPos = player.currentPosition.toLong()
			isPause = true
			pausePos = seekPos
		}
	}

	override fun stop() {
		stopPlaybackTimeUpdate()
		val player = mediaPlayer
		if (player != null) {
			player.stop()
			player.setOnCompletionListener(null)
			isPrepared = false
			onStopPlay()
			player.currentPosition
			seekPos = 0
		}
		isPause = false
		pausePos = 0
	}

	override fun isPlaying(): Boolean {
		return try {
			val player = mediaPlayer
			player != null && player.isPlaying
		} catch (e: IllegalStateException) {
			Timber.e(e, "Player is not initialized!")
			false
		}
	}

	override fun isPause(): Boolean = isPause

	override fun getPauseTime(): Long = seekPos

	override fun release() {
		stop()
		mediaPlayer?.let {
			it.release()
			mediaPlayer = null
		}
		isPrepared = false
		isPause = false
		dataSource = null
		actionsListeners.clear()
	}

	private fun schedulePlaybackTimeUpdate() {
		handler.postDelayed({
			try {
				val player = mediaPlayer
				if (player != null && player.isPlaying) {
					onPlayProgress(player.currentPosition.toLong())
				}
				schedulePlaybackTimeUpdate()
			} catch (e: IllegalStateException) {
				Timber.e(e, "Player is not initialized!")
				onError(PlayerInitException())
			}
		}, PLAYBACK_VISUALIZATION_INTERVAL.toLong())
	}

	private fun stopPlaybackTimeUpdate() {
		handler.removeCallbacksAndMessages(null)
	}

	private fun onPreparePlay() {
		for (callback in actionsListeners) {
			callback.onPreparePlay()
		}
	}

	private fun onStartPlay() {
		for (callback in actionsListeners) {
			callback.onStartPlay()
		}
	}

	private fun onPlayProgress(mills: Long) {
		for (callback in actionsListeners) {
			callback.onPlayProgress(mills)
		}
	}

	private fun onStopPlay() {
		for (i in actionsListeners.indices.reversed()) {
			actionsListeners[i].onStopPlay()
		}
	}

	private fun onPausePlay() {
		for (callback in actionsListeners) {
			callback.onPausePlay()
		}
	}

	private fun onSeek(mills: Long) {
		for (callback in actionsListeners) {
			callback.onSeek(mills)
		}
	}

	private fun onError(throwable: AppException) {
		for (callback in actionsListeners) {
			callback.onError(throwable)
		}
	}

	companion object {
		private val singleton: AudioPlayer by lazy { AudioPlayer() }

		@JvmStatic
		fun getInstance(): AudioPlayer = singleton
	}
}
