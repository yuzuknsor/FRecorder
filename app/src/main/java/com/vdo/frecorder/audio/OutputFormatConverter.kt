/*
 * Copyright 2024 Dmytro Ponomarenko
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

package com.vdo.frecorder.audio

import android.media.MediaCodec
import android.media.MediaFormat
import com.naman14.androidlame.AndroidLame
import com.naman14.androidlame.LameBuilder
import com.vdo.frecorder.AppConstants
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * WAV 转其他格式（MP3/FLAC/24-bit WAV）。
 * 内部始终先录 WAV；本转换器在录音结束后运行。Kotlin 化（原 OutputFormatConverter.java）。
 */
object OutputFormatConverter {

	interface ConversionCallback {
		fun onProgress(percent: Int)
		fun onComplete(outputFile: File)
		fun onError(message: String?)
	}

	/**
	 * WAV 转指定输出格式。
	 * @param outputFormat AppConstants.OUTPUT_FORMAT_WAV / MP3 / FLAC 之一
	 * @param bitDepth WAV 输出的位深（16/24）；MP3/FLAC 忽略
	 * @return 输出文件（WAV 同位深时可能与输入相同）
	 */
	@JvmStatic
	fun convert(wavFile: File?, outputFormat: String, bitDepth: Int, callback: ConversionCallback?) {
		if (wavFile == null || !wavFile.exists()) {
			callback?.onError("Source WAV file not found")
			return
		}
		when (outputFormat) {
			AppConstants.OUTPUT_FORMAT_MP3 -> convertToMp3(wavFile, callback)
			AppConstants.OUTPUT_FORMAT_FLAC -> convertToFlac(wavFile, callback)
			else -> handleWavBitDepth(wavFile, bitDepth, callback)
		}
	}

	private fun handleWavBitDepth(wavFile: File, bitDepth: Int, callback: ConversionCallback?) {
		//16 位是默认，WAV 本身就是 16-bit PCM，无需转换
		if (bitDepth == AppConstants.BIT_DEPTH_16) {
			callback?.onComplete(wavFile)
			return
		}
		try {
			val header = readWavHeader(wavFile)
			if (header == null) {
				callback?.onError("Failed to read WAV header")
				return
			}
			val bytesPerSampleIn = header.bitsPerSample / 8
			val bytesPerSampleOut = bitDepth / 8
			val numSamples = (header.dataSize / bytesPerSampleIn).toInt()

			val tempFile = File(wavFile.parent, wavFile.name + ".tmp")
			FileInputStream(wavFile).use { fis ->
				FileOutputStream(tempFile).use { fos ->
					fis.skip(44)
					//占位头，最后回写
					fos.write(ByteArray(44))

					val inBuffer = ByteArray(bytesPerSampleIn * header.channels * 1024)
					val outBuffer = ByteArray(bytesPerSampleOut * header.channels * 1024)
					var samplesProcessed = 0
					while (true) {
						val bytesRead = fis.read(inBuffer)
						if (bytesRead <= 0) break
						val samplesInChunk = bytesRead / bytesPerSampleIn
						for (i in 0 until samplesInChunk) {
							//读 16-bit 样本（小端）
							val sample = (((inBuffer[i * 2 + 1].toInt() and 0xFF) shl 8) or (inBuffer[i * 2].toInt() and 0xFF)).toShort()
							//16-bit → 24-bit 左移 8 位
							val sample24 = sample.toInt() shl 8
							outBuffer[i * 3] = (sample24 and 0xFF).toByte()
							outBuffer[i * 3 + 1] = ((sample24 shr 8) and 0xFF).toByte()
							outBuffer[i * 3 + 2] = ((sample24 shr 16) and 0xFF).toByte()
						}
						fos.write(outBuffer, 0, samplesInChunk * bytesPerSampleOut)
						samplesProcessed += samplesInChunk
						if (numSamples > 0) {
							callback?.onProgress((100L * samplesProcessed / numSamples).toInt())
						}
					}
				}
			}

			//回写正确的头
			val dataSize = tempFile.length() - 44
			writeWavHeader(tempFile, header.sampleRate, header.channels, bitDepth, dataSize)

			//替换原文件
			if (wavFile.delete() && tempFile.renameTo(wavFile)) {
				callback?.onComplete(wavFile)
			} else {
				callback?.onError("Failed to replace WAV file")
			}
		} catch (e: IOException) {
			Timber.e(e, "WAV bit depth conversion failed")
			callback?.onError("WAV conversion failed: " + e.message)
		}
	}

