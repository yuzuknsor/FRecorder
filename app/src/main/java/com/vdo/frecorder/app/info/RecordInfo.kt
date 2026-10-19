package com.vdo.frecorder.app.info

import android.os.Parcel
import android.os.Parcelable
import com.vdo.frecorder.AppConstants

/** 一条录音的元信息（可跨进程传）。Kotlin 化（原 RecordInfo.java），Parcel 布局保持不变。 */
class RecordInfo(
		val name: String,
		val format: String,
		val duration: Long,
		val size: Long,
		val location: String,
		val created: Long,
		val sampleRate: Int,
		val channelCount: Int,
		val bitrate: Int,
		val isInTrash: Boolean
) : Parcelable {

	var isInDatabase: Boolean = true

	val nameWithExtension: String
		get() = name + AppConstants.EXTENSION_SEPARATOR + format

	override fun describeContents(): Int = 0

	override fun writeToParcel(out: Parcel, flags: Int) {
		out.writeStringArray(arrayOf(name, format, location))
		out.writeLongArray(longArrayOf(duration, size, created))
		out.writeIntArray(intArrayOf(sampleRate, channelCount, bitrate))
		out.writeBooleanArray(booleanArrayOf(isInDatabase, isInTrash))
	}

	companion object {
		@JvmField
		val CREATOR: Parcelable.Creator<RecordInfo> = object : Parcelable.Creator<RecordInfo> {
			override fun createFromParcel(parcel: Parcel): RecordInfo = readFromParcel(parcel)

			override fun newArray(size: Int): Array<RecordInfo?> = arrayOfNulls(size)
		}

		private fun readFromParcel(parcel: Parcel): RecordInfo {
			val strings = arrayOfNulls<String>(3)
			parcel.readStringArray(strings)
			val longs = LongArray(3)
			parcel.readLongArray(longs)
			val ints = IntArray(3)
			parcel.readIntArray(ints)
			val booleans = BooleanArray(2)
			parcel.readBooleanArray(booleans)
			val info = RecordInfo(
					strings[0] ?: "",
					strings[1] ?: "",
					longs[0],
					longs[1],
					strings[2] ?: "",
					longs[2],
					ints[0],
					ints[1],
					ints[2],
					booleans[1]
			)
			info.isInDatabase = booleans[0]
			return info
		}
	}
}
