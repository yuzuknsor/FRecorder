package com.vdo.frecorder

import com.vdo.frecorder.app.info.RecordInfo
import com.vdo.frecorder.app.lostrecords.RecordItem
import com.vdo.frecorder.app.records.ListItem
import com.vdo.frecorder.data.database.Record
import com.vdo.frecorder.util.TimeUtils
import java.util.ArrayList

/** 各层数据模型转换。Kotlin 化（原 Mapper.java）。 */
object Mapper {

	@JvmStatic
	fun recordToListItem(record: Record?): ListItem? {
		if (record == null) return null
		return ListItem(
				record.id.toLong(),
				ListItem.ITEM_TYPE_NORMAL,
				record.name,
				record.format,
				TimeUtils.formatTimeIntervalHourMinSec2(record.duration / 1000),
				record.duration,
				record.size,
				record.created,
				record.added,
				record.path,
				record.sampleRate,
				record.channelCount,
				record.bitrate,
				record.isBookmarked,
				record.amps)
	}

	@JvmStatic
	fun recordsToListItems(records: List<Record>): MutableList<ListItem> {
		val items = ArrayList<ListItem>(records.size)
		for (i in records.indices) {
			recordToListItem(records[i])?.let { items.add(it) }
		}
		return items
	}

	@JvmStatic
	fun toRecordInfo(record: ListItem?): RecordInfo? {
		if (record == null) return null
		return RecordInfo(
				record.name,
				record.format,
				record.duration,
				record.size,
				record.path,
				record.created,
				record.sampleRate,
				record.channelCount,
				record.bitrate,
				false
		)
	}

	@JvmStatic
	fun toRecordInfo(record: RecordItem?): RecordInfo? {
		if (record == null) return null
		return RecordInfo(
				record.name,
				record.format,
				record.duration,
				record.size,
				record.path,
				record.created,
				record.sampleRate,
				record.channelCount,
				record.bitrate,
				false
		)
	}

	@JvmStatic
	fun toRecordInfoInTrash(record: RecordItem?): RecordInfo? {
		if (record == null) return null
		return RecordInfo(
				record.name,
				record.format,
				record.duration,
				record.size,
				record.path,
				record.created,
				record.sampleRate,
				record.channelCount,
				record.bitrate,
				true
		)
	}

	@JvmStatic
	fun toRecordInfo(record: Record?): RecordInfo? {
		if (record == null) return null
		return RecordInfo(
				record.name,
				record.format,
				record.duration,
				record.size,
				record.path,
				record.created,
				record.sampleRate,
				record.channelCount,
				record.bitrate,
				false
		)
	}

	@JvmStatic
	fun toRecordItem(r: Record?): RecordItem? {
		if (r == null) return null
		return RecordItem(
				r.id,
				r.name,
				r.size,
				r.format,
				r.duration,
				r.path,
				r.created,
				r.sampleRate,
				r.channelCount,
				r.bitrate)
	}

	@JvmStatic
	fun toRecordItemList(records: List<Record>): ArrayList<RecordItem> {
		val list = ArrayList<RecordItem>()
		for (r in records) {
			val recordItem = toRecordItem(r)
			if (recordItem != null) {
				list.add(recordItem)
			}
		}
		return list
	}
}
