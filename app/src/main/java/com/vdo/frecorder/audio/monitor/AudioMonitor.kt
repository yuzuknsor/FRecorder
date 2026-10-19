/*
 * Copyright 2024 Dmytro Ponomarenko
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 */

package com.vdo.frecorder.audio.monitor

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import com.vdo.frecorder.AppConstants
import com.vdo.frecorder.audio.AudioDeviceManager
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 录音时的实时监听：录音线程直接 feedAudio() 同步写 AudioTrack（已验证的 LoopbackLive 模式）。
 * 另支持独立监听模式（自开 AudioRecord，不录音时也能听）。
 * Kotlin 化（原 AudioMonitor.java）。
 */
class AudioMonitor private constructor() {

	private var audioTrack: AudioTrack? = null
	private var standaloneRecorder: AudioRecord? = null
	private var standaloneThread: Thread? = null

	private val _isMonitoring = AtomicBoolean(false)
	private val _isStandalone = AtomicBoolean(false)
	private val _isPaused = AtomicBoolean(false)
	private val feedCount = AtomicInteger(0)
	private val writeCount = AtomicInteger(0)

	private var sampleRate = 44100
	private var channelCount = 1
	private var inputDevice: AudioDeviceInfo? = null
	private var lastError: String? = null
	private var appContext: Context? = null

	//噪声门
	private var noiseGateEnabled = false
	private var gateState = GateState.CLOSED
	private var gateEnvelope = 0f
	private var holdCounter = 0L
	private var attackCoeff = 0f
	private var releaseCoeff = 0f
	private var holdSamples = 0L
	private var gateThreshold = 0
	private var gateHysteresis = 0

	//Biquad 滤波
	private var hpfMode = AppConstants.HPF_OFF
	private var lpfMode = AppConstants.LPF_OFF
	private var hpfX1 = 0.0
	private var hpfX2 = 0.0
	private var hpfY1 = 0.0
	private var hpfY2 = 0.0
	private var lpfX1 = 0.0
	private var lpfX2 = 0.0
	private var lpfY1 = 0.0
	private var lpfY2 = 0.0
	private var hpfCoeffs: DoubleArray? = null
	private var lpfCoeffs: DoubleArray? = null

	private var gainBoostLevel = AppConstants.GAIN_BOOST_OFF

	/** 用录音参数初始化。 */
	fun initialize(sampleRate: Int, channelCount: Int) {
		this.sampleRate = sampleRate
		this.channelCount = channelCount
		this.lastError = null
		initNoiseGate()
		initFilters()
		Timber.d("AudioMonitor.initialize: sampleRate=%d, channels=%d", sampleRate, channelCount)
	}

	private fun initFilters() {
		hpfX1 = 0.0; hpfX2 = 0.0; hpfY1 = 0.0; hpfY2 = 0.0
		lpfX1 = 0.0; lpfX2 = 0.0; lpfY1 = 0.0; lpfY2 = 0.0
		hpfCoeffs = computeHpfCoeffs()
		lpfCoeffs = computeLpfCoeffs()
	}

	fun setHpfMode(mode: Int) {
		hpfMode = mode
		initFilters()
	}

	fun setLpfMode(mode: Int) {
		lpfMode = mode
		initFilters()
	}

	fun getHpfMode(): Int = hpfMode

	fun getLpfMode(): Int = lpfMode

	fun setGainBoostLevel(level: Int) {
		gainBoostLevel = level
	}

	fun getGainBoostLevel(): Int = gainBoostLevel

	private val gainMultiplier: Float
		get() = when (gainBoostLevel) {
			AppConstants.GAIN_BOOST_6DB -> AppConstants.GAIN_BOOST_MULTIPLIER_6DB
			AppConstants.GAIN_BOOST_12DB -> AppConstants.GAIN_BOOST_MULTIPLIER_12DB
			else -> 1.0f
		}

