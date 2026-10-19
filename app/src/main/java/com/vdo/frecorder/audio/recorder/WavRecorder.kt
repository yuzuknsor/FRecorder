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

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import androidx.annotation.RequiresPermission
import com.vdo.frecorder.AppConstants
import com.vdo.frecorder.audio.monitor.AudioMonitor
import com.vdo.frecorder.audio.noise.NoiseReducer
import com.vdo.frecorder.exception.InvalidOutputFile
import com.vdo.frecorder.exception.RecorderInitException
import com.vdo.frecorder.exception.RecordingException
import com.vdo.frecorder.util.AndroidUtils
import timber.log.Timber
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * WAV 直录（AudioRecord → PCM 写盘），带增益提升/HPF/LPF/噪声门/实时监听/录音后降噪。
 * 这是应用的主录音路径。Kotlin 化（原 WavRecorder.java）。
 */
class WavRecorder private constructor() : RecorderContract.Recorder {

	private var recorder: AudioRecord? = null

	private var recordFile: File? = null
	private var bufferSize = 0
	private var updateTime = 0L
	private var durationMills = 0L

	private var recordingThread: Thread? = null

	private val recording = AtomicBoolean(false)
	private val paused = AtomicBoolean(false)
	private val handler = Handler()

	private var channelCount = 1

	/** 录音可视化用的值 */
	private var lastVal = 0

	private var sampleRate = AppConstants.RECORD_SAMPLE_RATE_44100
	private var gainBoostLevel = AppConstants.GAIN_BOOST_OFF
	private var monitoringEnabled = false

	@Volatile
	private var noiseReductionEnabled = false

	@Volatile
	private var noiseReductionDb = AppConstants.DEFAULT_NOISE_REDUCTION_DB

	@Volatile
	private var noiseReductionSensitivity = AppConstants.DEFAULT_NOISE_REDUCTION_SENSITIVITY

	@Volatile
	private var noiseReductionFreqSmoothing = AppConstants.DEFAULT_NOISE_REDUCTION_FREQ_SMOOTHING

	@Volatile
	private var noiseProfileSeconds = AppConstants.DEFAULT_NOISE_PROFILE_SECONDS

	@Volatile
	private var hpfMode = AppConstants.HPF_OFF

	@Volatile
	private var lpfMode = AppConstants.LPF_OFF

	//噪声门状态（录音通路）
	private var noiseGateEnabled = false
	private var gateState = GateState.CLOSED
	private var gateEnvelope = 0f
	private var holdCounter = 0L
	private var attackCoeff = 0f
	private var releaseCoeff = 0f
	private var holdSamples = 0L
	private var gateThreshold = 0
	private var gateHysteresis = 0

	//Biquad 滤波器状态（交错单/立体声按单样本流处理，无需分声道）
	private var hpfX1 = 0.0
	private var hpfX2 = 0.0
	private var hpfY1 = 0.0
	private var hpfY2 = 0.0
	private var lpfX1 = 0.0
	private var lpfX2 = 0.0
	private var lpfY1 = 0.0
	private var lpfY2 = 0.0
	private var hpfCoeffs: DoubleArray? = null // {b0, b1, b2, a1, a2}
	private var lpfCoeffs: DoubleArray? = null

	private val audioMonitor = AudioMonitor.getInstance()
	private var noiseReductionListener: NoiseReductionListener? = null

	private var recorderCallback: RecorderContract.RecorderCallback? = null

	override fun setRecorderCallback(callback: RecorderContract.RecorderCallback) {
		recorderCallback = callback
	}

	@RequiresPermission("android.permission.RECORD_AUDIO")
	override fun startRecording(outputFile: String, channelCount: Int, sampleRate: Int, bitrate: Int) {
		startRecording(outputFile, channelCount, sampleRate, bitrate, null, AppConstants.GAIN_BOOST_OFF)
	}

	@RequiresPermission("android.permission.RECORD_AUDIO")
	override fun startRecording(outputFile: String, channelCount: Int, sampleRate: Int, bitrate: Int, audioDevice: AudioDeviceInfo?) {
		startRecording(outputFile, channelCount, sampleRate, bitrate, audioDevice, AppConstants.GAIN_BOOST_OFF)
	}

