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
 * 纯 Kotlin 的 radix-2 Cooley-Tukey FFT 实现。
 * 数组长度必须是 2 的幂。
 */
object FFTHelper {

	/**
	 * 原地计算 FFT，re[] 与 im[] 会被就地修改。
	 * @param re 实部（长度必须为 2 的幂）
	 * @param im 虚部（与 re 等长）
	 * @param forward true 为正向 FFT，false 为逆向 FFT
	 */
	@JvmStatic
	fun fft(re: DoubleArray, im: DoubleArray, forward: Boolean) {
		val n = re.size
		if (n == 0 || (n and (n - 1)) != 0) {
			throw IllegalArgumentException("Length must be a power of 2, got $n")
		}

		//位反转置换
		val bits = Integer.numberOfTrailingZeros(n)
		for (i in 0 until n) {
			val j = Integer.reverse(i) ushr (32 - bits)
			if (i < j) {
				val tempRe = re[i]
				val tempIm = im[i]
				re[i] = re[j]
				im[i] = im[j]
				re[j] = tempRe
				im[j] = tempIm
			}
		}

		//Cooley-Tukey 迭代 FFT
		var size = 2
		while (size <= n) {
			val halfSize = size / 2
			val angle = 2.0 * Math.PI / size * (if (forward) -1 else 1)
			val wRe = Math.cos(angle)
			val wIm = Math.sin(angle)

			var i = 0
			while (i < n) {
				var curRe = 1.0
				var curIm = 0.0
				for (j in 0 until halfSize) {
					val evenIdx = i + j
					val oddIdx = i + j + halfSize

					val tRe = curRe * re[oddIdx] - curIm * im[oddIdx]
					val tIm = curRe * im[oddIdx] + curIm * re[oddIdx]

					re[oddIdx] = re[evenIdx] - tRe
					im[oddIdx] = im[evenIdx] - tIm
					re[evenIdx] = re[evenIdx] + tRe
					im[evenIdx] = im[evenIdx] + tIm

					val newCurRe = curRe * wRe - curIm * wIm
					curIm = curRe * wIm + curIm * wRe
					curRe = newCurRe
				}
				i += size
			}
			size *= 2
		}

		//逆向 FFT 归一化
		if (!forward) {
			for (i in 0 until n) {
				re[i] /= n
				im[i] /= n
			}
		}
	}

	/**
	 * 计算正向 FFT。
	 */
	@JvmStatic
	fun forward(re: DoubleArray, im: DoubleArray) {
		fft(re, im, true)
	}

	/**
	 * 计算逆向 FFT。
	 */
	@JvmStatic
	fun inverse(re: DoubleArray, im: DoubleArray) {
		fft(re, im, false)
	}
}
