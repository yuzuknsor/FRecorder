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
import com.vdo.frecorder.ARApplication
import com.vdo.frecorder.AppConstants
import com.vdo.frecorder.exception.CantCreateFileException
import com.vdo.frecorder.util.FileUtil
import timber.log.Timber
import java.io.File
import java.io.FileNotFoundException

/**
 * 录音文件存放位置与文件操作。单例，Kotlin 化（原 FileRepositoryImpl.java）。
 */
class FileRepositoryImpl private constructor(context: Context, private val prefs: Prefs) : FileRepository {

	private var recordDirectory: File = File("/")

	init {
		updateRecordingDir(context, prefs)
	}

	override fun provideRecordFile(): File {
		prefs.incrementRecordCounter()
		val recordName = when (prefs.getSettingNamingFormat()) {
			AppConstants.NAME_FORMAT_DATE -> FileUtil.generateRecordNameDateVariant()
			AppConstants.NAME_FORMAT_DATE_US -> FileUtil.generateRecordNameDateUS()
			AppConstants.NAME_FORMAT_DATE_ISO8601 -> FileUtil.generateRecordNameDateISO8601()
			AppConstants.NAME_FORMAT_TIMESTAMP -> FileUtil.generateRecordNameMills()
			else -> FileUtil.generateRecordNameCounted(prefs.getRecordCounter())
		}
		val extension = when (prefs.getSettingOutputFormat()) {
			AppConstants.OUTPUT_FORMAT_MP3 -> AppConstants.FORMAT_MP3
			AppConstants.OUTPUT_FORMAT_FLAC -> AppConstants.FORMAT_FLAC
			else -> AppConstants.FORMAT_WAV
		}
		return FileUtil.createFile(recordDirectory, FileUtil.addExtension(recordName, extension))
				?: throw CantCreateFileException()
	}

	override fun provideRecordFile(name: String): File {
		return FileUtil.createFile(recordDirectory, name) ?: throw CantCreateFileException()
	}

	override fun getPrivateDirFiles(context: Context): Array<File> {
		return try {
			FileUtil.getPrivateRecordsDir(context).listFiles() ?: emptyArray()
		} catch (e: FileNotFoundException) {
			Timber.e(e)
			emptyArray()
		}
	}

	override fun getPublicDirFiles(): Array<File> {
		return FileUtil.getAppDir()?.listFiles() ?: emptyArray()
	}

	override val publicDir: File
		get() = FileUtil.getAppDir() ?: hardcodedPrivateDir()

	override fun getPrivateDir(context: Context): File {
		return try {
			FileUtil.getPrivateRecordsDir(context)
		} catch (e: FileNotFoundException) {
			Timber.e(e)
			hardcodedPrivateDir()
		}
	}

	override fun getRecordingDir(): File = recordDirectory

	override fun deleteRecordFile(path: String): Boolean {
		return FileUtil.deleteFile(File(path))
	}

	override fun markAsTrashRecord(path: String): String {
		val trashLocation = FileUtil.addExtension(path, AppConstants.TRASH_MARK_EXTENSION)
		return if (FileUtil.renameFile(File(path), File(trashLocation))) trashLocation else path
	}

	override fun unmarkTrashRecord(path: String): String {
		val restoredFile = FileUtil.removeFileExtension(path)
		return if (FileUtil.renameFile(File(path), File(restoredFile))) restoredFile else path
	}

	override fun deleteAllRecords(): Boolean = false

	override fun renameFile(path: String, newName: String, extension: String): Boolean {
		return FileUtil.renameFile(File(path), newName, extension)
	}

	override fun updateRecordingDir(context: Context, prefs: Prefs) {
		recordDirectory = if (prefs.isStoreDirPublic) {
			//公共目录不可用时回退私有目录，再不行硬编码
			FileUtil.getAppDir() ?: privateDirOrFallback(context)
		} else {
			try {
				FileUtil.getPrivateRecordsDir(context)
			} catch (e: FileNotFoundException) {
				Timber.e(e)
				hardcodedPrivateDir()
			}
		}
	}

	private fun privateDirOrFallback(context: Context): File {
		return try {
			FileUtil.getPrivateRecordsDir(context)
		} catch (e: FileNotFoundException) {
			Timber.e(e)
			hardcodedPrivateDir()
		}
	}

	private fun hardcodedPrivateDir(): File = File("/data/data/" + ARApplication.appPackage() + "/files")

	override fun hasAvailableSpace(context: Context): Boolean {
		val space = if (prefs.isStoreDirPublic) {
			FileUtil.getAvailableExternalMemorySize()
		} else {
			FileUtil.getAvailableInternalMemorySize(context)
		}
		val time = spaceToTimeSecs(space, prefs.getSettingRecordingFormat(),
				prefs.getSettingSampleRate(), prefs.getSettingBitrate(), prefs.getSettingChannelCount())
		return time > AppConstants.MIN_REMAIN_RECORDING_TIME
	}

	companion object {
		@Volatile
		private var instance: FileRepositoryImpl? = null

		@JvmStatic
		fun getInstance(context: Context, prefs: Prefs): FileRepositoryImpl {
			return instance ?: synchronized(this) {
				instance ?: FileRepositoryImpl(context.applicationContext, prefs).also { instance = it }
			}
		}
	}

	private fun spaceToTimeSecs(spaceBytes: Long, recordingFormat: String?, sampleRate: Int, bitrate: Int, channels: Int): Long {
		//内部始终录 WAV：字节数 = 采样率 × 声道 × 2
		return 1000 * (spaceBytes / (sampleRate.toLong() * channels * 2))
	}
}