	@RequiresPermission("android.permission.RECORD_AUDIO")
	override fun startRecording(outputFile: String, channelCount: Int, sampleRate: Int, bitrate: Int, audioDevice: AudioDeviceInfo?, gainBoostLevel: Int) {
		this.sampleRate = sampleRate
		this.gainBoostLevel = gainBoostLevel
		this.channelCount = channelCount
		recordFile = File(outputFile)
		val file = recordFile
		if (file != null && file.exists() && file.isFile) {
			val channel = if (channelCount == 1) AudioFormat.CHANNEL_IN_MONO else AudioFormat.CHANNEL_IN_STEREO
			try {
				bufferSize = AudioRecord.getMinBufferSize(sampleRate, channel, AudioFormat.ENCODING_PCM_16BIT)
				if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
					bufferSize = AudioRecord.getMinBufferSize(sampleRate, channel, AudioFormat.ENCODING_PCM_16BIT)
				}
				recorder = AudioRecord(
						MediaRecorder.AudioSource.MIC,
						sampleRate,
						channel,
						AudioFormat.ENCODING_PCM_16BIT,
						bufferSize
				)
				//USB 等指定输入设备
				if (audioDevice != null) {
					val deviceSet = recorder!!.setPreferredDevice(audioDevice)
					Timber.d("Set preferred audio device: %s, success: %b", audioDevice.productName, deviceSet)
				}
			} catch (e: IllegalArgumentException) {
				Timber.e(e, "sampleRate = $sampleRate channel = $channel bufferSize = $bufferSize")
				recorder?.release()
			}
			val rec = recorder
			if (rec != null && rec.state == AudioRecord.STATE_INITIALIZED) {
				//录音前停掉独立监听，释放 AudioRecord
				if (audioMonitor.isStandalone) {
					audioMonitor.stopStandalone()
				}

				initFilters()
				rec.startRecording()
				updateTime = System.currentTimeMillis()
				recording.set(true)
				recordingThread = Thread({ writeAudioDataToFile() }, "AudioRecorder Thread")

				//开监听时由录音循环喂音频
				if (monitoringEnabled) {
					audioMonitor.initialize(sampleRate, channelCount)
					audioMonitor.start()
				}

				recordingThread!!.start()
				scheduleRecordingTimeUpdate()
				recorderCallback?.onStartRecord(file)
				paused.set(false)
			} else {
				Timber.e("prepare() failed")
				recorderCallback?.onError(RecorderInitException())
			}
		} else {
			recorderCallback?.onError(InvalidOutputFile())
		}
	}

	override fun resumeRecording() {
		val rec = recorder
		if (rec != null && rec.state == AudioRecord.STATE_INITIALIZED) {
			if (paused.get()) {
				//恢复录音前停独立监听（释放 AudioRecord）
				if (audioMonitor.isStandalone) {
					audioMonitor.stopStandalone()
				}
				updateTime = System.currentTimeMillis()
				scheduleRecordingTimeUpdate()
				rec.startRecording()

				//重启由录音喂的监听
				if (monitoringEnabled && !audioMonitor.isMonitoring) {
					audioMonitor.initialize(sampleRate, channelCount)
					audioMonitor.start()
				}
				recorderCallback?.onResumeRecord()
				paused.set(false)
			}
		}
	}

	override fun pauseRecording() {
		if (recording.get()) {
			recorder?.stop()
			durationMills += System.currentTimeMillis() - updateTime
			pauseRecordingTimer()

			//暂停时切独立监听，用户戴着耳机仍能听到
			if (monitoringEnabled && audioMonitor.isMonitoring) {
				audioMonitor.stop()
				audioMonitor.startStandalone(sampleRate, channelCount, null)
			}
			paused.set(true)
			recorderCallback?.onPauseRecord()
		}
	}

	override fun stopRecording() {
		val rec = recorder ?: return
		recording.set(false)
		paused.set(false)
		stopRecordingTimer()

		//停录音喂的监听；录音线程结束后会以独立模式重启
		if (audioMonitor.isMonitoring && !audioMonitor.isStandalone) {
			audioMonitor.stop()
		}

		if (rec.state == AudioRecord.STATE_INITIALIZED) {
			try {
				rec.stop()
			} catch (e: IllegalStateException) {
				Timber.e(e, "stopRecording() problems")
			}
		}
		durationMills = 0
		recordingThread!!.interrupt()

		//等录音线程写完 WAV 头
		try {
			recordingThread!!.join(5000)
		} catch (ignored: InterruptedException) {
		}

		rec.release()

		//AudioRecord 释放后以独立模式重启监听
		if (monitoringEnabled && !audioMonitor.isMonitoring) {
			audioMonitor.startStandalone(sampleRate, channelCount, null)
		}

		//按需降噪（同步；此时文件已完整）
		val file = recordFile
		if (noiseReductionEnabled && file != null && file.exists()) {
			Timber.d("Starting noise reduction on %s", file.name)
			noiseReductionListener?.let {
				AndroidUtils.runOnUIThread { it.onNoiseReductionStart() }
			}
			Thread({
				val success = NoiseReducer.process(
						file,
						noiseProfileSeconds,
						noiseReductionDb,
						noiseReductionSensitivity,
						noiseReductionFreqSmoothing,
						object : NoiseReducer.ProgressListener {
							override fun onProgress(percent: Int) {
								noiseReductionListener?.let {
									AndroidUtils.runOnUIThread { it.onNoiseReductionProgress(percent) }
								}
							}
						})
				Timber.d("Noise reduction %s for %s",
						if (success) "completed" else "failed", file.name)
				noiseReductionListener?.let {
					AndroidUtils.runOnUIThread { it.onNoiseReductionEnd(success) }
				}
				recorderCallback?.let {
					AndroidUtils.runOnUIThread { it.onStopRecord(file) }
				}
			}, "NoiseReduction").start()
		} else {
			file?.let { recorderCallback?.onStopRecord(it) }
		}
	}

	override val isRecording: Boolean
		get() = recording.get()

	override val isPaused: Boolean
		get() = paused.get()

	private fun writeAudioDataToFile() {
		val data = ByteArray(bufferSize)
		val file = recordFile
		val fos = try {
			FileOutputStream(file)
		} catch (e: FileNotFoundException) {
			Timber.e(e)
			null
		} ?: return

		fos.use { out ->
			writeEmptyHeader(out)
			//TODO: 暂停时停掉这个循环
			while (recording.get()) {
				if (paused.get()) {
					//暂停期间的监听由独立模式负责（pauseRecording 里已启动）
					continue
				}
				val rec = recorder ?: break
				var bytesRead = rec.read(data, 0, bufferSize)
				if (bytesRead <= 0) continue
				//保证偶数字节（16-bit 样本）
				bytesRead = bytesRead and 1.inv()
				if (bytesRead == AudioRecord.ERROR_INVALID_OPERATION) continue

				var sum = 0L
				val shortBuffer = java.nio.ByteBuffer.allocate(2)
				shortBuffer.order(ByteOrder.LITTLE_ENDIAN)
				var i = 0
				while (i < bytesRead) {
					shortBuffer.put(data[i])
					shortBuffer.put(data[i + 1])
					var sample = shortBuffer.getShort(0)

					//增益提升
					val multiplier = gainMultiplier
					if (multiplier > 1.0f) {
						var amplified = (sample * multiplier).toInt()
						if (amplified > Short.MAX_VALUE) amplified = Short.MAX_VALUE.toInt()
						if (amplified < Short.MIN_VALUE) amplified = Short.MIN_VALUE.toInt()
						sample = amplified.toShort()
					}

					//高通滤波
					hpfCoeffs?.let { c ->
						val x = sample.toDouble()
						val y = c[0] * x + c[1] * hpfX1 + c[2] * hpfX2 - c[3] * hpfY1 - c[4] * hpfY2
						hpfX2 = hpfX1; hpfX1 = x
						hpfY2 = hpfY1; hpfY1 = y
						var clamped = Math.round(y).toInt()
						if (clamped > Short.MAX_VALUE) clamped = Short.MAX_VALUE.toInt()
						if (clamped < Short.MIN_VALUE) clamped = Short.MIN_VALUE.toInt()
						sample = clamped.toShort()
					}

					//低通滤波
					lpfCoeffs?.let { c ->
						val x = sample.toDouble()
						val y = c[0] * x + c[1] * lpfX1 + c[2] * lpfX2 - c[3] * lpfY1 - c[4] * lpfY2
						lpfX2 = lpfX1; lpfX1 = x
						lpfY2 = lpfY1; lpfY1 = y
						var clamped = Math.round(y).toInt()
						if (clamped > Short.MAX_VALUE) clamped = Short.MAX_VALUE.toInt()
						if (clamped < Short.MIN_VALUE) clamped = Short.MIN_VALUE.toInt()
						sample = clamped.toShort()
					}

					//处理完写回
					data[i] = (sample.toInt() and 0xff).toByte()
					data[i + 1] = ((sample.toInt() shr 8) and 0xff).toByte()

					sum += abs(sample.toInt())
					shortBuffer.clear()
					i += 2
				}
				lastVal = (sum / max(1, bytesRead / 16)).toInt()

				//噪声门
				if (noiseGateEnabled) {
					processNoiseGate(data, bytesRead)
				}

				//开监听时喂音频（在增益提升之后）
				if (monitoringEnabled && audioMonitor.isMonitoring) {
					val monitorData = data.copyOf(bytesRead)
					audioMonitor.feedAudio(monitorData)
				}

				try {
					out.write(data, 0, bytesRead)
				} catch (e: IOException) {
					Timber.e(e)
					AndroidUtils.runOnUIThread {
						recorderCallback?.onError(RecordingException())
						stopRecording()
					}
				}
			}
		}
		//裁掉末尾 200ms，消除停止瞬态
		recordFile?.let { trimTail(it, channelCount) }
		recordFile?.let { setWaveFileHeader(it, channelCount) }
	}

	private fun trimTail(file: File, channels: Int) {
		//裁掉末尾 200ms 消除停止瞬态
		val bytesPerSample = channels * (RECORDER_BPP / 8)
		val tailBytes = (sampleRate * 0.2).toInt() * bytesPerSample
		val dataLen = file.length() - 44 //减 WAV 头
		if (dataLen > tailBytes) {
			try {
				randomAccessFile(file).use { raf ->
					raf.setLength(file.length() - tailBytes)
				}
			} catch (e: IOException) {
				Timber.e(e, "trimTail failed")
			}
		}
	}

	private fun setWaveFileHeader(file: File, channels: Int) {
		val fileSize = file.length() - 44
		val totalSize = fileSize + 36
		val byteRate = sampleRate.toLong() * channels * (RECORDER_BPP / 8) //单声道 1 样本 2 字节

		try {
			randomAccessFile(file).use { wavFile ->
				wavFile.seek(0)
				wavFile.write(generateHeader(fileSize, totalSize, sampleRate.toLong(), channels, byteRate))
			}
		} catch (e: FileNotFoundException) {
			Timber.e(e)
		} catch (e: IOException) {
			Timber.e(e)
		}
	}

	private fun writeEmptyHeader(fos: FileOutputStream) {
		try {
			fos.write(ByteArray(44))
			fos.flush()
		} catch (e: IOException) {
			Timber.e(e)
		}
	}

	private fun randomAccessFile(file: File): RandomAccessFile {
		return try {
			RandomAccessFile(file, "rw")
		} catch (e: FileNotFoundException) {
			throw RuntimeException(e)
		}
	}

	private fun generateHeader(totalAudioLen: Long, totalDataLen: Long, longSampleRate: Long, channels: Int, byteRate: Long): ByteArray {
		val header = ByteArray(44)

		header[0] = 'R'.code.toByte() //RIFF/WAVE header
		header[1] = 'I'.code.toByte()
		header[2] = 'F'.code.toByte()
		header[3] = 'F'.code.toByte()
		header[4] = (totalDataLen and 0xff).toByte()
		header[5] = ((totalDataLen shr 8) and 0xff).toByte()
		header[6] = ((totalDataLen shr 16) and 0xff).toByte()
		header[7] = ((totalDataLen shr 24) and 0xff).toByte()
		header[8] = 'W'.code.toByte()
		header[9] = 'A'.code.toByte()
		header[10] = 'V'.code.toByte()
		header[11] = 'E'.code.toByte()
		header[12] = 'f'.code.toByte() //'fmt ' chunk
		header[13] = 'm'.code.toByte()
		header[14] = 't'.code.toByte()
		header[15] = ' '.code.toByte()
		header[16] = 16 //PCM 的 fmt 块大小
		header[17] = 0
		header[18] = 0
		header[19] = 0
		header[20] = 1 //format = 1
		header[21] = 0
		header[22] = channels.toByte()
		header[23] = 0
		header[24] = (longSampleRate and 0xff).toByte()
		header[25] = ((longSampleRate shr 8) and 0xff).toByte()
		header[26] = ((longSampleRate shr 16) and 0xff).toByte()
		header[27] = ((longSampleRate shr 24) and 0xff).toByte()
		header[28] = (byteRate and 0xff).toByte()
		header[29] = ((byteRate shr 8) and 0xff).toByte()
		header[30] = ((byteRate shr 16) and 0xff).toByte()
		header[31] = ((byteRate shr 24) and 0xff).toByte()
		header[32] = (channels * (RECORDER_BPP / 8)).toByte() //block align
		header[33] = 0
		header[34] = RECORDER_BPP.toByte() //bits per sample
		header[35] = 0
		header[36] = 'd'.code.toByte()
		header[37] = 'a'.code.toByte()
		header[38] = 't'.code.toByte()
		header[39] = 'a'.code.toByte()
		header[40] = (totalAudioLen and 0xff).toByte()
		header[41] = ((totalAudioLen shr 8) and 0xff).toByte()
		header[42] = ((totalAudioLen shr 16) and 0xff).toByte()
		header[43] = ((totalAudioLen shr 24) and 0xff).toByte()
		return header
	}

	private fun scheduleRecordingTimeUpdate() {
		handler.postDelayed({
			if (recorderCallback != null && recorder != null) {
				val curTime = System.currentTimeMillis()
				if (updateTime > 0) {
					durationMills += curTime - updateTime
				}
				updateTime = curTime
				recorderCallback?.onRecordProgress(durationMills, lastVal)
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

	fun setGainBoostLevel(level: Int) {
		gainBoostLevel = level
	}

	private val gainMultiplier: Float
		get() = when (gainBoostLevel) {
			AppConstants.GAIN_BOOST_6DB -> AppConstants.GAIN_BOOST_MULTIPLIER_6DB
			AppConstants.GAIN_BOOST_12DB -> AppConstants.GAIN_BOOST_MULTIPLIER_12DB
			else -> 1.0f
		}

	/**
	 * 开关实时监听。开着时录音经耳机实时回放。
	 * 应在 startRecording() 之前调用。
	 */
	fun setMonitoringEnabled(enabled: Boolean) {
		monitoringEnabled = enabled
		if (enabled && recording.get() && !paused.get() && !audioMonitor.isMonitoring) {
			audioMonitor.initialize(sampleRate, channelCount)
			audioMonitor.start()
		} else if (!enabled && audioMonitor.isMonitoring) {
			audioMonitor.stop()
		}
	}

	fun isMonitoringEnabled(): Boolean = monitoringEnabled

	fun setNoiseReductionEnabled(enabled: Boolean) {
		noiseReductionEnabled = enabled
	}

	fun isNoiseReductionEnabled(): Boolean = noiseReductionEnabled

	fun setNoiseReductionDb(db: Float) {
		noiseReductionDb = db
	}

	fun setNoiseReductionSensitivity(sensitivity: Float) {
		noiseReductionSensitivity = sensitivity
	}

	fun setNoiseReductionFreqSmoothing(bands: Int) {
		noiseReductionFreqSmoothing = bands
	}

	fun setNoiseProfileSeconds(seconds: Float) {
		noiseProfileSeconds = seconds
	}

	fun setHpfMode(mode: Int) {
		hpfMode = mode
	}

	fun getHpfMode(): Int = hpfMode

	fun setLpfMode(mode: Int) {
		lpfMode = mode
	}

	fun getLpfMode(): Int = lpfMode

	private fun initFilters() {
		hpfX1 = 0.0; hpfX2 = 0.0; hpfY1 = 0.0; hpfY2 = 0.0
		lpfX1 = 0.0; lpfX2 = 0.0; lpfY1 = 0.0; lpfY2 = 0.0
		hpfCoeffs = computeHpfCoeffs()
		lpfCoeffs = computeLpfCoeffs()
		initNoiseGate()
	}

	private fun initNoiseGate() {
		gateState = GateState.CLOSED
		gateEnvelope = 0f
		holdCounter = 0
		gateThreshold = AppConstants.NOISE_GATE_THRESHOLD_RMS
		gateHysteresis = (gateThreshold * AppConstants.NOISE_GATE_HYSTERESIS).toInt()
		val attackMs = AppConstants.NOISE_GATE_ATTACK_MS
		val releaseMs = AppConstants.NOISE_GATE_RELEASE_MS
		val holdMs = AppConstants.NOISE_GATE_HOLD_MS
		attackCoeff = 1000.0f / (sampleRate * attackMs)
		releaseCoeff = 1000.0f / (sampleRate * releaseMs)
		holdSamples = (sampleRate * holdMs / 1000.0f).toLong()
	}

	fun setNoiseGateEnabled(enabled: Boolean) {
		noiseGateEnabled = enabled
		if (!enabled) {
			gateState = GateState.OPEN
			gateEnvelope = 1.0f
		}
	}

	fun isNoiseGateEnabled(): Boolean = noiseGateEnabled

	private fun processNoiseGate(pcmData: ByteArray, length: Int) {
		var sumSquares = 0L
		val sampleCount = length / 2
		var i = 0
		while (i < length) {
			val sample = ((pcmData[i].toInt() and 0xFF) or (pcmData[i + 1].toInt() shl 8)).toShort()
			sumSquares += sample.toLong() * sample
			i += 2
		}
		val rms = sqrt(sumSquares.toDouble() / sampleCount).toFloat()

		when (gateState) {
			GateState.CLOSED -> if (rms > gateThreshold) gateState = GateState.ATTACK
			GateState.ATTACK -> {
				gateEnvelope += attackCoeff * sampleCount
				if (gateEnvelope >= 1.0f) {
					gateEnvelope = 1.0f
					gateState = GateState.OPEN
				}
			}
			GateState.OPEN -> if (rms < gateHysteresis) {
				holdCounter = holdSamples
				gateState = GateState.HOLD
			}
			GateState.HOLD -> {
				holdCounter -= sampleCount
				if (holdCounter <= 0) gateState = GateState.RELEASE
				if (rms > gateThreshold) gateState = GateState.OPEN
			}
			GateState.RELEASE -> {
				gateEnvelope -= releaseCoeff * sampleCount
				if (gateEnvelope <= 0f) {
					gateEnvelope = 0f
					gateState = GateState.CLOSED
				}
				if (rms > gateThreshold) gateState = GateState.ATTACK
			}
		}

		if (gateEnvelope < 1.0f) {
			i = 0
			while (i < length) {
				val sample = (((pcmData[i].toInt() and 0xFF) or (pcmData[i + 1].toInt() shl 8)).toShort() * gateEnvelope).toInt().toShort()
				pcmData[i] = (sample.toInt() and 0xFF).toByte()
				pcmData[i + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
				i += 2
			}
		}
	}

	private fun computeHpfCoeffs(): DoubleArray? {
		val freq = when (hpfMode) {
			AppConstants.HPF_80 -> AppConstants.HPF_FREQ_80
			AppConstants.HPF_120 -> AppConstants.HPF_FREQ_120
			else -> return null
		}
		return biquadHighPass(freq.toDouble(), sampleRate.toDouble(), 0.7071)
	}

	private fun computeLpfCoeffs(): DoubleArray? {
		val freq = when (lpfMode) {
			AppConstants.LPF_9500 -> AppConstants.LPF_FREQ_9500
			AppConstants.LPF_15000 -> AppConstants.LPF_FREQ_15000
			else -> return null
		}
		return biquadLowPass(freq.toDouble(), sampleRate.toDouble(), 0.7071)
	}

	fun setNoiseReductionListener(listener: NoiseReductionListener?) {
		noiseReductionListener = listener
	}

	interface NoiseReductionListener {
		fun onNoiseReductionStart()
		fun onNoiseReductionProgress(percent: Int)
		fun onNoiseReductionEnd(success: Boolean)
	}

	private enum class GateState { CLOSED, ATTACK, OPEN, HOLD, RELEASE }

	companion object {
		private const val RECORDER_BPP = 16 //bits per sample

		@Volatile
		private var singleton: WavRecorder? = null

		@JvmStatic
		fun getInstance(): WavRecorder {
			return singleton ?: synchronized(this) {
				singleton ?: WavRecorder().also { singleton = it }
			}
		}

		private fun biquadHighPass(fc: Double, fs: Double, q: Double): DoubleArray {
			val w0 = 2.0 * PI * fc / fs
			val alpha = sin(w0) / (2.0 * q)
			val cosw0 = kotlin.math.cos(w0)
			val b0 = (1.0 + cosw0) / 2.0
			val b1 = -(1.0 + cosw0)
			val b2 = (1.0 + cosw0) / 2.0
			val a0 = 1.0 + alpha
			val a1 = -2.0 * cosw0
			val a2 = 1.0 - alpha
			return doubleArrayOf(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
		}

		private fun biquadLowPass(fc: Double, fs: Double, q: Double): DoubleArray {
			val w0 = 2.0 * PI * fc / fs
			val alpha = sin(w0) / (2.0 * q)
			val cosw0 = kotlin.math.cos(w0)
			val b0 = (1.0 - cosw0) / 2.0
			val b1 = 1.0 - cosw0
			val b2 = (1.0 - cosw0) / 2.0
			val a0 = 1.0 + alpha
			val a1 = -2.0 * cosw0
			val a2 = 1.0 - alpha
			return doubleArrayOf(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
		}
	}
}
