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

package com.vdo.frecorder.app.records

import android.os.Parcel
import android.os.Parcelable
import com.vdo.frecorder.AppConstants
import com.vdo.frecorder.util.TimeUtils
import java.util.Arrays

/** 录音列表项（含头部/日期/脚部占位类型，可跨进程传）。Kotlin 化（原 ListItem.java），Parcel 布局保持不变。 */
class ListItem : Parcelable {

	val id: Long
	val type: Int
	val name: String
	val format: String
	val description: String
	val durationStr: String
	val duration: Long
	val size: Long
	val sampleRate: Int
	val channelCount: Int
	val bitrate: Int
	val created: Long
	val added: Long
	val addedTimeStr: String
	val createTimeStr: String
	var isBookmarked: Boolean
	val imageUrl: String
	val amps: IntArray?
	val path: String

	val nameWithExtension: String
		get() = name + AppConstants.EXTENSION_SEPARATOR + format

	constructor(id: Long, type: Int, name: String, format: String, description: String, duration: Long,
				size: Long, created: Long, added: Long, path: String, sampleRate: Int, channelCount: Int, bitrate: Int,
				bookmarked: Boolean, amps: IntArray?) {
		this.id = id
		this.type = type
		this.name = name
		this.format = format
		this.description = description
		this.created = created
		this.createTimeStr = convertTimeToStr(created)
		this.added = added
		this.addedTimeStr = convertTimeToStr(added)
		this.duration = duration
		this.size = size
		this.durationStr = convertDurationToStr(duration / 1000)
		this.path = path
		this.sampleRate = sampleRate
		this.channelCount = channelCount
		this.bitrate = bitrate
		this.isBookmarked = bookmarked
		this.imageUrl = ""
		this.amps = amps
	}

	private fun convertTimeToStr(time: Long): String {
		return TimeUtils.formatDateTimeLocale(time)
	}

	private fun convertDurationToStr(dur: Long): String {
		return TimeUtils.formatTimeIntervalHourMinSec2(dur)
	}

	//----- START Parcelable implementation ----------
	private constructor(parcel: Parcel) {
		val ints = IntArray(4)
		parcel.readIntArray(ints)
		type = ints[0]
		sampleRate = ints[1]
		channelCount = ints[2]
		bitrate = ints[3]
		val longs = LongArray(5)
		parcel.readLongArray(longs)
		id = longs[0]
		duration = longs[1]
		size = longs[2]
		created = longs[3]
		added = longs[4]
		val data = arrayOfNulls<String>(8)
		parcel.readStringArray(data)
		name = data[0] ?: ""
		format = data[1] ?: ""
		path = data[2] ?: ""
		description = data[3] ?: ""
		durationStr = data[4] ?: ""
		addedTimeStr = data[5] ?: ""
		createTimeStr = data[6] ?: ""
		imageUrl = data[7] ?: ""
		amps = null
		//与原实现一致：原 Java 把数组读入 null 引用而丢弃，amps 保持 null；
		//createIntArray 消费同样的流位置
		parcel.createIntArray()
		val bools = BooleanArray(1)
		parcel.readBooleanArray(bools)
		isBookmarked = bools[0]
	}

	override fun describeContents(): Int = 0

	override fun writeToParcel(out: Parcel, flags: Int) {
		out.writeIntArray(intArrayOf(type, sampleRate, channelCount, bitrate))
		out.writeLongArray(longArrayOf(id, duration, size, created, added))
		out.writeStringArray(arrayOf(name, format, path, description, durationStr, addedTimeStr, createTimeStr, imageUrl))
		out.writeIntArray(amps)
		out.writeBooleanArray(booleanArrayOf(isBookmarked))
	}

	override fun toString(): String {
		return "ListItem{" +
				"id=" + id +
				", type=" + type +
				", name='" + name + '\'' +
				", format='" + format + '\'' +
				", path='" + path + '\'' +
				", description='" + description + '\'' +
				", durationStr='" + durationStr + '\'' +
				", duration=" + duration +
				", size=" + size +
				", sampleRate=" + sampleRate +
				", channelCount=" + channelCount +
				", bitrate=" + bitrate +
				", created=" + created +
				", added=" + added +
				", addedTime='" + addedTimeStr + '\'' +
				", createTime='" + createTimeStr + '\'' +
				", bookmarked=" + isBookmarked +
				", avatar_url='" + imageUrl + '\'' +
				", amps=" + Arrays.toString(amps) +
				'}'
	}

	companion object {
		const val ITEM_TYPE_NORMAL = 1
		const val ITEM_TYPE_HEADER = 2
		const val ITEM_TYPE_DATE = 3
		const val ITEM_TYPE_FOOTER = 4

		@JvmStatic
		fun createHeaderItem(): ListItem {
			return ListItem(-1, ITEM_TYPE_HEADER, "HEADER", "", "", 0, 0, 0, 0, "", 0, 0, 0, false, null)
		}

		@JvmStatic
		fun createFooterItem(): ListItem {
			return ListItem(-1, ITEM_TYPE_FOOTER, "FOOTER", "", "", 0, 0, 0, 0, "", 0, 0, 0, false, null)
		}

		@JvmStatic
		fun createDateItem(date: Long): ListItem {
			return ListItem(-1, ITEM_TYPE_DATE, "DATE", "", "", 0, 0, 0, date, "", 0, 0, 0, false, null)
		}

		@JvmField
		val CREATOR: Parcelable.Creator<ListItem> = object : Parcelable.Creator<ListItem> {
			override fun createFromParcel(parcel: Parcel): ListItem {
				return ListItem(parcel)
			}

			override fun newArray(size: Int): Array<ListItem?> {
				return arrayOfNulls(size)
			}
		}
	}
}
