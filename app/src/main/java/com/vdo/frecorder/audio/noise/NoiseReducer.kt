/*
 * Copyright 2024 Dimowner
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.vdo.frecorder.audio.noise

import timber.log.Timber
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 谱减法降噪，灵感来自 Audacity 的 NoiseReduction 效果。
 *
 * 算法：
 * 1. 取开头 N 秒作为噪声画像
 * 2. 逐频点统计（均值 + 幅度标准差）
 * 3. 全曲逐帧按「超出噪声门限的程度」算软增益掩码
 * 4. 时间平滑（attack/release）+ 可选频率平滑
 * 5. 频谱乘掩码 → 逆 FFT → overlap-add 重建
 * 6. 结果写回 WAV
 *
 * Kotlin 化（原 NoiseReducer.java）。
 */
object NoiseReducer {

	private const val TAG = "NoiseReducer"

	// FFT 帧长，必须 2 的幂
	private const val FFT_SIZE = 2048
	private const val HOP_SIZE = FFT_SIZE / 2 // 50% 重叠

	// 时间平滑常数
	private const val ATTACK_TIME_SEC = 0.02  // 20ms
	private const val RELEASE_TIME_SEC = 0.10 // 100ms

	// WAV 头大小
	private const val WAV_HEADER_SIZE = 44

	/** 降噪进度回调 */
	interface ProgressListener {
		fun onProgress(percent: Int)
	}

