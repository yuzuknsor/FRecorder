/*
 * Copyright 2020 Dmytro Ponomarenko
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.vdo.frecorder.app

import android.media.AudioDeviceInfo
import com.vdo.frecorder.ARApplication
import com.vdo.frecorder.AppConstants
import com.vdo.frecorder.AppConstants.PLAYBACK_VISUALIZATION_INTERVAL
import com.vdo.frecorder.BackgroundQueue
import com.vdo.frecorder.IntArrayList
import com.vdo.frecorder.app.info.RecordInfo
import com.vdo.frecorder.audio.AudioDecoder
import com.vdo.frecorder.audio.OutputFormatConverter
import com.vdo.frecorder.audio.recorder.RecorderContract
import com.vdo.frecorder.data.Prefs
import com.vdo.frecorder.data.RecordDataSource
import com.vdo.frecorder.data.database.LocalRepository
import com.vdo.frecorder.data.database.Record
import com.vdo.frecorder.exception.AppException
import com.vdo.frecorder.exception.RecordingException
import com.vdo.frecorder.util.AndroidUtils
import timber.log.Timber
import java.io.File
import java.util.Timer
import java.util.TimerTask
import kotlin.math.ceil
import kotlin.math.floor

/**
 * 录音编排层：驱动 Recorder、收集波形、停录后转换格式并落库。
 * Kotlin 化（原 AppRecorderImpl.java）。
 */
