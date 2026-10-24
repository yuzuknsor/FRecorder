/*
 * Copyright 2018 Dmytro Ponomarenko
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

package com.vdo.frecorder

/** 简化版 int 动态数组。Kotlin 化（原 IntArrayList.java）。 */
class IntArrayList {

	private var data = IntArray(100)
	private var sizeField = 0

	fun add(value: Int) {
		if (data.size == sizeField) {
			grow()
			add(value)
			return
		}
		data[sizeField] = value
		sizeField++
	}

	operator fun get(index: Int): Int = data[index]

	fun getData(): IntArray {
		val arr = IntArray(sizeField)
		for (i in 0 until sizeField) {
			arr[i] = data[i]
		}
		return arr
	}

	fun clear() {
		data = IntArray(100)
		sizeField = 0
	}

	fun size(): Int = sizeField

	private fun grow() {
		val backup = data
		data = IntArray(data.size * 2)
		for (i in backup.indices) {
			data[i] = backup[i]
		}
	}
}
