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

package com.vdo.frecorder.data.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import timber.log.Timber

/**
 * 操作 records 表：[SQLiteHelper.TABLE_RECORDS]。
 * Kotlin 化（原 RecordsDataSource.java）。
 */
class RecordsDataSource private constructor(context: Context) : DataSource<Record>(context, SQLiteHelper.TABLE_RECORDS) {

	override fun itemToContentValues(item: Record): ContentValues? {
		if (item.name.isNotEmpty()) {
			val values = ContentValues()
			if (item.id != Record.NO_ID) {
				values.put(SQLiteHelper.COLUMN_ID, item.id)
			}
			values.put(SQLiteHelper.COLUMN_NAME, item.name)
			values.put(SQLiteHelper.COLUMN_DURATION, item.duration)
			values.put(SQLiteHelper.COLUMN_CREATION_DATE, item.created)
			values.put(SQLiteHelper.COLUMN_DATE_ADDED, item.added)
			values.put(SQLiteHelper.COLUMN_PATH, item.path)
			values.put(SQLiteHelper.COLUMN_FORMAT, item.format)
			values.put(SQLiteHelper.COLUMN_SIZE, item.size)
			values.put(SQLiteHelper.COLUMN_SAMPLE_RATE, item.sampleRate)
			values.put(SQLiteHelper.COLUMN_CHANNEL_COUNT, item.channelCount)
			values.put(SQLiteHelper.COLUMN_BITRATE, item.bitrate)
			values.put(SQLiteHelper.COLUMN_BOOKMARK, if (item.isBookmarked) 1 else 0)
			values.put(SQLiteHelper.COLUMN_WAVEFORM_PROCESSED, if (item.isWaveformProcessed) 1 else 0)
			values.put(SQLiteHelper.COLUMN_DATA, item.data)
			//TODO: 从数据库中移除该字段
			values.put(SQLiteHelper.COLUMN_DATA_STR, "")
			return values
		} else {
			Timber.e("Can't convert Record with empty Name!")
			return null
		}
	}

	override fun recordToItem(cursor: Cursor): Record {
		return Record(
				cursor.getInt(cursor.getColumnIndex(SQLiteHelper.COLUMN_ID)),
				cursor.getString(cursor.getColumnIndex(SQLiteHelper.COLUMN_NAME)),
				cursor.getLong(cursor.getColumnIndex(SQLiteHelper.COLUMN_DURATION)),
				cursor.getLong(cursor.getColumnIndex(SQLiteHelper.COLUMN_CREATION_DATE)),
				cursor.getLong(cursor.getColumnIndex(SQLiteHelper.COLUMN_DATE_ADDED)),
				Long.MAX_VALUE, //此处不需要 removed date
				cursor.getString(cursor.getColumnIndex(SQLiteHelper.COLUMN_PATH)),
				cursor.getString(cursor.getColumnIndex(SQLiteHelper.COLUMN_FORMAT)),
				cursor.getLong(cursor.getColumnIndex(SQLiteHelper.COLUMN_SIZE)),
				cursor.getInt(cursor.getColumnIndex(SQLiteHelper.COLUMN_SAMPLE_RATE)),
				cursor.getInt(cursor.getColumnIndex(SQLiteHelper.COLUMN_CHANNEL_COUNT)),
				cursor.getInt(cursor.getColumnIndex(SQLiteHelper.COLUMN_BITRATE)),
				cursor.getInt(cursor.getColumnIndex(SQLiteHelper.COLUMN_BOOKMARK)) != 0,
				cursor.getInt(cursor.getColumnIndex(SQLiteHelper.COLUMN_WAVEFORM_PROCESSED)) != 0,
				cursor.getBlob(cursor.getColumnIndex(SQLiteHelper.COLUMN_DATA))
		)
	}

	companion object {
		@Volatile
		private var instance: RecordsDataSource? = null

		@JvmStatic
		fun getInstance(context: Context): RecordsDataSource {
			if (instance == null) {
				synchronized(RecordsDataSource::class.java) {
					if (instance == null) {
						instance = RecordsDataSource(context)
					}
				}
			}
			return instance!!
		}
	}
}