class AppRecorderImpl private constructor(
		recorder: RecorderContract.Recorder,
		private val localRepository: LocalRepository,
		private val recordingsTasks: BackgroundQueue,
		private val recordDataSource: RecordDataSource,
		private val prefs: Prefs
) : AppRecorder {

	private var audioRecorder: RecorderContract.Recorder = recorder
	private val appCallbacks = ArrayList<AppRecorderCallback>()
	private val recordingData = IntArrayList()
	private val apmpPool = IntArrayList()
	private var durationMills = 0L
	private var updateTime = 0L
	private var timerProgress: Timer? = null
	private var recordFilePath: String? = null

	private val recorderCallback: RecorderContract.RecorderCallback = object : RecorderContract.RecorderCallback {

		override fun onStartRecord(output: File) {
			durationMills = 0
			scheduleRecordingTimeUpdate()
			onRecordingStarted(output)
		}

		override fun onPauseRecord() {
			onRecordingPaused()
			pauseRecordingTimer()
		}

		override fun onResumeRecord() {
			scheduleRecordingTimeUpdate()
			onRecordingResumed()
		}

		override fun onRecordProgress(mills: Long, amp: Int) {
			apmpPool.add(amp)
		}

		override fun onStopRecord(output: File) {
			stopRecordingTimer()
			recordingsTasks.postRunnable {
				//需要时转输出格式（WAV → MP3/FLAC，或改位深）
				val outputFormat = prefs.getSettingOutputFormat()
				val bitDepth = prefs.getSettingBitDepth()
				var finalOutput = output

				if (outputFormat != AppConstants.OUTPUT_FORMAT_WAV || bitDepth != AppConstants.BIT_DEPTH_16) {
					OutputFormatConverter.convert(output, outputFormat, bitDepth,
							object : OutputFormatConverter.ConversionCallback {
								override fun onProgress(percent: Int) {}

								override fun onComplete(outputFile: File) {
									finalOutput = outputFile
								}

								override fun onError(message: String?) {
									Timber.e("Output format conversion failed: %s. Keeping WAV.", message)
									finalOutput = output
								}
							})
				}

				val resultFile = finalOutput
				val info = AudioDecoder.readRecordInfo(resultFile)
				var duration = info.duration
				if (duration <= 0) {
					duration = durationMills
				}
				durationMills = 0

				//格式以文件名后缀为准（MediaExtractor 可能报错 MIME，如刚转出的 FLAC 报 audio/raw）
				val fileName = resultFile.name
				val dotIndex = fileName.lastIndexOf('.')
				val fileFormat = if (dotIndex >= 0) {
					fileName.substring(dotIndex + 1).lowercase()
				} else {
					info.format
				}

				val waveForm = convertRecordingData(recordingData, (duration / 1000000f).toInt())
				val record = recordDataSource.recordingRecord
				if (record != null) {
					//格式变了就更新路径（如 .wav → .mp3）
					val update = Record(
							record.id,
							record.name,
							duration,
							record.created,
							record.added,
							record.removed,
							resultFile.absolutePath,
							fileFormat,
							info.size,
							info.sampleRate,
							info.channelCount,
							info.bitrate,
							record.isBookmarked,
							record.isWaveformProcessed,
							waveForm
					)
					if (localRepository.updateRecord(update)) {
						recordingData.clear()
						val rec = localRepository.getRecord(update.id)
						AndroidUtils.runOnUIThread { onRecordingStopped(resultFile, rec) }
					} else {
						//失败重试一次
						if (localRepository.updateRecord(update)) {
							recordingData.clear()
							val rec = localRepository.getRecord(update.id)
							AndroidUtils.runOnUIThread { onRecordingStopped(resultFile, rec) }
						} else {
							AndroidUtils.runOnUIThread { onRecordingStopped(resultFile, record) }
						}
					}
					recordDataSource.recordingRecord = null
				} else {
					AndroidUtils.runOnUIThread { onRecordingError(RecordingException()) }
				}
			}
		}

		override fun onError(throwable: AppException) {
			Timber.e(throwable)
			onRecordingError(throwable)
		}
	}

	init {
		audioRecorder.setRecorderCallback(recorderCallback)
	}

	private fun convertRecordingData(list: IntArrayList, durationSec: Int): IntArray {
		if (durationSec > AppConstants.LONG_RECORD_THRESHOLD_SECONDS) {
			val sampleCount = ARApplication.longWaveformSampleCount
			val waveForm = IntArray(sampleCount)
			val scale = list.size().toFloat() / sampleCount.toFloat()
			if (list.size() < sampleCount * 2) {
				for (i in 0 until sampleCount) {
					waveForm[i] = convertAmp(list.get(floor(i * scale).toInt()).toDouble())
				}
			} else {
				val step = ceil(scale).toInt()
				for (i in 0 until sampleCount) {
					var `val` = 0
					for (j in 0 until step) {
						`val` += list.get((i * scale + j).toInt())
					}
					`val` = (`val` / scale).toInt()
					waveForm[i] = convertAmp(`val`.toDouble())
				}
			}
			return waveForm
		} else {
			val waveForm = IntArray(list.size())
			for (i in 0 until list.size()) {
				waveForm[i] = convertAmp(list.get(i).toDouble())
			}
			return waveForm
		}
	}

	/** dB 振幅 → 视图振幅。 */
	private fun convertAmp(amp: Double): Int = (255 * (amp / 32767f)).toInt()

	override fun addRecordingCallback(callback: AppRecorderCallback?) {
		if (callback != null) appCallbacks.add(callback)
	}

	override fun removeRecordingCallback(callback: AppRecorderCallback?) {
		appCallbacks.remove(callback)
	}

	override fun setRecorder(recorder: RecorderContract.Recorder) {
		audioRecorder = recorder
		audioRecorder.setRecorderCallback(recorderCallback)
	}

	override fun startRecording(filePath: String, channelCount: Int, sampleRate: Int, bitrate: Int) {
		startRecording(filePath, channelCount, sampleRate, bitrate, null, AppConstants.GAIN_BOOST_OFF)
	}

	override fun startRecording(filePath: String, channelCount: Int, sampleRate: Int, bitrate: Int, audioDevice: AudioDeviceInfo?) {
		startRecording(filePath, channelCount, sampleRate, bitrate, audioDevice, AppConstants.GAIN_BOOST_OFF)
	}

	override fun startRecording(filePath: String, channelCount: Int, sampleRate: Int, bitrate: Int, audioDevice: AudioDeviceInfo?, gainBoostLevel: Int) {
		if (!audioRecorder.isRecording) {
			recordFilePath = filePath
			audioRecorder.startRecording(filePath, channelCount, sampleRate, bitrate, audioDevice, gainBoostLevel)
		}
	}

	override fun pauseRecording() {
		if (audioRecorder.isRecording) {
			audioRecorder.pauseRecording()
		}
	}

	override fun resumeRecording() {
		if (audioRecorder.isPaused) {
			audioRecorder.resumeRecording()
		}
	}

	override fun stopRecording() {
		if (audioRecorder.isRecording) {
			audioRecorder.stopRecording()
		}
	}

	override fun getRecordingData(): IntArrayList = recordingData

	override fun getRecordingDuration(): Long = durationMills

	override fun isRecording(): Boolean = audioRecorder.isRecording

	override fun isPaused(): Boolean = audioRecorder.isPaused

	override fun getRecordFile(): File? = recordFilePath?.let { File(it) }

	override fun release() {
		stopRecordingTimer()
		recordingData.clear()
		apmpPool.clear()
		audioRecorder.stopRecording()
		appCallbacks.clear()
	}

	private fun onRecordingStarted(output: File) {
		for (i in appCallbacks.indices.reversed()) {
			appCallbacks[i].onRecordingStarted(output)
		}
	}

	private fun onRecordingPaused() {
		for (i in appCallbacks.indices.reversed()) {
			appCallbacks[i].onRecordingPaused()
		}
	}

	private fun onRecordingResumed() {
		for (i in appCallbacks.indices.reversed()) {
			appCallbacks[i].onRecordingResumed()
		}
	}

	private fun onRecordingStopped(file: File, record: Record?) {
		for (i in appCallbacks.indices.reversed()) {
			appCallbacks[i].onRecordingStopped(file, record!!)
		}
	}

	private fun onRecordingProgress(mills: Long, amp: Int) {
		for (i in appCallbacks.indices.reversed()) {
			appCallbacks[i].onRecordingProgress(mills, amp)
		}
	}

	private fun onRecordingError(e: AppException) {
		for (i in appCallbacks.indices.reversed()) {
			appCallbacks[i].onError(e)
		}
	}

	private fun scheduleRecordingTimeUpdate() {
		updateTime = System.currentTimeMillis()
		timerProgress = Timer()
		timerProgress?.schedule(object : TimerTask() {
			override fun run() {
				try {
					readProgress()
				} catch (e: IllegalStateException) {
					Timber.e(e)
				}
				val curTime = System.currentTimeMillis()
				if (updateTime > 0) {
					durationMills += curTime - updateTime
				}
				updateTime = curTime
			}
		}, 0, PLAYBACK_VISUALIZATION_INTERVAL.toLong())
	}

	private fun readProgress() {
		if (apmpPool.size() > 0) {
			val amp = apmpPool.get(apmpPool.size() - 1)
			apmpPool.clear()
			apmpPool.add(amp)
			recordingData.add(amp)
			onRecordingProgress(durationMills, amp)
		}
	}

	private fun stopRecordingTimer() {
		timerProgress?.let {
			it.cancel()
			it.purge()
		}
		updateTime = 0
	}

	private fun pauseRecordingTimer() {
		timerProgress?.let {
			it.cancel()
			it.purge()
		}
		updateTime = 0
	}

	companion object {
		@Volatile
		private var instance: AppRecorderImpl? = null

		@JvmStatic
		fun getInstance(
				recorder: RecorderContract.Recorder,
				localRep: LocalRepository,
				tasks: BackgroundQueue,
				recordDataSource: RecordDataSource,
				prefs: Prefs
		): AppRecorderImpl {
			instance?.let { return it }
			synchronized(AppRecorderImpl::class.java) {
				instance?.let { return it }
				val created = AppRecorderImpl(recorder, localRep, tasks, recordDataSource, prefs)
				instance = created
				return created
			}
		}
	}
}
