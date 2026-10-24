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

package com.vdo.frecorder.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.vdo.frecorder.ARApplication
import com.vdo.frecorder.AppConstants
import com.vdo.frecorder.AppConstants.Companion.SUPPORTED_EXT
import com.vdo.frecorder.IntArrayList
import com.vdo.frecorder.app.info.RecordInfo
import com.vdo.frecorder.util.FileUtil
import timber.log.Timber
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.ByteOrder
import java.util.Arrays

/** 音频解码：文件 → 波形振幅数组 + 元数据。Kotlin 化（原 AudioDecoder.java）。 */
object AudioDecoder {

	private const val QUEUE_INPUT_BUFFER_EFFECTIVE = 1 //最快
	private const val QUEUE_INPUT_BUFFER_SIMPLE = 2   //较慢的回退方案
	private const val TRASH_EXT = "del"

	private var dpPerSec = AppConstants.SHORT_RECORD_DP_PER_SECOND.toFloat()
	private var sampleRate = 0
	private var channelCount = 0
	private var oneFrameAmps = IntArray(0)
	private var frameIndex = 0
	private var duration = 0L
	private lateinit var gains: IntArrayList

	@JvmStatic
	fun decode(fileName: String, decodeListener: AudioDecodingListener) {
		try {
			val file = File(fileName)
			if (!file.exists()) {
				throw FileNotFoundException(fileName)
			}
			val name = file.name.lowercase()
			val components = name.split("\\.".toRegex()).toTypedArray()
			if (components.size < 2) {
				throw IOException()
			}
			if (!Arrays.asList(*SUPPORTED_EXT).contains(components[components.size - 1])) {
				throw IOException()
			}
			AudioDecoder.decodeFile(file, decodeListener, QUEUE_INPUT_BUFFER_EFFECTIVE)
		} catch (e: Exception) {
			decodeListener.onError(e)
		}
	}

	private fun calculateSamplesPerFrame(): Int = (sampleRate / dpPerSec).toInt()

