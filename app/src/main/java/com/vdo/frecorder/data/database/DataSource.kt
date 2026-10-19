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

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.SQLException
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.vdo.frecorder.AppConstants
import com.vdo.frecorder.BuildConfig

/**
 * 某张表 T 的数据访问基类。
 * Kotlin 化（原 DataSource.java）。子类多为 Java，protected 成员照旧暴露。
 */
abstract class DataSource<T>(context: Context, protected val tableName: String) {

	/** SQLite 管理器。 */
	protected val dbHelper: SQLiteHelper = SQLiteHelper(context)

	/** 数据库句柄（open() 之后有效）。 */
	protected lateinit var db: SQLiteDatabase

	private val logTag: String = this.javaClass.simpleName

	fun open() {
		if (!this::db.isInitialized || !db.isOpen) {
			db = dbHelper.writableDatabase
		}
	}

	fun close() {
		if (this::db.isInitialized) {
			db.close()
		}
		dbHelper.close()
	}

	val isOpen: Boolean
		get() = this::db.isInitialized && db.isOpen

	/** 插入新条目，返回落库后的对象（失败为 null）。 */
	open fun insertItem(item: T): T? {
		val values = itemToContentValues(item)
		return if (values != null) {
			val insertId = db.insert(tableName, null, values).toInt()
			Log.d(logTag, "Insert into $tableName id = $insertId")
			getItem(insertId)
		} else {
			Log.e(logTag, "Unable to write empty item!")
			null
		}
	}

	/** 条目 → ContentValues。 */
	abstract fun itemToContentValues(item: T): ContentValues?

	fun deleteItem(id: Int): Int {
		Log.d(logTag, "$tableName deleted ID = $id")
		return db.delete(tableName, SQLiteHelper.COLUMN_ID + " = " + id, null)
	}

	fun updateItem(item: T): Int {
		val values = itemToContentValues(item)
		return if (values != null && values.containsKey(SQLiteHelper.COLUMN_ID)) {
			val where = SQLiteHelper.COLUMN_ID + " = " + values.get(SQLiteHelper.COLUMN_ID)
			val n = db.update(tableName, values, where, null)
			Log.d(logTag, "Updated records count = $n")
			n
		} else {
			Log.e(logTag, "Unable to update empty item!")
			0
		}
	}

	fun getAll(): ArrayList<T> {
		val cursor = queryLocal("SELECT * FROM " + tableName + " ORDER BY " + SQLiteHelper.COLUMN_DATE_ADDED + " DESC")
		return convertCursor(cursor)
	}

	fun getAllItemsIds(): ArrayList<Int> {
		val cursor = queryLocal("SELECT " + SQLiteHelper.COLUMN_ID + " FROM " + tableName +
				" ORDER BY " + SQLiteHelper.COLUMN_DATE_ADDED + " DESC")
		return convertCursorIds(cursor)
	}

	fun getRecords(page: Int): ArrayList<T> {
		val cursor = queryLocal("SELECT * FROM " + tableName +
				" ORDER BY " + SQLiteHelper.COLUMN_DATE_ADDED + " DESC" +
				" LIMIT " + AppConstants.DEFAULT_PER_PAGE +
				" OFFSET " + (page - 1) * AppConstants.DEFAULT_PER_PAGE)
		return convertCursor(cursor)
	}

	val count: Int
		get() {
			val cursor = queryLocal("SELECT COUNT(*) FROM $tableName")
			return if (cursor != null) {
				cursor.moveToFirst()
				cursor.getInt(0)
			} else {
				-1
			}
		}

	fun getRecords(page: Int, order: String): ArrayList<T> {
		val cursor = queryLocal("SELECT * FROM " + tableName +
				" ORDER BY " + order +
				" LIMIT " + AppConstants.DEFAULT_PER_PAGE +
				" OFFSET " + (page - 1) * AppConstants.DEFAULT_PER_PAGE)
		return convertCursor(cursor)
	}

	@Throws(SQLException::class)
	fun deleteAll() {
		db.execSQL("DELETE FROM $tableName")
	}

	fun getItems(where: String): ArrayList<T> {
		val cursor = queryLocal("SELECT * FROM $tableName WHERE $where")
		return convertCursor(cursor)
	}

	fun getItem(id: Int): T? {
		val cursor = queryLocal("SELECT * FROM " + tableName +
				" WHERE " + SQLiteHelper.COLUMN_ID + " = " + id)
		val list = convertCursor(cursor)
		return if (list.isNotEmpty()) list[0] else null
	}

	fun convertCursor(cursor: Cursor): ArrayList<T> {
		val items = ArrayList<T>()
		cursor.moveToFirst()
		while (!cursor.isAfterLast && !cursor.isBeforeFirst) {
			items.add(recordToItem(cursor))
			cursor.moveToNext()
		}
		cursor.close()
		return items
	}

	fun convertCursorIds(cursor: Cursor): ArrayList<Int> {
		val items = ArrayList<Int>()
		cursor.moveToFirst()
		while (!cursor.isAfterLast && !cursor.isBeforeFirst) {
			items.add(cursor.getInt(cursor.getColumnIndex(SQLiteHelper.COLUMN_ID)))
			cursor.moveToNext()
		}
		cursor.close()
		return items
	}

	/** 光标当前行 → 条目 T。 */
	abstract fun recordToItem(cursor: Cursor): T

	/** 本地查询，顺带打日志。同包调用（LocalRepositoryImpl）需要，故为 public。 */
	fun queryLocal(query: String): Cursor {
		Log.d(logTag, "queryLocal: $query")
		val c = db.rawQuery(query, null)
		if (BuildConfig.DEBUG) {
			val data = StringBuilder("Cursor[")
			if (c.moveToFirst()) {
				do {
					val columnCount = c.columnCount
					data.append("row[")
					for (i in 0 until columnCount) {
						data.append(c.getColumnName(i)).append(" = ")
						when (c.getType(i)) {
							Cursor.FIELD_TYPE_BLOB -> data.append("byte array")
							Cursor.FIELD_TYPE_FLOAT -> data.append(c.getFloat(i))
							Cursor.FIELD_TYPE_INTEGER -> data.append(c.getInt(i))
							Cursor.FIELD_TYPE_NULL -> data.append("null")
							Cursor.FIELD_TYPE_STRING -> data.append(c.getString(i))
						}
						if (i != columnCount - 1) {
							data.append(", ")
						}
					}
					data.append("]\n")
				} while (c.moveToNext())
			}
			data.append("]")
			Log.d(logTag, data.toString())
		}
		return c
	}

	//TODO: 这个方法该挪走
	open fun getRecordsDurations(): MutableList<Long> {
		val cursor = queryLocal("SELECT " + SQLiteHelper.COLUMN_DURATION + " FROM " + tableName)
		val items = ArrayList<Long>()
		cursor.moveToFirst()
		while (!cursor.isAfterLast) {
			items.add(cursor.getLong(cursor.getColumnIndex(SQLiteHelper.COLUMN_DURATION)))
			cursor.moveToNext()
		}
		cursor.close()
		return items
	}
}