	private fun convertToMp3(wavFile: File, callback: ConversionCallback?) {
		try {
			val header = readWavHeader(wavFile)
			if (header == null) {
				callback?.onError("Failed to read WAV header")
				return
			}
			val basePath = stripAudioExtension(wavFile.absolutePath)
			val mp3File = File("$basePath.mp3")
			val sameFile = wavFile.absolutePath == mp3File.absolutePath
			val outputFile = if (sameFile) File("$basePath.mp3.tmp") else mp3File

			val lame = LameBuilder()
					.setInSampleRate(header.sampleRate)
					.setOutChannels(header.channels)
					.setOutBitrate(320)
					.setOutSampleRate(header.sampleRate)
					.setQuality(2) //高质量
					.build()

			FileInputStream(wavFile).use { fis ->
				FileOutputStream(outputFile).use { fos ->
					fis.skip(44) //跳过 WAV 头

					val bufferSize = 1024 * header.channels
					val wavBuffer = ByteArray(bufferSize * 2) //16-bit 每样本 2 字节
					val mp3Buffer = ByteArray((7200 + bufferSize * 1.25).toInt())

					val totalBytes = header.dataSize
					var processedBytes = 0L
					while (true) {
						val bytesRead = fis.read(wavBuffer)
						if (bytesRead <= 0) break
						val samplesRead = bytesRead / 2

						val pcm = ShortArray(samplesRead)
						ByteBuffer.wrap(wavBuffer, 0, bytesRead).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(pcm)

						val encodedBytes: Int
						if (header.channels == 1) {
							encodedBytes = lame.encode(pcm, pcm, samplesRead, mp3Buffer)
						} else {
							//立体声交错采样拆左右声道
							val framesRead = samplesRead / 2
							val left = ShortArray(framesRead)
							val right = ShortArray(framesRead)
							for (i in 0 until framesRead) {
								left[i] = pcm[i * 2]
								right[i] = pcm[i * 2 + 1]
							}
							encodedBytes = lame.encode(left, right, framesRead, mp3Buffer)
						}
						if (encodedBytes > 0) {
							fos.write(mp3Buffer, 0, encodedBytes)
						}
						processedBytes += bytesRead
						if (totalBytes > 0) {
							callback?.onProgress((100L * processedBytes / totalBytes).toInt())
						}
					}

					val flushBytes = lame.flush(mp3Buffer)
					if (flushBytes > 0) {
						fos.write(mp3Buffer, 0, flushBytes)
					}
				}
			}
			lame.close()

			if (sameFile) {
				wavFile.delete()
				outputFile.renameTo(mp3File)
			} else {
				wavFile.delete()
			}
			callback?.onComplete(mp3File)
		} catch (e: Exception) {
			Timber.e(e, "MP3 conversion failed")
			callback?.onError("MP3 conversion failed: " + e.message)
		}
	}

	private fun convertToFlac(wavFile: File, callback: ConversionCallback?) {
		try {
			val header = readWavHeader(wavFile)
			if (header == null) {
				callback?.onError("Failed to read WAV header")
				return
			}
			val basePath = stripAudioExtension(wavFile.absolutePath)
			val flacFile = File("$basePath.flac")
			val sameFile = wavFile.absolutePath == flacFile.absolutePath
			val outputFile = if (sameFile) File("$basePath.flac.tmp") else flacFile

			val format = MediaFormat.createAudioFormat(
					MediaFormat.MIMETYPE_AUDIO_FLAC,
					header.sampleRate,
					header.channels)
			format.setInteger(MediaFormat.KEY_FLAC_COMPRESSION_LEVEL, 5)
			format.setInteger(MediaFormat.KEY_PCM_ENCODING, android.media.AudioFormat.ENCODING_PCM_16BIT)

			val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_FLAC)
			codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
			codec.start()

			FileInputStream(wavFile).use { fis ->
				FileOutputStream(outputFile).use { fos ->
					fis.skip(44) //跳过 WAV 头

					val bufferSize = 4096 * header.channels
					val readBuffer = ByteArray(bufferSize)
					var inputDone = false
					var outputDone = false

					val totalBytes = header.dataSize
					var processedBytes = 0L
					var presentationTimeUs = 0L

					while (!outputDone) {
						if (!inputDone) {
							val inputBufferIndex = codec.dequeueInputBuffer(10000)
							if (inputBufferIndex >= 0) {
								val inputBuffer = codec.getInputBuffer(inputBufferIndex)!!
								val bytesRead = fis.read(readBuffer)
								if (bytesRead <= 0) {
									codec.queueInputBuffer(inputBufferIndex, 0, 0, 0,
											MediaCodec.BUFFER_FLAG_END_OF_STREAM)
									inputDone = true
								} else {
									inputBuffer.clear()
									inputBuffer.put(readBuffer, 0, bytesRead)
									val samplesInBuffer = bytesRead / (2 * header.channels)
									codec.queueInputBuffer(inputBufferIndex, 0, bytesRead, presentationTimeUs, 0)
									presentationTimeUs += 1_000_000L * samplesInBuffer / header.sampleRate
									processedBytes += bytesRead
									if (totalBytes > 0) {
										callback?.onProgress((100L * processedBytes / totalBytes).toInt())
									}
								}
							}
						}

						val info = MediaCodec.BufferInfo()
						val outputBufferIndex = codec.dequeueOutputBuffer(info, 10000)
						if (outputBufferIndex >= 0) {
							val outputBuffer = codec.getOutputBuffer(outputBufferIndex)
							if (info.size > 0 && outputBuffer != null) {
								val encoded = ByteArray(info.size)
								outputBuffer.get(encoded)
								fos.write(encoded)
							}
							codec.releaseOutputBuffer(outputBufferIndex, false)
							if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
								outputDone = true
							}
						}
					}
				}
			}
			codec.stop()
			codec.release()