	/** 原地处理 WAV 文件的降噪。 */
	@JvmStatic
	fun process(wavFile: File, noiseProfileSeconds: Float, reductionDb: Float,
				sensitivity: Float, freqSmoothingBands: Int, listener: ProgressListener?): Boolean {
		return try {
			Timber.d("%s: Starting noise reduction on %s", TAG, wavFile.name)
			Timber.d("%s: params: reduction=%.1fdB sensitivity=%.1f freqSmooth=%d profileSec=%.1f",
					TAG, reductionDb, sensitivity, freqSmoothingBands, noiseProfileSeconds)

			val wavInfo = readWavHeader(wavFile)
			if (wavInfo == null) {
				Timber.e("%s: Failed to read WAV header", TAG)
				return false
			}
			Timber.d("%s: WAV: sr=%d ch=%d bps=%d dataSize=%d",
					TAG, wavInfo.sampleRate, wavInfo.channels, wavInfo.bitsPerSample, wavInfo.dataSize)

			if (wavInfo.bitsPerSample != 16) {
				Timber.e("%s: Only 16-bit PCM is supported, got %d", TAG, wavInfo.bitsPerSample)
				return false
			}

			//读全部样本（交错声道 → 处理用单声道混合）
			val samples = readSamples(wavFile, wavInfo)
			if (samples == null || samples.isEmpty()) {
				Timber.e("%s: Failed to read samples", TAG)
				return false
			}
			val totalSamples = samples.size
			Timber.d("%s: Read %d mono samples (%.2f seconds)",
					TAG, totalSamples, totalSamples.toDouble() / wavInfo.sampleRate)

			//步骤 1：从开头 N 秒建噪声画像
			val profileSamples = min((noiseProfileSeconds * wavInfo.sampleRate).toInt(), totalSamples)
			if (profileSamples < FFT_SIZE) {
				Timber.e("%s: Not enough samples for noise profile (%d < %d)", TAG, profileSamples, FFT_SIZE)
				return false
			}

			val numBins = FFT_SIZE / 2 + 1
			val noiseMean = DoubleArray(numBins)
			val noiseStd = DoubleArray(numBins)
			buildNoiseProfile(samples, profileSamples, noiseMean, noiseStd)

			//逐频点门限：sensitivity 越大门限越低 → 判为噪声的频点越多
			//sensitivity 0 → mean+3std（保守）；24 → mean+0std（激进）
			val sensitivityScale = (24.0 - sensitivity) / 24.0 * 3.0
			val threshold = DoubleArray(numBins)
			for (i in threshold.indices) {
				threshold[i] = noiseMean[i] + sensitivityScale * noiseStd[i]
			}

			//降噪强度：reductionDb 0 → 不减；24 → 减 2 倍门限
			val reductionStrength = reductionDb / 12.0 //6dB→0.5, 12dB→1.0, 24dB→2.0
			Timber.d("%s: sensitivityScale=%.2f reductionStrength=%.2f", TAG, sensitivityScale, reductionStrength)

			//时间平滑系数
			val hopDuration = HOP_SIZE.toDouble() / wavInfo.sampleRate
			val attackCoeff = exp(-hopDuration / ATTACK_TIME_SEC)
			val releaseCoeff = exp(-hopDuration / RELEASE_TIME_SEC)

			//步骤 2：处理全信号
			val window = HannWindow(FFT_SIZE)
			val output = DoubleArray(totalSamples)
			val windowSum = DoubleArray(totalSamples) //归一化用

			//上一帧增益掩码（时间平滑用）
			val prevGain = DoubleArray(numBins) { 1.0 }

			val numFrames = (totalSamples - FFT_SIZE) / HOP_SIZE + 1
			var frameCount = 0

			val re = DoubleArray(FFT_SIZE)
			val im = DoubleArray(FFT_SIZE)

			var pos = 0
			while (pos + FFT_SIZE <= totalSamples) {
				//加窗
				for (i in 0 until FFT_SIZE) {
					re[i] = samples[pos + i] * window.get(i)
					im[i] = 0.0
				}

				//正 FFT
				FFTHelper.forward(re, im)

				//幅度/相位与增益掩码
				val magnitude = DoubleArray(numBins)
				val phase = DoubleArray(numBins)
				var gainMask = DoubleArray(numBins)

				for (i in 0 until numBins) {
					magnitude[i] = sqrt(re[i] * re[i] + im[i] * im[i])
					phase[i] = atan2(im[i], re[i])

					//谱减法：从幅度里减去按比例缩放的噪声底
					val noiseEstimate = threshold[i] * reductionStrength
					var reduced = magnitude[i] - noiseEstimate
					if (reduced < 0) reduced = 0.0
					//增益 = 降噪后 / 原幅度
					gainMask[i] = if (magnitude[i] > 1e-10) reduced / magnitude[i] else 0.0
				}

				//频率平滑
				if (freqSmoothingBands > 0) {
					gainMask = smoothFrequency(gainMask, freqSmoothingBands)
				}

				//时间平滑
				for (i in 0 until numBins) {
					gainMask[i] = if (gainMask[i] < prevGain[i]) {
						//attack（增益下降 = 噪声门合上）
						attackCoeff * prevGain[i] + (1.0 - attackCoeff) * gainMask[i]
					} else {
						//release（增益上升 = 噪声门打开）
						releaseCoeff * prevGain[i] + (1.0 - releaseCoeff) * gainMask[i]
					}
					prevGain[i] = gainMask[i]
				}

				//频谱乘掩码
				for (i in 0 until numBins) {
					re[i] = magnitude[i] * gainMask[i] * cos(phase[i])
					im[i] = magnitude[i] * gainMask[i] * sin(phase[i])
				}
				//负频镜像
				for (i in 1 until FFT_SIZE / 2) {
					re[FFT_SIZE - i] = re[i]
					im[FFT_SIZE - i] = -im[i]
				}

				//逆 FFT
				FFTHelper.inverse(re, im)

				//overlap-add
				for (i in 0 until FFT_SIZE) {
					val idx = pos + i
					if (idx < totalSamples) {
						output[idx] += re[i] * window.get(i)
						windowSum[idx] += window.get(i) * window.get(i)
					}
				}

				frameCount++
				if (listener != null && frameCount % 50 == 0) {
					listener.onProgress((100.0 * frameCount / numFrames).toInt())
				}
				pos += HOP_SIZE
			}

			//按窗和归一化（防除零）
			for (i in 0 until totalSamples) {
				if (windowSum[i] > 1e-8) {
					output[i] /= windowSum[i]
				}
			}
			listener?.onProgress(95)

			//步骤 3：写回 WAV
			writeSamples(wavFile, wavInfo, output)
			listener?.onProgress(100)

			Timber.d("%s: Noise reduction complete, processed %d frames", TAG, frameCount)
			true
		} catch (e: Exception) {
			Timber.e(e, "%s: Noise reduction failed", TAG)
			false
		}
	}

	/** 用信号开头 profileSamples 个样本建噪声画像 */
	private fun buildNoiseProfile(samples: DoubleArray, profileSamples: Int, mean: DoubleArray, std: DoubleArray) {
		val window = HannWindow(FFT_SIZE)
		val numBins = FFT_SIZE / 2 + 1
		var frameCount = 0

		val sumMag = DoubleArray(numBins)
		val sumMagSq = DoubleArray(numBins)
		val re = DoubleArray(FFT_SIZE)
		val im = DoubleArray(FFT_SIZE)

		var pos = 0
		while (pos + FFT_SIZE <= profileSamples) {
			for (i in 0 until FFT_SIZE) {
				re[i] = samples[pos + i] * window.get(i)
				im[i] = 0.0
			}
			FFTHelper.forward(re, im)
			for (i in 0 until numBins) {
				val mag = sqrt(re[i] * re[i] + im[i] * im[i])
				sumMag[i] += mag
				sumMagSq[i] += mag * mag
			}
			frameCount++
			pos += HOP_SIZE
		}

		if (frameCount > 0) {
			for (i in 0 until numBins) {
				mean[i] = sumMag[i] / frameCount
				val variance = sumMagSq[i] / frameCount - mean[i] * mean[i]
				std[i] = sqrt(max(0.0, variance))
			}
		}
		Timber.d("%s: Built noise profile from %d frames", TAG, frameCount)
	}

