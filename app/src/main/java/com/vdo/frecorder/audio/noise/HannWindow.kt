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

/**
 * 预计算的 Hann 窗，用于频谱分析。
 */
class HannWindow(size: Int) {

	private val window = DoubleArray(size)

	init {
		for (i in 0 until size) {
			window[i] = 0.5 * (1.0 - Math.cos(2.0 * Math.PI * i / (size - 1)))
		}
	}

	/**
	 * 就地对给定采样应用 Hann 窗。
	 */
	fun apply(samples: DoubleArray) {
		val len = minOf(samples.size, window.size)
		for (i in 0 until len) {
			samples[i] *= window[i]
		}
	}

	fun size(): Int = window.size

	fun get(index: Int): Double = window[index]
}
