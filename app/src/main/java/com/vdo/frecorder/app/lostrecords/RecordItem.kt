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

package com.vdo.frecorder.app.lostrecords

import android.os.Parcel
import android.os.Parcelable

/** 丢失录音列表项（可跨进程传）。Kotlin 化（原 RecordItem.java），Parcel 布局保持不变。 */
class RecordItem(
		val id: Int,
		val name: String,
		val size: Long,
		val format: String,
		val duration: Long,
		val path: String,
		val created: Long,
		val sampleRate: Int,
		val channelCount: Int,
		val bitrate: Int
) : Parcelable {

	override fun describeContents(): Int = 0

	override fun writeToParcel(out: Parcel, flags: Int) {
		out.writeLongArray(longArrayOf(duration, size, created))
		out.writeStringArray(arrayOf(name, format, path))
		out.writeIntArray(intArrayOf(id, sampleRate, channelCount, bitrate))
	}

	companion object {
		@JvmField
		val CREATOR: Parcelable.Creator<RecordItem> = object : Parcelable.Creator<RecordItem> {
			override fun createFromParcel(parcel: Parcel): RecordItem = readFromParcel(parcel)

			override fun newArray(size: Int): Array<RecordItem?> = arrayOfNulls(size)
		}

		private fun readFromParcel(parcel: Parcel): RecordItem {
			val longs = LongArray(3)
			parcel.readLongArray(longs)
			val strings = arrayOfNulls<String>(3)
			parcel.readStringArray(strings)
			val ints = IntArray(4)
			parcel.readIntArray(ints)
			return RecordItem(
					ints[0],
					strings[0] ?: "",
					longs[1],
					strings[1] ?: "",
					longs[0],
					strings[2] ?: "",
					longs[2],
					ints[1],
					ints[2],
					ints[3]
			)
		}
	}
}