			if (sameFile) {
				wavFile.delete()
				outputFile.renameTo(flacFile)
			} else {
				wavFile.delete()
			}
			callback?.onComplete(flacFile)
		} catch (e: Exception) {
			Timber.e(e, "FLAC conversion failed")
			callback?.onError("FLAC conversion failed: " + e.message)
		}
	}

	private class WavHeader {
		var channels = 0
		var sampleRate = 0
		var bitsPerSample = 0
		var dataSize = 0L
	}

	private fun readWavHeader(wavFile: File): WavHeader? {
		return try {
			RandomAccessFile(wavFile, "r").use { raf ->
				val header = ByteArray(44)
				raf.read(header)
				//校验 RIFF
				if (header[0] != 'R'.code.toByte() || header[1] != 'I'.code.toByte()
						|| header[2] != 'F'.code.toByte() || header[3] != 'F'.code.toByte()) {
					return null
				}
				val h = WavHeader()
				h.channels = (header[22].toInt() and 0xFF) or ((header[23].toInt() and 0xFF) shl 8)
				h.sampleRate = ((header[24].toInt() and 0xFF)
						or ((header[25].toInt() and 0xFF) shl 8)
						or ((header[26].toInt() and 0xFF) shl 16)
						or ((header[27].toInt() and 0xFF) shl 24))
				h.bitsPerSample = (header[34].toInt() and 0xFF) or ((header[35].toInt() and 0xFF) shl 8)
				var ds: Long = (header[40].toInt() and 0xFF).toLong()
				ds = ds or ((header[41].toInt() and 0xFF).toLong() shl 8)
				ds = ds or ((header[42].toInt() and 0xFF).toLong() shl 16)
				ds = ds or ((header[43].toInt() and 0xFF).toLong() shl 24)
				h.dataSize = ds
				h
			}
		} catch (e: IOException) {
			Timber.e(e, "Failed to read WAV header")
			null
		}
	}

	@Throws(IOException::class)
	private fun writeWavHeader(file: File, sampleRate: Int, channels: Int, bitsPerSample: Int, dataSize: Long) {
		val totalSize = dataSize + 36
		val byteRate = sampleRate.toLong() * channels * (bitsPerSample / 8)
		val blockAlign = channels * (bitsPerSample / 8)
		//32-bit float 用格式码 3（IEEE float），整数 PCM 用 1
		val audioFormat = if (bitsPerSample == 32) 3 else 1

		RandomAccessFile(file, "rw").use { raf ->
			raf.seek(0)
			raf.write(ByteArray(44).apply {
				this[0] = 'R'.code.toByte(); this[1] = 'I'.code.toByte(); this[2] = 'F'.code.toByte(); this[3] = 'F'.code.toByte()
				this[4] = (totalSize and 0xff).toByte()
				this[5] = ((totalSize shr 8) and 0xff).toByte()
				this[6] = ((totalSize shr 16) and 0xff).toByte()
				this[7] = ((totalSize shr 24) and 0xff).toByte()
				this[8] = 'W'.code.toByte(); this[9] = 'A'.code.toByte(); this[10] = 'V'.code.toByte(); this[11] = 'E'.code.toByte()
				this[12] = 'f'.code.toByte(); this[13] = 'm'.code.toByte(); this[14] = 't'.code.toByte(); this[15] = ' '.code.toByte()
				this[16] = 16; this[17] = 0; this[18] = 0; this[19] = 0
				this[20] = audioFormat.toByte(); this[21] = 0
				this[22] = channels.toByte(); this[23] = 0
				this[24] = (sampleRate and 0xff).toByte()
				this[25] = ((sampleRate shr 8) and 0xff).toByte()
				this[26] = ((sampleRate shr 16) and 0xff).toByte()
				this[27] = ((sampleRate shr 24) and 0xff).toByte()
				this[28] = (byteRate and 0xff).toByte()
				this[29] = ((byteRate shr 8) and 0xff).toByte()
				this[30] = ((byteRate shr 16) and 0xff).toByte()
				this[31] = ((byteRate shr 24) and 0xff).toByte()
				this[32] = blockAlign.toByte(); this[33] = 0
				this[34] = bitsPerSample.toByte(); this[35] = 0
				this[36] = 'd'.code.toByte(); this[37] = 'a'.code.toByte(); this[38] = 't'.code.toByte(); this[39] = 'a'.code.toByte()
				this[40] = (dataSize and 0xff).toByte()
				this[41] = ((dataSize shr 8) and 0xff).toByte()
				this[42] = ((dataSize shr 16) and 0xff).toByte()
				this[43] = ((dataSize shr 24) and 0xff).toByte()
			})
		}
	}

	private fun stripAudioExtension(path: String): String {
		for (ext in AppConstants.SUPPORTED_EXT) {
			if (path.endsWith(".$ext")) {
				return path.substring(0, path.length - ext.length - 1)
			}
		}
		return path
	}
}
