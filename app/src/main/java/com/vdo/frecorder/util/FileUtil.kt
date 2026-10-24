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

package com.vdo.frecorder.util

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.DocumentsContract
import android.util.Log
import com.vdo.frecorder.AppConstants
import com.vdo.frecorder.AppConstants.Companion.SUPPORTED_EXT
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.FileDescriptor
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** 文件工具集。Kotlin 化（原 FileUtil.java），行为与键名保持不变。 */
object FileUtil {

	private const val LOG_TAG = "FileUtil"

	/** copyLarge 的默认缓冲区 */
	private const val DEFAULT_BUFFER_SIZE = 1024 * 4

	/** 流结束符 */
	const val EOF = -1

	/** 去掉路径分隔符与上跳序列，防路径穿越 */
	@JvmStatic
	fun sanitizeFileName(name: String?): String? {
		if (name == null) return null
		val sanitized = name.replace("..", "")
				.replace(File.separator, "")
				.replace("/", "")
				.replace("\\", "")
				.replace("\u0000", "")
		return if (sanitized.isEmpty()) null else sanitized
	}

	/** 校验 file 的规范路径是否落在 parentDir 下，防穿越 */
	@JvmStatic
	fun isPathWithinDir(file: File, parentDir: File): Boolean {
		return try {
			val canonicalFile = file.canonicalPath
			val canonicalParent = parentDir.canonicalPath + File.separator
			canonicalFile.startsWith(canonicalParent)
		} catch (e: IOException) {
			Timber.e(e, "Failed to resolve canonical path")
			false
		}
	}

	@JvmStatic
	fun getAppDir(): File? = getStorageDir(AppConstants.APPLICATION_NAME)

	@JvmStatic
	@Throws(FileNotFoundException::class)
	fun getPrivateRecordsDir(context: Context): File {
		return getPrivateMusicStorageDir(context, AppConstants.RECORDS_DIR)
				?: throw FileNotFoundException()
	}

	@JvmStatic
	fun generateRecordNameCounted(counter: Long): String = AppConstants.BASE_RECORD_NAME + counter

	@JvmStatic
	fun generateRecordNameDateVariant(): String = TimeUtils.formatDateForNameVariant(System.currentTimeMillis())

	@JvmStatic
	fun generateRecordNameDateUS(): String = TimeUtils.formatDateForNameUS(System.currentTimeMillis())

	@JvmStatic
	fun generateRecordNameDateISO8601(): String = TimeUtils.formatDateForNameISO8601(System.currentTimeMillis())

	@JvmStatic
	fun generateRecordNameMills(): String = System.currentTimeMillis().toString()

	@JvmStatic
	fun addExtension(name: String, extension: String): String = name + AppConstants.EXTENSION_SEPARATOR + extension

	/** 去掉已识别的扩展名；未识别则原样返回 */
	@JvmStatic
	fun removeFileExtension(name: String): String {
		if (name.contains(AppConstants.EXTENSION_SEPARATOR)) {
			val extIndex = name.lastIndexOf(AppConstants.EXTENSION_SEPARATOR)
			if (extIndex >= 0 && extIndex + 1 < name.length
					&& (isSupportedExtension(name.substring(extIndex + 1)) || isDelExtension(name.substring(extIndex + 1)))) {
				return name.substring(0, name.lastIndexOf(AppConstants.EXTENSION_SEPARATOR))
			}
		}
		return name
	}

	@JvmStatic
	fun isSupportedExtension(ext: String): Boolean = SUPPORTED_EXT.any { e -> e.equals(ext, ignoreCase = true) }

	@JvmStatic
	fun isDelExtension(ext: String): Boolean = AppConstants.TRASH_MARK_EXTENSION.equals(ext, ignoreCase = true)

	@JvmStatic
	@Throws(IOException::class)
	fun copyLarge(input: InputStream, output: OutputStream): Long = copyLarge(input, output, ByteArray(DEFAULT_BUFFER_SIZE))

	@JvmStatic
	@Throws(IOException::class)
	fun copyLarge(input: InputStream, output: OutputStream, buffer: ByteArray): Long {
		var count = 0L
		var n: Int
		while (EOF != input.read(buffer).also { n = it }) {
			output.write(buffer, 0, n)
			count += n.toLong()
		}
		return count
	}