	/** 掩码沿频率与相邻 band 平均平滑 */
	private fun smoothFrequency(mask: DoubleArray, bands: Int): DoubleArray {
		val smoothed = DoubleArray(mask.size)
		for (i in mask.indices) {
			var sum = 0.0
			var count = 0
			for (j in max(0, i - bands)..min(mask.size - 1, i + bands)) {
				sum += mask[j]
				count++
			}
			smoothed[i] = sum / count
		}
		return smoothed
	}

	private class WavInfo {
		var channels = 0
		var sampleRate = 0
		var bitsPerSample = 0
		var dataSize = 0
	}

	private fun readWavHeader(file: File): WavInfo? {
		RandomAccessFile(file, "r").use { raf ->
			if (raf.length() < WAV_HEADER_SIZE) return null
			val header = ByteArray(WAV_HEADER_SIZE)
			raf.readFully(header)
			val bb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)

			//校验 RIFF 与 WAVE
			if (header[0] != 'R'.code.toByte() || header[1] != 'I'.code.toByte()
					|| header[2] != 'F'.code.toByte() || header[3] != 'F'.code.toByte()) return null
			if (header[8] != 'W'.code.toByte() || header[9] != 'A'.code.toByte()
					|| header[10] != 'V'.code.toByte() || header[11] != 'E'.code.toByte()) return null

			val info = WavInfo()
			info.channels = bb.getShort(22).toInt() and 0xFFFF
			info.sampleRate = bb.getInt(24)
			info.bitsPerSample = bb.getShort(34).toInt() and 0xFFFF
			info.dataSize = bb.getInt(40)
			return info
		}
	}

	/** 读全部 PCM 样本，混合成 [-1,1] 的单声道 double */
	private fun readSamples(file: File, info: WavInfo): DoubleArray? {
		val bytesPerSample = info.bitsPerSample / 8
		val totalFrames = info.dataSize / (bytesPerSample * info.channels)
		val mono = DoubleArray(totalFrames)

		RandomAccessFile(file, "r").use { raf ->
			raf.seek(WAV_HEADER_SIZE.toLong())
			val buffer = ByteArray(min(info.dataSize, 65536))
			var sampleIdx = 0
			var remaining = info.dataSize

			while (remaining > 0 && sampleIdx < totalFrames) {
				val toRead = min(buffer.size, remaining)
				val read = raf.read(buffer, 0, toRead)
				if (read <= 0) break
				remaining -= read

				val bb = ByteBuffer.wrap(buffer, 0, read).order(ByteOrder.LITTLE_ENDIAN)
				val frameBytes = bytesPerSample * info.channels

				while (bb.remaining() >= frameBytes && sampleIdx < totalFrames) {
					var sum = 0.0
					for (ch in 0 until info.channels) {
						sum += bb.short / 32768.0
					}
					mono[sampleIdx++] = sum / info.channels
				}
			}
			return mono
		}
	}

	/** 处理完的单声道写回 WAV（多声道时同信号写入所有声道） */
	private fun writeSamples(file: File, info: WavInfo, mono: DoubleArray) {
		RandomAccessFile(file, "rw").use { raf ->
			raf.seek(WAV_HEADER_SIZE.toLong())

			val bytesPerFrame = (info.bitsPerSample / 8) * info.channels
			val buffer = ByteArray(min(65536, mono.size * bytesPerFrame))
			val bb = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN)

			var sampleIdx = 0
			while (sampleIdx < mono.size) {
				bb.clear()
				while (bb.remaining() >= bytesPerFrame && sampleIdx < mono.size) {
					//限幅 [-1,1] 转 16-bit
					val value = max(-1.0, min(1.0, mono[sampleIdx]))
					val s = (value * 32767.0).toInt().toShort()
					for (ch in 0 until info.channels) {
						bb.putShort(s)
					}
					sampleIdx++
				}
				raf.write(buffer, 0, bb.position())
			}
		}
	}
}
