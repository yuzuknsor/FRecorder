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

import android.annotation.SuppressLint
import android.database.Cursor
import android.database.SQLException
import androidx.annotation.VisibleForTesting
import com.vdo.frecorder.ARApplication
import com.vdo.frecorder.AppConstants
import com.vdo.frecorder.data.FileRepository
import com.vdo.frecorder.data.Prefs
import com.vdo.frecorder.data.database.SQLiteHelper.Companion.COLUMN_PATH
import com.vdo.frecorder.exception.FailedToRestoreRecord
import com.vdo.frecorder.util.FileUtil
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.Date

/**
 * 录音记录仓库实现：正片表 + 回收站表 + 文件改名联动。
 * Kotlin 化（原 LocalRepositoryImpl.java）。
 */
class LocalRepositoryImpl private constructor(
		private val dataSource: RecordsDataSource,
		private val trashDataSource: TrashDataSource,
		private val fileRepository: FileRepository,
		private val prefs: Prefs
) : LocalRepository {

	override fun open() {
		dataSource.open()
		trashDataSource.open()
	}

	override fun close() {
		dataSource.close()
		trashDataSource.close()
	}

	override fun getRecord(id: Int): Record? {
		if (!dataSource.isOpen) {
			dataSource.open()
		}
		val r = dataSource.getItem(id) ?: return null
		checkForLostRecords(listOf(r))
		return r
	}

	override fun findRecordByPath(path: String): Record? {
		if (!dataSource.isOpen) {
			dataSource.open()
		}
		val escaped = if (path.contains("'")) path.replace("'", "''") else path
		val records = dataSource.getItems("$COLUMN_PATH = '$escaped'")
		return if (records.isEmpty()) null else records[0]
	}

	override fun findRecordsByPath(path: String): MutableList<Record> {
		if (!dataSource.isOpen) {
			dataSource.open()
		}
		val escaped = if (path.contains("'")) path.replace("'", "''") else path
		return dataSource.getItems("$COLUMN_PATH LIKE '%$escaped%'")
	}

	override fun hasRecordsWithPath(path: String): Boolean {
		if (!dataSource.isOpen) {
			dataSource.open()
		}
		val escaped = if (path.contains("'")) path.replace("'", "''") else path
		val records = dataSource.getItems("$COLUMN_PATH LIKE '%$escaped%' LIMIT 1")
		return records.isNotEmpty()
	}

	override fun getTrashRecord(id: Int): Record? {
		if (!trashDataSource.isOpen) {
			trashDataSource.open()
		}
		return trashDataSource.getItem(id)
	}

	override fun insertRecord(record: Record): Record? {
		if (!dataSource.isOpen) {
			dataSource.open()
		}
		return dataSource.insertItem(record)
	}

	override fun updateRecord(record: Record): Boolean {
		if (!dataSource.isOpen) {
			dataSource.open()
		}
		return dataSource.updateItem(record) > 0
	}

	override fun updateTrashRecord(record: Record): Boolean {
		if (!trashDataSource.isOpen) {
			trashDataSource.open()
		}
		return trashDataSource.updateItem(record) > 0
	}

	@Throws(IOException::class)
	override fun insertEmptyFile(filePath: String): Record? {
		if (filePath.isNotEmpty()) {
			val file = File(filePath)
			val record = Record(
					Record.NO_ID,
					FileUtil.removeFileExtension(file.name),
					0, //mills
					file.lastModified(),
					Date().time,
					Long.MAX_VALUE,
					filePath,
					prefs.getSettingRecordingFormat(),
					0,
					prefs.getSettingSampleRate(),
					prefs.getSettingChannelCount(),
					prefs.getSettingBitrate(),
					false,
					false,
					IntArray(ARApplication.longWaveformSampleCount)
			)
			val r = insertRecord(record)
			if (r != null) {
				return r
			} else {
				Timber.e("Failed to insert record into local database!")
			}
		} else {
			Timber.e("Unable to read sound file by specified path!")
			throw IOException("Unable to read sound file by specified path!")
		}
		return null
	}

	override fun getAllRecords(): MutableList<Record> {
		if (!dataSource.isOpen) {
			dataSource.open()
		}
		val list = dataSource.all
		checkForLostRecords(list)
		return list
	}

	override fun getAllItemsIds(): MutableList<Int> {
		if (!dataSource.isOpen) {
			dataSource.open()
		}
		return dataSource.allItemsIds
	}

	override fun getRecords(page: Int): MutableList<Record> {
		if (!dataSource.isOpen) {
			dataSource.open()
		}
		val list = dataSource.getRecords(page)
		checkForLostRecords(list)
		return list
	}

	override fun getRecords(page: Int, order: Int): MutableList<Record> {
		if (!dataSource.isOpen) {
			dataSource.open()
		}
		val orderStr = when (order) {
			AppConstants.SORT_NAME -> SQLiteHelper.COLUMN_NAME + " ASC"
			AppConstants.SORT_NAME_DESC -> SQLiteHelper.COLUMN_NAME + " DESC"
			AppConstants.SORT_DURATION -> SQLiteHelper.COLUMN_DURATION + " DESC"
			AppConstants.SORT_DURATION_DESC -> SQLiteHelper.COLUMN_DURATION + " ASC"
			AppConstants.SORT_DATE_DESC -> SQLiteHelper.COLUMN_DATE_ADDED + " ASC"
			else -> SQLiteHelper.COLUMN_DATE_ADDED + " DESC"
		}
		val list = dataSource.getRecords(page, orderStr)
		checkForLostRecords(list)
		return list
	}

	override fun getLastRecord(): Record? {
		if (!dataSource.isOpen) {
			dataSource.open()
		}
		val c: Cursor? = dataSource.queryLocal(
				"SELECT * FROM " + SQLiteHelper.TABLE_RECORDS +
						" ORDER BY " + SQLiteHelper.COLUMN_ID + " DESC LIMIT 1"
		)
		if (c != null && c.moveToFirst()) {
			val r = dataSource.recordToItem(c)
			if (!isFileExists(r.path)) {
				checkForLostRecords(listOf(r))
			}
			return r
		}
		return null
	}

	private fun isFileExists(path: String): Boolean = File(path).exists()

	override fun deleteRecord(id: Int): Boolean {
		if (!dataSource.isOpen) {
			dataSource.open()
		}
		if (!trashDataSource.isOpen) {
			trashDataSource.open()
		}
		val recordToDelete = dataSource.getItem(id) ?: return false
		var renamed: String? = fileRepository.markAsTrashRecord(recordToDelete.path)
		if (renamed == null) {
			renamed = fileRepository.markAsTrashRecord(recordToDelete.path)
		}
		if (renamed == null) return false

		recordToDelete.path = renamed
		if (trashDataSource.insertItem(recordToDelete) != null) {
			return dataSource.deleteItem(id) > 0
		}
		//回收站入库失败 → 把文件名改回去
		rollbackTrashRename(renamed)
		return false
	}

	private fun rollbackTrashRename(renamed: String) {
		if (fileRepository.unmarkTrashRecord(renamed) == null) {
			if (fileRepository.unmarkTrashRecord(renamed) == null) {
				fileRepository.unmarkTrashRecord(renamed)
			}
		}
	}

	override fun deleteRecordForever(id: Int) {
		if (!dataSource.isOpen) {
			dataSource.open()
		}
		dataSource.deleteItem(id)
	}

	override fun getRecordsDurations(): MutableList<Long> {
		if (!dataSource.isOpen) {
			dataSource.open()
		}
		return dataSource.recordsDurations as MutableList<Long>
	}

	override fun addToBookmarks(id: Int): Boolean {
		if (!dataSource.isOpen) {
			dataSource.open()
		}
		val r = dataSource.getItem(id) ?: return false
		r.setBookmark(true)
		return dataSource.updateItem(r) > 0
	}

	override fun removeFromBookmarks(id: Int): Boolean {
		if (!dataSource.isOpen) {
			dataSource.open()
		}
		val r = dataSource.getItem(id) ?: return false
		r.setBookmark(false)
		return dataSource.updateItem(r) > 0
	}

	override fun getBookmarks(): MutableList<Record> {
		if (!dataSource.isOpen) {
			dataSource.open()
		}
		val list = mutableListOf<Record>()
		val c: Cursor? = dataSource.queryLocal(
				"SELECT * FROM " + SQLiteHelper.TABLE_RECORDS +
						" WHERE " + SQLiteHelper.COLUMN_BOOKMARK + " = 1" +
						" ORDER BY " + SQLiteHelper.COLUMN_CREATION_DATE + " DESC"
		)
		if (c != null && c.moveToFirst()) {
			do {
				list.add(dataSource.recordToItem(c))
			} while (c.moveToNext())
		} else {
			return mutableListOf()
		}
		Timber.v("Bookmarks: %s", list.toString())
		return list
	}

	override fun getTrashRecords(): MutableList<Record> {
		if (!trashDataSource.isOpen) {
			trashDataSource.open()
		}
		return trashDataSource.all
	}

	override fun getTrashRecordsIds(): MutableList<Int> {
		if (!trashDataSource.isOpen) {
			trashDataSource.open()
		}
		return trashDataSource.allItemsIds
	}

	override fun getTrashRecordsCount(): Int {
		if (!trashDataSource.isOpen) {
			trashDataSource.open()
		}
		return trashDataSource.count
	}

	@Throws(FailedToRestoreRecord::class)
	override fun restoreFromTrash(id: Int) {
		if (!trashDataSource.isOpen) {
			trashDataSource.open()
		}
		val recordToRestore = trashDataSource.getItem(id) ?: throw FailedToRestoreRecord()
		if (!restoreRecord(recordToRestore)) {
			throw FailedToRestoreRecord()
		}
		//从回收站删记录，最多试 3 次
		if (trashDataSource.deleteItem(id) <= 0) {
			if (trashDataSource.deleteItem(id) <= 0) {
				trashDataSource.deleteItem(id)
			}
		}
	}

	private fun restoreRecord(record: Record): Boolean {
		var renamed = fileRepository.unmarkTrashRecord(record.path)
		if (renamed == null) {
			renamed = fileRepository.unmarkTrashRecord(record.path)
		}
		if (renamed == null) return false

		record.path = renamed
		if (insertRecord(record) != null) return true
		//入库失败 → 文件名改回回收站态
		if (fileRepository.markAsTrashRecord(renamed) == null) {
			if (fileRepository.markAsTrashRecord(renamed) == null) {
				fileRepository.markAsTrashRecord(renamed)
			}
		}
		return false
	}

	override fun removeFromTrash(id: Int): Boolean {
		if (!trashDataSource.isOpen) {
			trashDataSource.open()
		}
		return trashDataSource.deleteItem(id) > 0
	}

	override fun emptyTrash(): Boolean {
		if (!trashDataSource.isOpen) {
			trashDataSource.open()
		}
		return try {
			trashDataSource.deleteAll()
			true
		} catch (e: SQLException) {
			Timber.e(e)
			false
		}
	}

	override fun removeOutdatedTrashRecords() {
		if (!trashDataSource.isOpen) {
			trashDataSource.open()
		}
		val curTime = Date().time
		val list = trashDataSource.all
		for (item in list) {
			if (item.removed + AppConstants.RECORD_IN_TRASH_MAX_DURATION < curTime) {
				fileRepository.deleteRecordFile(item.path)
				trashDataSource.deleteItem(item.id)
			}
		}
	}

	//原实现就是不删（保留接口）
	override fun deleteAllRecords(): Boolean = false

	private fun checkForLostRecords(list: List<Record>) {
		val lost: MutableList<Record> = list.filter { !isFileExists(it.path) }.toMutableList()
		if (lost.isNotEmpty()) {
			onLostRecordsListener?.onLostRecords(lost)
		}
	}

	override fun setOnRecordsLostListener(listener: OnRecordsLostListener?) {
		onLostRecordsListener = listener
	}

	private var onLostRecordsListener: OnRecordsLostListener? = null

	companion object {
		@Volatile
		private var instance: LocalRepositoryImpl? = null

		@JvmStatic
		fun getInstance(source: RecordsDataSource, trashSource: TrashDataSource,
						fileRepository: FileRepository, prefs: Prefs): LocalRepositoryImpl {
			instance?.let { return it }
			synchronized(LocalRepositoryImpl::class.java) {
				instance?.let { return it }
				val created = LocalRepositoryImpl(source, trashSource, fileRepository, prefs)
				created.removeOutdatedTrashRecords()
				instance = created
				return created
			}
		}

		@VisibleForTesting
		@JvmStatic
		fun clearInstance() {
			synchronized(LocalRepositoryImpl::class.java) {
				instance = null
			}
		}
	}
}
