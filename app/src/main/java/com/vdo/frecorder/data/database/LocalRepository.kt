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

import com.vdo.frecorder.exception.FailedToRestoreRecord
import java.io.IOException

/**
 * 录音记录的持久化仓库：正片、书签、回收站都走这里。
 */
@JvmSuppressWildcards
interface LocalRepository {

    fun open()

    fun close()

    fun getRecord(id: Int): Record?

    fun findRecordByPath(path: String): Record?

    fun findRecordsByPath(path: String): MutableList<Record>

    fun hasRecordsWithPath(path: String): Boolean

    fun getTrashRecord(id: Int): Record?

    fun getAllRecords(): MutableList<Record>

    fun getAllItemsIds(): MutableList<Int>

    fun getRecords(page: Int): MutableList<Record>

    fun getRecords(page: Int, order: Int): MutableList<Record>

    fun deleteAllRecords(): Boolean

    fun getLastRecord(): Record?

    fun insertRecord(record: Record): Record?

    fun updateRecord(record: Record): Boolean

    fun updateTrashRecord(record: Record): Boolean

    @Throws(IOException::class)
    fun insertEmptyFile(filePath: String): Record?

    fun deleteRecord(id: Int): Boolean

    fun deleteRecordForever(id: Int)

    fun getRecordsDurations(): MutableList<Long>

    fun addToBookmarks(id: Int): Boolean

    fun removeFromBookmarks(id: Int): Boolean

    fun getBookmarks(): MutableList<Record>

    fun getTrashRecords(): MutableList<Record>

    fun getTrashRecordsIds(): MutableList<Int>

    fun getTrashRecordsCount(): Int

    @Throws(FailedToRestoreRecord::class)
    fun restoreFromTrash(id: Int)

    fun removeFromTrash(id: Int): Boolean

    fun emptyTrash(): Boolean

    fun removeOutdatedTrashRecords()

    fun setOnRecordsLostListener(listener: OnRecordsLostListener?)
}