	@JvmStatic
	@Throws(IOException::class)
	fun copyLarge(input: InputStream, output: OutputStream, buffer: ByteArray, listener: FileOnCopyListener?): Long {
		var count = 0L
		val size = input.available().toLong()
		var percent: Int
		var stepPercent = 0
		var isCancel = false
		var n: Int
		while (EOF != input.read(buffer).also { n = it } && !isCancel) {
			output.write(buffer, 0, n)
			count += n.toLong()
			percent = (100f * count / size).toInt()
			if (listener != null) {
				isCancel = listener.isCancel()
				if (percent > stepPercent + 1 || count == size) {
					listener.onCopyProgress(percent)
					stepPercent = percent
				}
			}
		}
		if (listener != null) {
			if (isCancel) {
				listener.onCanceled()
				return -1
			} else {
				listener.onCopyFinish(null)
			}
		}
		return count
	}

	/** 带进度回调的文件复制 */
	@JvmStatic
	fun copyFile(fileToCopy: File, newFile: File, listener: FileOnCopyListener?): Boolean {
		if (fileToCopy.exists() && newFile.exists()) {
			var input: FileInputStream? = null
			var out: FileOutputStream? = null
			try {
				input = FileInputStream(fileToCopy)
				out = FileOutputStream(newFile)
				return if (copyLarge(input, out, ByteArray(DEFAULT_BUFFER_SIZE), listener) > 0) {
					true
				} else {
					Timber.e("Nothing was copied!")
					out.close()
					out = null
					deleteFile(newFile)
					false
				}
			} catch (e: Exception) {
				listener?.onError(null)
				return false
			} finally {
				try {
					input?.close()
				} catch (e: IOException) {
					Timber.e(e)
				}
				try {
					out?.close()
				} catch (e: IOException) {
					Timber.e(e)
				}
			}
		}
		listener?.onError(null)
		return false
	}

	/** FileDescriptor 版复制，含穿越校验 */
	@JvmStatic
	@Throws(IOException::class)
	fun copyFile(fileToCopy: FileDescriptor, newFile: File): Boolean {
		if (newFile.parentFile != null && !isPathWithinDir(newFile, newFile.parentFile)) {
			Log.e(LOG_TAG, "Path traversal detected in copyFile, rejecting: " + newFile.path)
			return false
		}
		var input: FileInputStream? = null
		var out: FileOutputStream? = null
		try {
			input = FileInputStream(fileToCopy)
			out = FileOutputStream(newFile.canonicalFile)
			if (copyLarge(input, out) > 0) {
				return true
			}
			Timber.e("Nothing was copied!")
			return false
		} catch (e: Exception) {
			return false
		} finally {
			input?.close()
			out?.close()
		}
	}

	/** 指定目录的剩余空间（字节） */
	@JvmStatic
	fun getFree(f: File): Long {
		var dir = f
		while (!dir.exists()) {
			dir = dir.parentFile ?: return 0
		}
		val fsi = StatFs(dir.path)
		return if (Build.VERSION.SDK_INT >= 18) {
			fsi.blockSizeLong * fsi.availableBlocksLong
		} else {
			fsi.blockSize.toLong() * fsi.availableBlocks
		}
	}

	@JvmStatic
	@Throws(IllegalArgumentException::class)
	fun getAvailableInternalMemorySize(context: Context): Long {
		val file = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: return 0
		val fsi = StatFs(file.absolutePath)
		return if (Build.VERSION.SDK_INT >= 18) {
			fsi.blockSizeLong * fsi.availableBlocksLong
		} else {
			fsi.blockSize.toLong() * fsi.availableBlocks
		}
	}

	@JvmStatic
	@Throws(IllegalArgumentException::class)
	fun getAvailableExternalMemorySize(): Long {
		return if (externalMemoryAvailable()) {
			//TODO: deprecated，待换 MediaStore 方案
			val path = Environment.getExternalStorageDirectory()
			val fsi = StatFs(path.path)
			if (Build.VERSION.SDK_INT >= 18) {
				fsi.blockSizeLong * fsi.availableBlocksLong
			} else {
				fsi.blockSize.toLong() * fsi.availableBlocks
			}
		} else {
			0
		}
	}

	@JvmStatic
	fun externalMemoryAvailable(): Boolean =
			Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED

	/** 建文件；重名时自动加前缀重试；拒绝穿越路径 */
	@JvmStatic
	fun createFile(path: File?, fileName: String): File? {
		if (path == null) return null
		val safeName = sanitizeFileName(fileName)
		if (safeName == null) {
			Log.e(LOG_TAG, "Invalid file name after sanitization: $fileName")
			return null
		}
		createDir(path)
		Log.d(LOG_TAG, "createFile path = ${path.absolutePath} fileName = $safeName")
		val file = File(path, safeName)
		if (!isPathWithinDir(file, path)) {
			Log.e(LOG_TAG, "Path traversal detected, rejecting file: $safeName")
			return null
		}
		if (!file.exists()) {
			try {
				if (file.createNewFile()) {
					Log.i(LOG_TAG, "The file was successfully created! - ${file.absolutePath}")
				} else {
					Log.i(LOG_TAG, "The file exist! - ${file.absolutePath}")
				}
			} catch (e: IOException) {
				Log.e(LOG_TAG, "Failed to create the file.", e)
				return null
			}
		} else {
			Log.e(LOG_TAG, "File already exists!! Please rename file!")
			Log.i(LOG_TAG, "Renaming file")
			//TODO: 找更好的重命名策略
			return createFile(path, "1$safeName")
		}
		if (!file.canWrite()) {
			Log.e(LOG_TAG, "The file can not be written.")
		}
		return file
	}

	@JvmStatic
	fun createDir(dir: File?): File? {
		if (dir != null) {
			if (!dir.exists()) {
				try {
					if (dir.mkdirs()) {
						Log.d(LOG_TAG, "Dirs are successfully created")
						return dir
					} else {
						Log.e(LOG_TAG, "Dirs are NOT created! Please check permission write to external storage!")
					}
				} catch (e: Exception) {
					Timber.e(e)
				}
			} else {
				Log.d(LOG_TAG, "Dir already exists")
				return dir
			}
		}
		Log.e(LOG_TAG, "File is null or unable to create dirs")
		return null
	}