	private fun applyGainBoost(pcmData: ByteArray, length: Int) {
		val multiplier = gainMultiplier
		if (multiplier <= 1.0f) return
		var i = 0
		while (i < length) {
			var sample = ((pcmData[i].toInt() and 0xFF) or (pcmData[i + 1].toInt() shl 8)).toShort()
			var amplified = (sample * multiplier).toInt()
			if (amplified > Short.MAX_VALUE) amplified = Short.MAX_VALUE.toInt()
			if (amplified < Short.MIN_VALUE) amplified = Short.MIN_VALUE.toInt()
			pcmData[i] = (amplified and 0xFF).toByte()
			pcmData[i + 1] = ((amplified shr 8) and 0xFF).toByte()
			i += 2
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

	private fun applyFilters(pcmData: ByteArray, length: Int) {
		var i = 0
		while (i < length) {
			var sample = ((pcmData[i].toInt() and 0xFF) or (pcmData[i + 1].toInt() shl 8)).toShort()

			hpfCoeffs?.let { c ->
				val x = sample.toDouble()
				val y = c[0] * x + c[1] * hpfX1 + c[2] * hpfX2 - c[3] * hpfY1 - c[4] * hpfY2
				hpfX2 = hpfX1; hpfX1 = x
				hpfY2 = hpfY1; hpfY1 = y
				var clamped = y.roundToInt()
				if (clamped > Short.MAX_VALUE) clamped = Short.MAX_VALUE.toInt()
				if (clamped < Short.MIN_VALUE) clamped = Short.MIN_VALUE.toInt()
				sample = clamped.toShort()
			}

			lpfCoeffs?.let { c ->
				val x = sample.toDouble()
				val y = c[0] * x + c[1] * lpfX1 + c[2] * lpfX2 - c[3] * lpfY1 - c[4] * lpfY2
				lpfX2 = lpfX1; lpfX1 = x
				lpfY2 = lpfY1; lpfY1 = y
				var clamped = y.roundToInt()
				if (clamped > Short.MAX_VALUE) clamped = Short.MAX_VALUE.toInt()
				if (clamped < Short.MIN_VALUE) clamped = Short.MIN_VALUE.toInt()
				sample = clamped.toShort()
			}

			pcmData[i] = (sample.toInt() and 0xFF).toByte()
			pcmData[i + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
			i += 2
		}
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
		//按 ~40ms 块近似
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

	fun setContext(context: Context) {
		appContext = context.applicationContext
	}

	/** 开监听播放：建 AudioTrack 并置为播放态，之后 feedAudio() 直接写 PCM。 */
	fun start() {
		if (_isMonitoring.get()) {
			Timber.w("AudioMonitor.start: already running")
			return
		}
		releaseAudioTrack()

		val channelConfig = if (channelCount == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
		val minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, AudioFormat.ENCODING_PCM_16BIT)
		if (minBufferSize <= 0) {
			lastError = "Invalid minBufferSize: $minBufferSize"
			Timber.e(lastError)
			return
		}

		try {
			val track = AudioTrack.Builder()
					.setAudioAttributes(AudioAttributes.Builder()
							.setUsage(AudioAttributes.USAGE_MEDIA)
							.setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
							.build())
					.setAudioFormat(AudioFormat.Builder()
							.setEncoding(AudioFormat.ENCODING_PCM_16BIT)
							.setSampleRate(sampleRate)
							.setChannelMask(channelConfig)
							.build())
					.setBufferSizeInBytes(minBufferSize)
					.setTransferMode(AudioTrack.MODE_STREAM)
					.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
					.build()
			audioTrack = track

			if (track.state != AudioTrack.STATE_INITIALIZED) {
				lastError = "AudioTrack not initialized, state=${track.state}"
				Timber.e(lastError)
				track.release()
				audioTrack = null
				return
			}

			//输出绕开 USB 设备（回灌会炸），优先蓝牙/有线/内置扬声器
			findNonUsbOutputDevice()?.let { outputDevice ->
				val set = track.setPreferredDevice(outputDevice)
				Timber.d("Set preferred output device: %s (type=%d), success=%b",
						outputDevice.productName, outputDevice.type, set)
			}

			track.play()
			if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
				lastError = "AudioTrack not playing, playState=${track.playState}"
				Timber.e(lastError)
				track.release()
				audioTrack = null
				return
			}

			feedCount.set(0)
			writeCount.set(0)
			_isMonitoring.set(true)
			_isPaused.set(false)

			Timber.d("AudioMonitor STARTED: sr=%d ch=%d minBuf=%d playState=%d",
					sampleRate, channelCount, minBufferSize, track.playState)
		} catch (e: Exception) {
			lastError = "start() exception: ${e.message}"
			Timber.e(e, "Failed to start AudioMonitor")
			releaseAudioTrack()
		}
	}

	fun stop() {
		_isMonitoring.set(false)
		_isPaused.set(false)
		releaseAudioTrack()
		Timber.d("AudioMonitor stopped. feeds=%d writes=%d", feedCount.get(), writeCount.get())
	}

	private fun releaseAudioTrack() {
		audioTrack?.let { track ->
			try {
				track.stop()
				track.flush()
				track.release()
			} catch (e: Exception) {
				Timber.e(e, "Error releasing AudioTrack")
			}
			audioTrack = null
		}
	}

	fun pause() {
		if (_isMonitoring.get() && !_isPaused.get()) {
			_isPaused.set(true)
			try {
				audioTrack?.pause()
			} catch (ignored: Exception) {
			}
		}
	}

	fun resume() {
		if (_isMonitoring.get() && _isPaused.get()) {
			_isPaused.set(false)
			try {
				audioTrack?.play()
			} catch (ignored: Exception) {
			}
		}
	}

	/**
	 * 喂音频：由录音线程直接调用，同步写 AudioTrack。
	 * AudioTrack.write 满缓冲会阻塞，天然按实时速率限流。
	 */
	fun feedAudio(pcmData: ByteArray?) {
		if (!_isMonitoring.get() || _isPaused.get() || pcmData == null) return
		val track = audioTrack ?: return

		//独立模式才做增益/滤波；录音模式 WavRecorder 已处理过
		if (_isStandalone.get()) {
			applyGainBoost(pcmData, pcmData.size)
			if (hpfCoeffs != null || lpfCoeffs != null) {
				applyFilters(pcmData, pcmData.size)
			}
		}
		if (noiseGateEnabled && _isStandalone.get()) {
			processNoiseGate(pcmData, pcmData.size)
		}

		feedCount.incrementAndGet()
		try {
			val written = track.write(pcmData, 0, pcmData.size, AudioTrack.WRITE_NON_BLOCKING)
			if (written > 0) {
				writeCount.incrementAndGet()
			} else if (written < 0) {
				Timber.e("AudioTrack.write error: %d", written)
			}
		} catch (e: Exception) {
			Timber.e(e, "Error writing to AudioTrack in feedAudio")
		}
	}

	/** 放 440Hz/0.5s 测试音，验证输出通路。 */
	fun playTestTone(): String {
		return try {
			val sr = 44100
			val channelCfg = AudioFormat.CHANNEL_OUT_MONO
			val minBuf = AudioTrack.getMinBufferSize(sr, channelCfg, AudioFormat.ENCODING_PCM_16BIT)
			if (minBuf <= 0) return "minBuf error: $minBuf"

			val durationMs = 500
			val numSamples = sr * durationMs / 1000
			val samples = ShortArray(numSamples)
			for (i in 0 until numSamples) {
				samples[i] = (Short.MAX_VALUE * sin(2 * PI * 440 * i / sr)).toInt().toShort()
			}

			//某些设备上 MODE_STATIC 会失败，故用 MODE_STREAM + 后台线程写
			val tone = AudioTrack.Builder()
					.setAudioAttributes(AudioAttributes.Builder()
							.setUsage(AudioAttributes.USAGE_MEDIA)
							.setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
							.build())
					.setAudioFormat(AudioFormat.Builder()
							.setEncoding(AudioFormat.ENCODING_PCM_16BIT)
							.setSampleRate(sr)
							.setChannelMask(channelCfg)
							.build())
					.setBufferSizeInBytes(minBuf)
					.setTransferMode(AudioTrack.MODE_STREAM)
					.build()

			if (tone.state != AudioTrack.STATE_INITIALIZED) {
				val err = "Tone init failed, state=${tone.state}"
				tone.release()
				return err
			}

			tone.play()
			Thread({
				tone.write(samples, 0, samples.size)
				try {
					Thread.sleep(100)
				} catch (ignored: InterruptedException) {
				}
				try {
					tone.stop(); tone.release()
				} catch (ignored: Exception) {
				}
			}).start()

			"Tone playing (state=${tone.playState}, sr=$sr, minBuf=$minBuf)"
		} catch (e: Exception) {
			"Tone error: ${e.message}"
		}
	}

	/** UI 调试用状态串。 */
	fun getDebugStatus(): String {
		return "mon=" + _isMonitoring.get() +
				" sr=" + sampleRate + " ch=" + channelCount +
				" track=" + (audioTrack?.let { "play=" + it.playState } ?: "null") +
				" f=" + feedCount.get() +
				" w=" + writeCount.get() +
				(lastError?.let { " ERR=$it" } ?: "")
	}

	fun setVolume(volume: Float) {
		val v = max(0f, min(1f, volume))
		audioTrack?.setStereoVolume(v, v)
	}

	val isMonitoring: Boolean
		get() = _isMonitoring.get()

	val isPaused: Boolean
		get() = _isPaused.get()

	/**
	 * 独立监听（不录音）：自开 AudioRecord 循环读，喂给 AudioTrack。
	 * 开始录音前应 stopStandalone()，录完再起。
	 */
	@SuppressLint("MissingPermission")
	fun startStandalone(sampleRate: Int, channelCount: Int, inputDevice: AudioDeviceInfo?) {
		if (_isMonitoring.get()) {
			Timber.w("AudioMonitor.startStandalone: already monitoring")
			return
		}

		this.inputDevice = inputDevice
		initialize(sampleRate, channelCount)
		start()
		if (!_isMonitoring.get()) {
			Timber.e("AudioMonitor.startStandalone: AudioTrack failed to start")
			return
		}

		val channelIn = if (channelCount == 1) AudioFormat.CHANNEL_IN_MONO else AudioFormat.CHANNEL_IN_STEREO
		val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelIn, AudioFormat.ENCODING_PCM_16BIT)
		if (bufferSize <= 0) {
			lastError = "Standalone AudioRecord minBuf error: $bufferSize"
			Timber.e(lastError)
			stop()
			return
		}

		try {
			val rec = AudioRecord(
					MediaRecorder.AudioSource.MIC,
					sampleRate, channelIn,
					AudioFormat.ENCODING_PCM_16BIT, bufferSize
			)
			standaloneRecorder = rec
			inputDevice?.let { rec.setPreferredDevice(it) }

			if (rec.state != AudioRecord.STATE_INITIALIZED) {
				lastError = "Standalone AudioRecord init failed"
				Timber.e(lastError)
				rec.release()
				standaloneRecorder = null
				stop()
				return
			}

			rec.startRecording()
			_isStandalone.set(true)

			standaloneThread = Thread({
				val buffer = ByteArray(bufferSize)
				while (_isStandalone.get() && _isMonitoring.get()) {
					if (!_isPaused.get()) {
						val read = rec.read(buffer, 0, bufferSize)
						if (read > 0) {
							feedAudio(buffer.copyOf())
						}
					} else {
						try {
							Thread.sleep(10)
						} catch (ignored: InterruptedException) {
						}
					}
				}
			}, "AudioMonitor-Standalone").also { it.start() }

			Timber.d("Standalone monitoring started: sr=%d ch=%d device=%s",
					sampleRate, channelCount,
					inputDevice?.productName ?: "default")
		} catch (e: Exception) {
			lastError = "Standalone start error: ${e.message}"
			Timber.e(e, lastError)
			releaseStandaloneRecorder()
			stop()
		}
	}

	/** 停独立监听。开录前必须先调。 */
	fun stopStandalone() {
		_isStandalone.set(false)
		standaloneThread?.let { t ->
			try {
				t.join(500)
			} catch (ignored: InterruptedException) {
			}
			standaloneThread = null
		}
		releaseStandaloneRecorder()
		stop()
		Timber.d("Standalone monitoring stopped")
	}

	private fun releaseStandaloneRecorder() {
		standaloneRecorder?.let { rec ->
			try {
				rec.stop()
				rec.release()
			} catch (e: Exception) {
				Timber.e(e, "Error releasing standalone AudioRecord")
			}
			standaloneRecorder = null
		}
	}

	val isStandalone: Boolean
		get() = _isStandalone.get()

	fun getInputDevice(): AudioDeviceInfo? = inputDevice

	fun release() {
		stopStandalone()
		stop()
	}

	/**
	 * 监听是否可能啸叫：内置麦 + 只有内置扬声器时才危险。
	 */
	fun hasFeedbackRisk(inputDevice: AudioDeviceInfo?): Boolean {
		val ctx = appContext ?: return false

		//指定了外接输入设备 → 麦克风收不到扬声器
		if (inputDevice != null && AudioDeviceManager.isExternalInputDevice(inputDevice)) return false

		val audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
		val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
		for (device in devices) {
			val type = device.type
			if (type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
					type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
					type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
					type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
					type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
					type == AudioDeviceInfo.TYPE_USB_DEVICE ||
					type == AudioDeviceInfo.TYPE_USB_HEADSET) {
				return false
			}
		}
		//只剩内置扬声器 + 内置麦 → 有啸叫风险
		return true
	}

	/** 找非 USB 输出设备：蓝牙 A2DP/BLE > 蓝牙 SCO > 有线 > 内置扬声器。 */
	private fun findNonUsbOutputDevice(): AudioDeviceInfo? {
		val ctx = appContext ?: return null
		val audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return null

		val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
		var bluetooth: AudioDeviceInfo? = null
		var wired: AudioDeviceInfo? = null
		var speaker: AudioDeviceInfo? = null

		for (device in devices) {
			val type = device.type
			if (type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || type == AudioDeviceInfo.TYPE_BLE_HEADSET) {
				bluetooth = device
			} else if (type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
				if (bluetooth == null) bluetooth = device
			} else if (type == AudioDeviceInfo.TYPE_WIRED_HEADSET || type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES) {
				wired = device
			} else if (type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
				speaker = device
			}
		}
		return bluetooth ?: wired ?: speaker
	}

	private enum class GateState { CLOSED, ATTACK, OPEN, HOLD, RELEASE }

	companion object {
		private val singleton: AudioMonitor by lazy { AudioMonitor() }

		@JvmStatic
		fun getInstance(): AudioMonitor = singleton

		private fun biquadHighPass(fc: Double, fs: Double, q: Double): DoubleArray {
			val w0 = 2.0 * PI * fc / fs
			val alpha = sin(w0) / (2.0 * q)
			val cosw0 = cos(w0)
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
			val cosw0 = cos(w0)
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
