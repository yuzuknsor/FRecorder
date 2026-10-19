/*
 * Copyright 2018 Dmytro Ponomarenko
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

package com.vdo.frecorder.audio.recorder

import android.media.AudioDeviceInfo
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import com.vdo.frecorder.AppConstants
import com.vdo.frecorder.exception.InvalidOutputFile
import com.vdo.frecorder.exception.RecorderInitException
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * MediaRecorder 系录音器公共基类（Kotlin 化并合并 AudioRecorder/ThreeGpRecorder 的重复逻辑）。
 * 子类只决定输出格式与编码器。
 */
abstract class MediaRecorderBase protected constructor() : RecorderContract.Recorder {

	protected abstract val outputFormat: Int
	protected abstract val audioEncoder: Int

	protected var recorder: MediaRecorder? = null
	protected var recordFile: File? = null
	protected var updateTime = 0L
	protected var durationMills = 0L

	private val recording = AtomicBoolean(false)
	private val paused = AtomicBoolean(false)
	private val handler = Handler()

	private var recorderCallback: RecorderContract.RecorderCallback? = null

	override fun setRecorderCallback(callback: RecorderContract.RecorderCallback) {
		this.recorderCallback = callback
	}

	override fun startRecording(outputFile: String, channelCount: Int, sampleRate: Int, bitrate: Int) {
		startRecording(outputFile, channelCount, sampleRate, bitrate, null, AppConstants.GAIN_BOOST_OFF)
	}

	override fun startRecording(outputFile: String, channelCount: Int, sampleRate: Int, bitrate: Int, audioDevice: AudioDeviceInfo?) {
		startRecording(outputFile, channelCount, sampleRate, bitrate, audioDevice, AppConstants.GAIN_BOOST_OFF)
	}

	override fun startRecording(outputFile: String, channelCount: Int, sampleRate: Int, bitrate: Int, audioDevice: AudioDeviceInfo?, gainBoostLevel: Int) {
		// 注意：M4A 不支持增益提升（MediaRecorder 不暴露原始音频采样）
		val file = File(outputFile)
		if (file.exists() && file.isFile) {
			recordFile = file
			try {
				recorder = MediaRecorder().apply {
					setAudioSource(MediaRecorder.AudioSource.MIC)
					// USB 等指定输入设备
					if (audioDevice != null) {
						val deviceSet = setPreferredDevice(audioDevice)
						Timber.d("Set preferred audio device: %s, success: %b", audioDevice.productName, deviceSet)
					}
					configureRecorder(this, sampleRate)
					setAudioChannels(channelCount)
					setAudioSamplingRate(sampleRate)
					setAudioEncodingBitRate(bitrate)
					setMaxDuration(-1) //不限时长
					setOutputFile(file.absolutePath)
					prepare()
					start()
				}
				updateTime = System.currentTimeMillis()
				recording.set(true)
				scheduleRecordingTimeUpdate()
				recorderCallback?.onStartRecord(file)
				paused.set(false)
			} catch (e: IOException) {
				Timber.e(e, "prepare() failed")
				recorderCallback?.onError(RecorderInitException())
			} catch (e: IllegalStateException) {
				Timber.e(e, "prepare() failed")
				recorderCallback?.onError(RecorderInitException())
			}
		} else {
			recorderCallback?.onError(InvalidOutputFile())
		}
	}

	/** 子类定制输出格式/编码器/采样率相关的格式细节 */
	protected open fun configureRecorder(recorder: MediaRecorder, sampleRate: Int) {
		recorder.setOutputFormat(outputFormat)
		recorder.setAudioEncoder(audioEncoder)
	}

	override fun resumeRecording() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && paused.get()) {
			try {
				recorder?.resume()
				updateTime = System.currentTimeMillis()
				scheduleRecordingTimeUpdate()
				recorderCallback?.onResumeRecord()
				paused.set(false)
			} catch (e: IllegalStateException) {
				Timber.e(e, "unpauseRecording() failed")
				recorderCallback?.onError(RecorderInitException())
			}
		}
	}

	override fun pauseRecording() {
		if (recording.get()) {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
				if (!paused.get()) {
					try {
						recorder?.pause()
						durationMills += System.currentTimeMillis() - updateTime
						pauseRecordingTimer()
						recorderCallback?.onPauseRecord()
						paused.set(true)
					} catch (e: IllegalStateException) {
						Timber.e(e, "pauseRecording() failed")
						//TODO: 修异常类型
						recorderCallback?.onError(RecorderInitException())
					}
				}
			} else {
				stopRecording()
			}
		}
	}

	override fun stopRecording() {
		if (recording.get()) {
			stopRecordingTimer()
			val finished = recordFile
			try {
				recorder?.stop()
			} catch (e: RuntimeException) {
				Timber.e(e, "stopRecording() problems")
			}
			recorder?.release()
			if (finished != null) {
				recorderCallback?.onStopRecord(finished)
			}
			durationMills = 0
			recordFile = null
			recording.set(false)
			paused.set(false)
			recorder = null
		} else {
			Timber.e("Recording has already stopped or hasn't started")
		}
	}

	protected open fun scheduleRecordingTimeUpdate() {
		handler.postDelayed({
			val rec = recorder
			if (recorderCallback != null && rec != null) {
				try {
					val curTime = System.currentTimeMillis()
					if (updateTime > 0) {
						durationMills += curTime - updateTime
					}
					updateTime = curTime
					recorderCallback?.onRecordProgress(durationMills, rec.maxAmplitude)
				} catch (e: IllegalStateException) {
					Timber.e(e)
				}
				scheduleRecordingTimeUpdate()
			}
		}, AppConstants.RECORDING_VISUALIZATION_INTERVAL.toLong())
	}

	private fun stopRecordingTimer() {
		handler.removeCallbacksAndMessages(null)
		updateTime = 0
	}

	private fun pauseRecordingTimer() {
		handler.removeCallbacksAndMessages(null)
		updateTime = 0
	}

	override val isRecording: Boolean
		get() = recording.get()

	override val isPaused: Boolean
		get() = paused.get()
}