	/** 写位图到文件 */
	@JvmStatic
	fun writeImage(file: File, bitmap: android.graphics.Bitmap?, quality: Int): Boolean {
		if (!file.canWrite()) {
			Log.e(LOG_TAG, "The file can not be written.")
			return false
		}
		if (bitmap == null) {
			Log.e(LOG_TAG, "Failed to write! bitmap is null.")
			return false
		}
		try {
			FileOutputStream(file).use { fos ->
				if (bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, fos)) {
					fos.flush()
					return true
				}
			}
		} catch (e: FileNotFoundException) {
			Log.e(LOG_TAG, "File not found: " + e.message)
		} catch (e: IOException) {
			Log.e(LOG_TAG, "Error accessing file: " + e.message)
		}
		return false
	}

	@JvmStatic
	fun getStorageDir(dirName: String?): File? {
		//TODO: deprecated，待换 MediaStore 方案
		return if (dirName != null && dirName.isNotEmpty()) {
			val file = File(Environment.getExternalStorageDirectory(), dirName)
			if (isExternalStorageReadable && isExternalStorageWritable) {
				createDir(file)
			} else {
				Log.e(LOG_TAG, "External storage are not readable or writable")
			}
			file
		} else {
			null
		}
	}

	val isExternalStorageWritable: Boolean
		get() = Environment.MEDIA_MOUNTED == Environment.getExternalStorageState()

	val isExternalStorageReadable: Boolean
		get() = Environment.MEDIA_MOUNTED == Environment.getExternalStorageState()
				|| Environment.MEDIA_MOUNTED_READ_ONLY == Environment.getExternalStorageState()

	@JvmStatic
	fun isFileInExternalStorage(context: Context, path: String?): Boolean {
		var privateDir = ""
		try {
			privateDir = getPrivateRecordsDir(context).absolutePath
		} catch (e: FileNotFoundException) {
			Timber.e(e)
		}
		return path == null || !path.contains(privateDir)
	}

	@JvmStatic
	fun getPublicMusicStorageDir(albumName: String): File {
		val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), albumName)
		if (!file.mkdirs()) {
			Log.e(LOG_TAG, "Directory not created")
		}
		return file
	}

	@JvmStatic
	fun getPrivateMusicStorageDir(context: Context, albumName: String): File? {
		val file = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: return null
		val f = File(file, albumName)
		if (!f.exists() && !f.mkdirs()) {
			Log.e(LOG_TAG, "Directory not created")
		} else {
			return f
		}
		return null
	}

	@JvmStatic
	fun renameFile(file: File, newName: String, extension: String): Boolean {
		if (!file.exists()) return false
		Timber.v("old File: " + file.absolutePath)
		val renamed = File(file.parentFile!!.absolutePath + File.separator + newName + AppConstants.EXTENSION_SEPARATOR + extension)
		Timber.v("new File: " + renamed.absolutePath)
		if (!file.renameTo(renamed)) {
			if (!file.renameTo(renamed)) {
				return file.renameTo(renamed)
			}
		}
		return true
	}

	@JvmStatic
	fun renameFile(file: File, renamed: File): Boolean {
		if (!file.exists()) return false
		Timber.v("old File: " + file.absolutePath)
		Timber.v("new File: " + renamed.absolutePath)
		if (!file.renameTo(renamed)) {
			if (!file.renameTo(renamed)) {
				return file.renameTo(renamed)
			}
		}
		return true
	}

	@JvmStatic
	fun removeUnallowedSignsFromName(name: String): String = name.replace("[:]", "_").trim()

	/** 递归删除文件或目录 */
	@JvmStatic
	fun deleteFile(file: File?): Boolean {
		return if (deleteRecursivelyDirs(file)) {
			true
		} else {
			Log.e(LOG_TAG, "Failed to delete directory: " + (file?.absolutePath ?: "null"))
			false
		}
	}

	private fun deleteRecursivelyDirs(file: File?): Boolean {
		var ok = true
		if (file != null && file.exists()) {
			if (file.isDirectory) {
				val children = file.list()
				if (children != null) {
					for (child in children) {
						ok = ok and deleteRecursivelyDirs(File(file, child))
					}
				}
			}
			if (ok && file.delete()) {
				Log.d(LOG_TAG, "File deleted: " + file.absolutePath)
			}
		}
		return ok
	}

	private fun isVirtualFile(context: Context, uri: Uri): Boolean {
		if (!DocumentsContract.isDocumentUri(context, uri)) {
			return false
		}
		context.contentResolver.query(
				uri,
				arrayOf(DocumentsContract.Document.COLUMN_FLAGS),
				null, null, null)?.use { cursor ->
			if (cursor.moveToFirst()) {
				val flags = cursor.getInt(0)
				return flags and DocumentsContract.Document.FLAG_VIRTUAL_DOCUMENT != 0
			}
		}
		return false
	}

	@Throws(IOException::class)
	private fun getInputStreamForVirtualFile(context: Context, uri: Uri, mimeTypeFilter: String?): InputStream {
		val resolver = context.contentResolver
		val openableMimeTypes = resolver.getStreamTypes(uri, mimeTypeFilter ?: "*/*")
		if (openableMimeTypes == null || openableMimeTypes.isEmpty()) {
			throw FileNotFoundException()
		}
		return resolver
				.openTypedAssetFileDescriptor(uri, openableMimeTypes[0], null)
				?.createInputStream()
				?: throw FileNotFoundException()
	}

	private fun getMimeType(url: String): String? {
		val extension = android.webkit.MimeTypeMap.getFileExtensionFromUrl(url)
		return if (extension != null) {
			android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
		} else null
	}

	/** 从 content URI 落盘到目标目录 */
	@JvmStatic
	fun saveFile(context: Context, name: String?, sourceuri: Uri, destinationDir: String, destFileName: String): Boolean {
		var input: InputStream? = null
		var hasError = false
		try {
			input = if (isVirtualFile(context, sourceuri)) {
				getInputStreamForVirtualFile(context, sourceuri, getMimeType(name ?: ""))
			} else {
				context.contentResolver.openInputStream(sourceuri)
			}
			val destDir = File(destinationDir)
			val directorySetupResult = when {
				!destDir.exists() -> destDir.mkdirs()
				!destDir.isDirectory -> replaceFileWithDir(destinationDir)
				else -> true
			}
			if (!directorySetupResult) {
				hasError = true
			} else {
				val destination = destinationDir + File.separator + destFileName
				BufferedInputStream(input).use { bis ->
					BufferedOutputStream(FileOutputStream(destination)).use { bos ->
						val buf = ByteArray(input!!.available())
						while (bis.read(buf) != -1) {
							bos.write(buf)
						}
					}
				}
			}
		} catch (e: Exception) {
			e.printStackTrace()
			hasError = true
		} finally {
			try {
				input?.close()
			} catch (ignored: Exception) {
			}
		}
		return !hasError
	}

	private fun replaceFileWithDir(path: String): Boolean {
		val file = File(path)
		return when {
			!file.exists() -> file.mkdirs()
			file.delete() -> File(path).mkdirs()
			else -> false
		}
	}

	interface FileOnCopyListener {
		fun isCancel(): Boolean
		fun onCopyProgress(percent: Int)
		fun onCanceled()
		fun onCopyFinish(message: String?)
		fun onError(message: String?)
	}
}
