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

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

/**
 * SQLite 数据库管理器：建表与升级脚本。
 */
class SQLiteHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(CREATE_RECORDS_TABLE_SCRIPT)
        db.execSQL(CREATE_TRASH_TABLE_SCRIPT)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        Log.d(
            SQLiteHelper::class.java.name,
            "Upgrading database from version $oldVersion to $newVersion, which will destroy all old data"
        )
        when {
            newVersion == 1 -> {
                db.execSQL("DROP TABLE IF EXISTS $TABLE_RECORDS")
                db.execSQL("DROP TABLE IF EXISTS $TABLE_TRASH")
                onCreate(db)
            }
            newVersion == 2 -> db.execSQL(CREATE_TRASH_TABLE_SCRIPT)
            oldVersion == 1 && newVersion == 3 -> {
                db.beginTransaction()
                db.execSQL(CREATE_TRASH_TABLE_SCRIPT)
                // 记录表补新字段
                db.execSQL("ALTER TABLE $TABLE_RECORDS ADD COLUMN $COLUMN_FORMAT TEXT NOT NULL DEFAULT '';")
                db.execSQL("ALTER TABLE $TABLE_RECORDS ADD COLUMN $COLUMN_SIZE LONG NOT NULL DEFAULT 0;")
                db.execSQL("ALTER TABLE $TABLE_RECORDS ADD COLUMN $COLUMN_SAMPLE_RATE INTEGER NOT NULL DEFAULT 0;")
                db.execSQL("ALTER TABLE $TABLE_RECORDS ADD COLUMN $COLUMN_CHANNEL_COUNT INTEGER NOT NULL DEFAULT 0;")
                db.execSQL("ALTER TABLE $TABLE_RECORDS ADD COLUMN $COLUMN_BITRATE INTEGER NOT NULL DEFAULT 0;")
                db.setTransactionSuccessful()
                db.endTransaction()
            }
            oldVersion == 2 && newVersion == 3 -> {
                db.beginTransaction()
                // 记录表补新字段
                db.execSQL("ALTER TABLE $TABLE_RECORDS ADD COLUMN $COLUMN_FORMAT TEXT NOT NULL DEFAULT '';")
                db.execSQL("ALTER TABLE $TABLE_RECORDS ADD COLUMN $COLUMN_SIZE LONG NOT NULL DEFAULT 0;")
                db.execSQL("ALTER TABLE $TABLE_RECORDS ADD COLUMN $COLUMN_SAMPLE_RATE INTEGER NOT NULL DEFAULT 0;")
                db.execSQL("ALTER TABLE $TABLE_RECORDS ADD COLUMN $COLUMN_CHANNEL_COUNT INTEGER NOT NULL DEFAULT 0;")
                db.execSQL("ALTER TABLE $TABLE_RECORDS ADD COLUMN $COLUMN_BITRATE INTEGER NOT NULL DEFAULT 0;")
                // 回收站表补新字段
                db.execSQL("ALTER TABLE $TABLE_TRASH ADD COLUMN $COLUMN_FORMAT TEXT NOT NULL DEFAULT '';")
                db.execSQL("ALTER TABLE $TABLE_TRASH ADD COLUMN $COLUMN_SIZE LONG NOT NULL DEFAULT 0;")
                db.execSQL("ALTER TABLE $TABLE_TRASH ADD COLUMN $COLUMN_SAMPLE_RATE INTEGER NOT NULL DEFAULT 0;")
                db.execSQL("ALTER TABLE $TABLE_TRASH ADD COLUMN $COLUMN_CHANNEL_COUNT INTEGER NOT NULL DEFAULT 0;")
                db.execSQL("ALTER TABLE $TABLE_TRASH ADD COLUMN $COLUMN_BITRATE INTEGER NOT NULL DEFAULT 0;")
                db.setTransactionSuccessful()
                db.endTransaction()
            }
        }
    }

    companion object {
        private const val DATABASE_NAME = "records.db"
        private const val DATABASE_VERSION = 3

        // 表名
        const val TABLE_RECORDS = "records"
        const val TABLE_TRASH = "trash"

        // records 表字段
        const val COLUMN_ID = "_id"
        const val COLUMN_NAME = "name"
        const val COLUMN_DURATION = "duration"
        const val COLUMN_CREATION_DATE = "created"
        const val COLUMN_DATE_ADDED = "added"
        const val COLUMN_DATE_REMOVED = "removed"
        const val COLUMN_PATH = "path"

        /** 音频振幅的简化数组，用于绘制波形。 */
        const val COLUMN_DATA = "data"
        const val COLUMN_DATA_STR = "data_str"
        const val COLUMN_WAVEFORM_PROCESSED = "waveform_processed"
        const val COLUMN_BOOKMARK = "bookmark"
        const val COLUMN_FORMAT = "format"
        const val COLUMN_SIZE = "size"
        const val COLUMN_SAMPLE_RATE = "sample_rate"
        const val COLUMN_CHANNEL_COUNT = "channel_count"
        const val COLUMN_BITRATE = "bitrate"

        private const val CREATE_RECORDS_TABLE_SCRIPT =
            "CREATE TABLE $TABLE_RECORDS (" +
                "$COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "$COLUMN_NAME TEXT NOT NULL, " +
                "$COLUMN_DURATION LONG NOT NULL, " +
                "$COLUMN_CREATION_DATE LONG NOT NULL, " +
                "$COLUMN_DATE_ADDED LONG NOT NULL, " +
                "$COLUMN_PATH TEXT NOT NULL, " +
                "$COLUMN_FORMAT TEXT NOT NULL DEFAULT '', " +
                "$COLUMN_SIZE LONG NOT NULL DEFAULT 0, " +
                "$COLUMN_SAMPLE_RATE INTEGER NOT NULL DEFAULT 0, " +
                "$COLUMN_CHANNEL_COUNT INTEGER NOT NULL DEFAULT 0, " +
                "$COLUMN_BITRATE INTEGER NOT NULL DEFAULT 0, " +
                "$COLUMN_DATA BLOB NOT NULL, " +
                "$COLUMN_BOOKMARK INTEGER NOT NULL DEFAULT 0, " +
                "$COLUMN_WAVEFORM_PROCESSED INTEGER NOT NULL DEFAULT 0, " +
                "$COLUMN_DATA_STR BLOB NOT NULL);"

        private const val CREATE_TRASH_TABLE_SCRIPT =
            "CREATE TABLE $TABLE_TRASH (" +
                "$COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "$COLUMN_NAME TEXT NOT NULL, " +
                "$COLUMN_DURATION LONG NOT NULL, " +
                "$COLUMN_CREATION_DATE LONG NOT NULL, " +
                "$COLUMN_DATE_ADDED LONG NOT NULL, " +
                "$COLUMN_DATE_REMOVED LONG NOT NULL, " +
                "$COLUMN_PATH TEXT NOT NULL, " +
                "$COLUMN_FORMAT TEXT NOT NULL DEFAULT '', " +
                "$COLUMN_SIZE LONG NOT NULL DEFAULT 0, " +
                "$COLUMN_SAMPLE_RATE INTEGER NOT NULL DEFAULT 0, " +
                "$COLUMN_CHANNEL_COUNT INTEGER NOT NULL DEFAULT 0, " +
                "$COLUMN_BITRATE INTEGER NOT NULL DEFAULT 0, " +
                "$COLUMN_DATA BLOB NOT NULL, " +
                "$COLUMN_BOOKMARK INTEGER NOT NULL DEFAULT 0, " +
                "$COLUMN_WAVEFORM_PROCESSED INTEGER NOT NULL DEFAULT 0, " +
                "$COLUMN_DATA_STR BLOB NOT NULL);"
    }
}
