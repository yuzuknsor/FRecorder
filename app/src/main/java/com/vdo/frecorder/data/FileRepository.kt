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

package com.vdo.frecorder.data

import android.content.Context
import com.vdo.frecorder.exception.CantCreateFileException
import java.io.File

/**
 * 录音文件的存放位置与文件操作。
 */
interface FileRepository {

    @Throws(CantCreateFileException::class)
    fun provideRecordFile(): File

    @Throws(CantCreateFileException::class)
    fun provideRecordFile(name: String): File

    fun getPrivateDirFiles(context: Context): Array<File>

    fun getPublicDirFiles(): Array<File>

    val publicDir: File

    fun getPrivateDir(context: Context): File

    fun getRecordingDir(): File

    fun deleteRecordFile(path: String): Boolean

    fun markAsTrashRecord(path: String): String?

    fun unmarkTrashRecord(path: String): String?

    fun deleteAllRecords(): Boolean

    fun renameFile(path: String, newName: String, extension: String): Boolean

    fun updateRecordingDir(context: Context, prefs: Prefs)

    @Throws(IllegalArgumentException::class)
    fun hasAvailableSpace(context: Context): Boolean
}
