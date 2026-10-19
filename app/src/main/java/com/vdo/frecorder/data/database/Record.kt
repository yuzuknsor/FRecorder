/*
 * Copyright 2018 Dmytro Ponomarenko
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 */

package com.vdo.frecorder.data.database

import com.vdo.frecorder.AppConstants

/**
 * 一条录音记录：正片表与回收站表的行模型。
 * Kotlin 化（原 Record.java）。键名与构造器语义保持不变。
 */
class Record {

	val id: Int

	val name: String

	var duration: Long

	val created: Long

	val added: Long

	val removed: Long

	var path: String

	val format: String

	val size: Long

	val sampleRate: Int

	val channelCount: Int

	val bitrate: Int

	private var bookmark: Boolean

	val isWaveformProcessed: Boolean

	val amps: IntArray

	val data: ByteArray

	constructor(id: Int, name: String, duration: Long, created: Long, added: Long, removed: Long, path: String,
				format: String, size: Long, sampleRate: Int, channelCount: Int, bitrate: Int,
				bookmark: Boolean, waveformProcessed: Boolean, amps: IntArray) {
		this.id = id
		this.name = name
		this.duration = duration
		this.created = created
		this.added = added
		this.removed = removed
		this.path = path
		this.format = format
		this.size = size
		this.sampleRate = sampleRate
		this.channelCount = channelCount
		this.bitrate = bitrate
		this.bookmark = bookmark
		this.isWaveformProcessed = waveformProcessed
		this.amps = amps
		this.data = int2byte(amps)
	}

	constructor(id: Int, name: String, duration: Long, created: Long, added: Long, removed: Long, path: String,
				format: String, size: Long, sampleRate: Int, channelCount: Int, bitrate: Int,
				bookmark: Boolean, waveformProcessed: Boolean, amps: ByteArray) {
		this.id = id
		this.name = name
		this.duration = duration
		this.created = created
		this.added = added
		this.removed = removed
		this.path = path
		this.format = format
		this.size = size
		this.sampleRate = sampleRate
		this.channelCount = channelCount
		this.bitrate = bitrate
		this.bookmark = bookmark
		this.isWaveformProcessed = waveformProcessed
		this.amps = byte2int(amps)
		this.data = amps
	}

	fun int2byte(amps: IntArray): ByteArray {
		val bytes = ByteArray(amps.size)
		for (i in amps.indices) {
			bytes[i] = when {
				amps[i] >= 255 -> 127
				amps[i] < 0 -> 0
				else -> (amps[i] - 128).toByte()
			}
		}
		return bytes
	}

	fun byte2int(amps: ByteArray): IntArray {
		val ints = IntArray(amps.size)
		for (i in amps.indices) {
			ints[i] = amps[i] + 128
		}
		return ints
	}

	val nameWithExtension: String
		get() = name + AppConstants.EXTENSION_SEPARATOR + format

	val isBookmarked: Boolean
		get() = bookmark

	fun setBookmark(b: Boolean) {
		bookmark = b
	}

	override fun toString(): String {
		return "Record{" +
				"id=" + id +
				", name='" + name + '\'' +
				", duration=" + duration +
				", created=" + created +
				", added=" + added +
				", removed=" + removed +
				", path='" + path + '\'' +
				", format='" + format + '\'' +
				", size=" + size +
				", sampleRate=" + sampleRate +
				", channelCount=" + channelCount +
				", bitrate=" + bitrate +
				", bookmark=" + bookmark +
				", waveformProcessed=" + isWaveformProcessed +
				", amps=" + amps.contentToString() +
				", data=" + data.contentToString() +
				'}'
	}

	companion object {
		const val NO_ID = -1
	}
}