	@Throws(IOException::class, OutOfMemoryError::class, IllegalStateException::class)
	private fun decodeFile(mInputFile: File, decodeListener: AudioDecodingListener, queueType: Int) {
		gains = IntArrayList()
		val extractor = MediaExtractor()
		var format: MediaFormat? = null

		extractor.setDataSource(mInputFile.path)
		val numTracks = extractor.trackCount
		//找到并选中第一个音频轨
		var i = 0
		while (i < numTracks) {
			format = extractor.getTrackFormat(i)
			if (format.getString(MediaFormat.KEY_MIME)!!.startsWith("audio/")) {
				extractor.selectTrack(i)
				break
			}
			i++
		}

		if (i == numTracks || format == null) {
			throw IOException("No audio track found in $mInputFile")
		}
		channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
		sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
		duration = format.getLong(MediaFormat.KEY_DURATION)

		//TODO: 让波形与 dpPerSec 解耦
		dpPerSec = ARApplication.getDpPerSecond(duration / 1000000f)
		oneFrameAmps = IntArray(calculateSamplesPerFrame() * channelCount)

		val mimeType = format.getString(MediaFormat.KEY_MIME)
		val decoder = MediaCodec.createDecoderByType(mimeType!!)

		decodeListener.onStartProcessing(duration, channelCount, sampleRate)
		decoder.setCallback(object : MediaCodec.Callback() {

			private var mOutputEOS = false
			private var mInputEOS = false
			private var decoded = 0L
			private val totalSize = mInputFile.length()
			private var percent = 0

			override fun onError(codec: MediaCodec, exception: MediaCodec.CodecException) {
				Timber.e(exception)
				if (queueType == QUEUE_INPUT_BUFFER_EFFECTIVE) {
					try {
						AudioDecoder.decodeFile(mInputFile, decodeListener, QUEUE_INPUT_BUFFER_SIMPLE)
					} catch (e: IllegalStateException) {
						decodeListener.onError(exception)
					} catch (e: IOException) {
						decodeListener.onError(exception)
					} catch (e: OutOfMemoryError) {
						decodeListener.onError(exception)
					}
				} else {
					decodeListener.onError(exception)
				}
			}

			override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {}

			override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
				if (mOutputEOS or mInputEOS) return
				if (decodeListener.isCanceled()) {
					codec.queueInputBuffer(index, 0, 0, -1, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
					mInputEOS = true
					return
				}
				try {
					val inputBuffer = codec.getInputBuffer(index) ?: return
					var sampleTime = 0L
					if (queueType == QUEUE_INPUT_BUFFER_EFFECTIVE) {
						//批量读样本进同一缓冲，效率最高
						var total = 0
						var advanced = false
						var maxresult = 0
						var result: Int
						do {
							result = extractor.readSampleData(inputBuffer, total)
							if (result >= 0) {
								total += result
								sampleTime = extractor.sampleTime
								advanced = extractor.advance()
								maxresult = maxOf(maxresult, result)
							}
						} while (result >= 0 && total < maxresult * 5 && advanced && inputBuffer.capacity() - inputBuffer.limit() > maxresult * 3) //3 是保险余量，去掉会崩
						decoded += total
						if (advanced) {
							codec.queueInputBuffer(index, 0, total, sampleTime, 0)
						} else {
							codec.queueInputBuffer(index, 0, 0, -1, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
							mInputEOS = true
						}
					} else {
						//有效模式失败后的回退
						val result = extractor.readSampleData(inputBuffer, 0)
						decoded += result
						if (result >= 0) {
							sampleTime = extractor.sampleTime
							codec.queueInputBuffer(index, 0, result, sampleTime, 0)
							extractor.advance()
						} else {
							codec.queueInputBuffer(index, 0, 0, -1, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
							mInputEOS = true
						}
					}
				} catch (e: IllegalStateException) {
					Timber.e(e)
				} catch (e: IllegalArgumentException) {
					Timber.e(e)
				}
				val curProgress = (100 * decoded / totalSize.toFloat()).toInt()
				if (curProgress != percent) {
					percent = curProgress
					decodeListener.onProcessingProgress(percent)
				}
			}

			override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
				try {
					val outputBuffer = codec.getOutputBuffer(index)
					if (outputBuffer != null) {
						outputBuffer.rewind()
						outputBuffer.order(ByteOrder.LITTLE_ENDIAN)
						while (outputBuffer.remaining() > 0) {
							oneFrameAmps[frameIndex] = outputBuffer.short.toInt()
							frameIndex++
							if (frameIndex >= oneFrameAmps.size - 1) {
								var gain = -1
								var j = 0
								while (j < oneFrameAmps.size) {
									var value = 0
									for (k in 0 until channelCount) {
										value += oneFrameAmps[j + k]
									}
									value /= channelCount
									if (gain < value) {
										gain = value
									}
									j += channelCount
								}
								gains.add(Math.sqrt(gain.toDouble()).toInt())
								frameIndex = 0
							}
						}
					}

					mOutputEOS = mOutputEOS or (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0)
					codec.releaseOutputBuffer(index, false)

					if (mOutputEOS) {
						if (decodeListener.isCanceled()) {
							decodeListener.onProcessingCancel()
						} else {
							decodeListener.onProcessingProgress(100)
							decodeListener.onFinishProcessing(gains.getData(), duration)
						}
						codec.stop()
						codec.release()
						extractor.release()
					}
				} catch (e: IllegalStateException) {
					Timber.e(e)
				}
			}
		})
		decoder.configure(format, null, null, 0)
		decoder.start()
	}

	@JvmStatic
	@Throws(OutOfMemoryError::class, IllegalStateException::class)
	fun readRecordInfo(inputFile: File): RecordInfo {
		var isInTrash = false
		return try {
			val canonicalFile = inputFile.canonicalFile
			if (!canonicalFile.exists()) {
				throw FileNotFoundException(canonicalFile.absolutePath)
			}
			val name = inputFile.name.lowercase()
			val components = name.split("\\.".toRegex()).toTypedArray()
			if (components.size < 2) {
				throw IOException()
			}
			isInTrash = TRASH_EXT.equals(components[components.size - 1], ignoreCase = true)
			if (!isInTrash && !FileUtil.isSupportedExtension(components[components.size - 1])) {
				throw IOException()
			}

			val extractor = MediaExtractor()
			var format: MediaFormat? = null
			extractor.setDataSource(canonicalFile.path)
			val numTracks = extractor.trackCount
			var i = 0
			while (i < numTracks) {
				format = extractor.getTrackFormat(i)
				try {
					if (format.getString(MediaFormat.KEY_MIME)!!.startsWith("audio/")) {
						extractor.selectTrack(i)
						break
					}
				} catch (e: Exception) {
					Timber.e(e)
				}
				i++
			}
			if (i == numTracks || format == null) {
				throw IOException("No audio track found in $inputFile")
			}

			val channelCount = try {
				format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
			} catch (e: Exception) {
				Timber.e(e); 0
			}
			val sampleRate = try {
				format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
			} catch (e: Exception) {
				Timber.e(e); 0
			}
			val bitrate = try {
				format.getInteger(MediaFormat.KEY_BIT_RATE)
			} catch (e: Exception) {
				Timber.e(e); 0
			}
			val duration = try {
				format.getLong(MediaFormat.KEY_DURATION)
			} catch (e: Exception) {
				Timber.e(e); 0
			}
			val mimeType = try {
				format.getString(MediaFormat.KEY_MIME) ?: ""
			} catch (e: Exception) {
				Timber.e(e); ""
			}

			RecordInfo(
					FileUtil.removeFileExtension(inputFile.name),
					readFileFormat(inputFile, mimeType),
					duration,
					inputFile.length(),
					inputFile.absolutePath,
					inputFile.lastModified(),
					sampleRate,
					channelCount,
					bitrate,
					isInTrash
			)
		} catch (e: Exception) {
			Timber.e(e)
			RecordInfo(
					FileUtil.removeFileExtension(inputFile.name), "", 0, inputFile.length(),
					inputFile.absolutePath, inputFile.lastModified(), 0, 0, 0, isInTrash
			)
		}
	}

	@JvmStatic
	fun readRecordMime(inputFile: File): String {
		return try {
			if (!inputFile.exists()) {
				throw FileNotFoundException(inputFile.absolutePath)
			}
			val name = inputFile.name.lowercase()
			val components = name.split("\\.".toRegex()).toTypedArray()
			if (components.size < 2) {
				throw IOException()
			}
			val extractor = MediaExtractor()
			var format: MediaFormat? = null
			extractor.setDataSource(inputFile.path)
			val numTracks = extractor.trackCount
			var i = 0
			while (i < numTracks) {
				format = extractor.getTrackFormat(i)
				try {
					if (format.getString(MediaFormat.KEY_MIME)!!.startsWith("audio/")) {
						extractor.selectTrack(i)
						break
					}
				} catch (e: Exception) {
					Timber.e(e)
				}
				i++
			}
			if (i == numTracks || format == null) {
				throw IOException("No audio track found in $inputFile")
			}
			try {
				format.getString(MediaFormat.KEY_MIME) ?: ""
			} catch (e: Exception) {
				Timber.e(e); ""
			}
		} catch (e: Exception) {
			Timber.e(e)
			"audio/*"
		}
	}

	private fun readFileFormat(file: File, mime: String?): String {
		val name = file.name.lowercase()
		return when {
			name.contains(AppConstants.FORMAT_M4A) || (mime != null && mime.contains("audio") && mime.contains("mp4a")) -> AppConstants.FORMAT_M4A
			name.contains(AppConstants.FORMAT_WAV) || (mime != null && mime.contains("audio") && mime.contains("raw")) -> AppConstants.FORMAT_WAV
			name.contains(AppConstants.FORMAT_3GP) || (mime != null && mime.contains("audio") && mime.contains("3gpp")) -> AppConstants.FORMAT_3GP
			name.contains(AppConstants.FORMAT_3GPP) -> AppConstants.FORMAT_3GPP
			name.contains(AppConstants.FORMAT_MP3) || (mime != null && mime.contains("audio") && mime.contains("mpeg")) -> AppConstants.FORMAT_MP3
			name.contains(AppConstants.FORMAT_AMR) -> AppConstants.FORMAT_AMR
			name.contains(AppConstants.FORMAT_AAC) -> AppConstants.FORMAT_AAC
			name.contains(AppConstants.FORMAT_MP4) -> AppConstants.FORMAT_MP4
			name.contains(AppConstants.FORMAT_OGG) -> AppConstants.FORMAT_OGG
			name.contains(AppConstants.FORMAT_FLAC) || (mime != null && mime.contains("audio") && mime.contains(AppConstants.FORMAT_FLAC)) -> AppConstants.FORMAT_FLAC
			else -> ""
		}
	}
}
