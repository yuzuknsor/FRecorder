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

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Color
import android.graphics.Point
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.text.Editable
import android.text.Html
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.text.style.ImageSpan
import android.text.style.StyleSpan
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import com.vdo.frecorder.ARApplication
import com.vdo.frecorder.AppConstants
import com.vdo.frecorder.Mapper
import com.vdo.frecorder.R
import com.vdo.frecorder.app.lostrecords.LostRecordsActivity
import com.vdo.frecorder.data.database.Record
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.IntBuffer

/** Android 相关工具方法。Kotlin 化（原 AndroidUtils.java）。 */
object AndroidUtils {

	/**
	 * 密度无关像素（dip）转像素（px）。
	 */
	@JvmStatic
	fun dpToPx(dp: Int): Float = dpToPx(dp.toFloat())

	/**
	 * 密度无关像素（dip）转像素（px）。
	 */
	@JvmStatic
	fun dpToPx(dp: Float): Float = dp * Resources.getSystem().displayMetrics.density

	/**
	 * 返回屏幕像素密度。
	 */
	@JvmStatic
	fun getDisplayDensity(): Float = Resources.getSystem().displayMetrics.density

	/**
	 * 像素（px）转密度无关像素（dip）。
	 */
	@JvmStatic
	fun pxToDp(px: Int): Float = pxToDp(px.toFloat())

	/**
	 * 像素（px）转密度无关像素（dip）。
	 */
	@JvmStatic
	fun pxToDp(px: Float): Float = px / Resources.getSystem().displayMetrics.density

	@JvmStatic
	fun screenWidth(): Int = Resources.getSystem().displayMetrics.widthPixels

	@JvmStatic
	fun screenHeight(): Int = Resources.getSystem().displayMetrics.heightPixels

	@JvmStatic
	fun convertMillsToPx(mills: Long, pxPerSec: Float): Int {
		//1000 毫秒即 1 秒
		return (mills * pxPerSec / 1000).toInt()
	}

	@JvmStatic
	fun convertPxToMills(px: Long, pxPerSecond: Float): Int {
		return (1000 * px / pxPerSecond).toInt()
	}

	//获取状态栏高度
	@JvmStatic
	fun getStatusBarHeight(context: Context): Int {
		var result = 0
		val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
		if (resourceId > 0) {
			result = context.resources.getDimensionPixelSize(resourceId)
		}
		return result
	}

	//获取导航栏高度
	@JvmStatic
	fun getNavigationBarHeight(context: Context): Int {
		var result = 0
		try {
			if (hasNavBar(context)) {
				val resourceId = context.resources.getIdentifier("navigation_bar_height", "dimen", "android")
				if (resourceId > 0) {
					result = context.resources.getDimensionPixelSize(resourceId)
				}
			}
		} catch (e: Resources.NotFoundException) {
			Timber.e(e)
			return 0
		}
		return result
	}

	//此方法结果不完全准确
	@JvmStatic
	fun hasNavBar(context: Context): Boolean {
		val hasBackKey = KeyCharacterMap.deviceHasKey(KeyEvent.KEYCODE_BACK)
		val hasHomeKey = KeyCharacterMap.deviceHasKey(KeyEvent.KEYCODE_HOME)
		return !hasHomeKey && !hasBackKey
	}

	@JvmStatic
	fun setTranslucent(activity: Activity, translucent: Boolean) {
		val w = activity.window
		if (translucent) {
			w.setFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS, WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
		} else {
			w.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
		}
	}

	@JvmStatic
	fun runOnUIThread(runnable: Runnable) {
		runOnUIThread(runnable, 0)
	}

	@JvmStatic
	fun runOnUIThread(runnable: Runnable, delay: Long) {
		if (delay == 0L) {
			ARApplication.applicationHandler!!.post(runnable)
		} else {
			ARApplication.applicationHandler!!.postDelayed(runnable, delay)
		}
	}

	@JvmStatic
	fun cancelRunOnUIThread(runnable: Runnable) {
		ARApplication.applicationHandler!!.removeCallbacks(runnable)
	}

	/**
	 * int 数组转 byte 数组
	 */
	@JvmStatic
	fun int2byte(src: IntArray): ByteArray {
		val byteBuffer = ByteBuffer.allocate(src.size * 4)
		val intBuffer = byteBuffer.asIntBuffer()
		intBuffer.put(src)
		return byteBuffer.array()
	}

	/**
	 * byte 数组转 int 数组
	 */
	@JvmStatic
	fun byte2int(src: ByteArray): IntArray {
		val dstLength = src.size ushr 2
		val dst = IntArray(dstLength)

		for (i in 0 until dstLength) {
			var j = i shl 2
			var x = 0
			x += (src[j++].toInt() and 0xff) shl 0
			x += (src[j++].toInt() and 0xff) shl 8
			x += (src[j++].toInt() and 0xff) shl 16
			x += (src[j].toInt() and 0xff) shl 24
			dst[i] = x
		}
		return dst
	}

	@JvmStatic
	fun getScreenWidth(context: Context): Int {
		val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
		@Suppress("DEPRECATION")
		val display = wm.defaultDisplay
		val size = Point()
		@Suppress("DEPRECATION")
		display.getSize(size)
		return size.x
	}

	@JvmStatic
	fun getScreenHeight(context: Context): Int {
		val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
		@Suppress("DEPRECATION")
		val display = wm.defaultDisplay
		val size = Point()
		@Suppress("DEPRECATION")
		display.getSize(size)
		return size.y
	}

	/**
	 * 读取音频文件时长。
	 * @return 时长（微秒）
	 */
	@JvmStatic
	fun readRecordDuration(file: File): Long {
		try {
			val extractor = MediaExtractor()
			var format: MediaFormat? = null
			var i: Int

			extractor.setDataSource(file.path)
			val numTracks = extractor.trackCount
			//找到并选中文件中的第一条音轨
			i = 0
			while (i < numTracks) {
				format = extractor.getTrackFormat(i)
				if (format.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
					extractor.selectTrack(i)
					break
				}
				i++
			}

			if (i == numTracks) {
				throw IOException("No audio track found in $file")
			}
			if (format != null) {
				try {
					return format.getLong(MediaFormat.KEY_DURATION)
				} catch (e: Exception) {
					Timber.e(e)
				}
			}
		} catch (e: IOException) {
			Timber.e(e)
		}
		return -1
	}

	/**
	 * 把 PopupMenu 菜单项的图标移入标题文本（Spannable），使其正常显示。
	 */
	@JvmStatic
	fun insertMenuItemIcons(context: Context, popupMenu: PopupMenu) {
		val menu = popupMenu.menu
		if (hasIcon(menu)) {
			for (i in 0 until menu.size()) {
				insertMenuItemIcon(context, menu.getItem(i))
			}
		}
	}

	/**
	 * @return 菜单中至少有一个带图标的菜单项时为 true
	 */
	private fun hasIcon(menu: Menu): Boolean {
		for (i in 0 until menu.size()) {
			if (menu.getItem(i).icon != null) return true
		}
		return false
	}

	/**
	 * 把给定菜单项的标题转换为同时包含图标与文字的 Spannable。
	 */
	private fun insertMenuItemIcon(context: Context, menuItem: MenuItem) {
		var icon = menuItem.icon
			?: ColorDrawable(Color.TRANSPARENT) //无图标时插入透明图标，保持与有图标项的标题对齐

		val iconSize = context.resources.getDimensionPixelSize(R.dimen.menu_item_icon_size)
		icon.setBounds(0, 0, iconSize, iconSize)
		val imageSpan = ImageSpan(icon)

		//在标题前加空格占位
		val ssb = SpannableStringBuilder("      " + menuItem.title)

		//把空格占位替换为图标
		ssb.setSpan(imageSpan, 1, 2, 0)
		menuItem.title = ssb
		//某些定制系统会在菜单里显示图标，这里置空避免出现两个图标
		menuItem.icon = null
	}

	@JvmStatic
	fun shareAudioFile(context: Context, sharePath: String?, name: String, format: String) {
		if (sharePath != null) {
			val fileUri = FileProvider.getUriForFile(
					context,
					context.applicationContext.packageName + ".app_file_provider",
					File(sharePath)
			)
			val share = Intent(Intent.ACTION_SEND)
			share.type = "audio/$format"
			share.putExtra(Intent.EXTRA_STREAM, fileUri)
			share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

			val chooser = Intent.createChooser(share, context.resources.getString(R.string.share_record, name))
			chooser.flags = Intent.FLAG_ACTIVITY_NEW_TASK
			context.startActivity(chooser)
		} else {
			Timber.e("There no record selected!")
			Toast.makeText(context, R.string.please_select_record_to_share, Toast.LENGTH_LONG).show()
		}
	}

	@JvmStatic
	fun shareAudioFiles(context: Context, list: List<String>) {
		val intent = Intent()
		intent.action = Intent.ACTION_SEND_MULTIPLE
		intent.type = "audio/*"
		intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

		val files = ArrayList<Uri>()
		for (path in list) {
			val uri = FileProvider.getUriForFile(
					context,
					context.applicationContext.packageName + ".app_file_provider",
					File(path)
			)
			files.add(uri)
		}
		intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, files)
		val text = context.resources.getQuantityString(R.plurals.share_records_count, list.size, list.size)
		val chooser = Intent.createChooser(intent, text)
		chooser.flags = Intent.FLAG_ACTIVITY_NEW_TASK
		context.startActivity(chooser)
	}

	@JvmStatic
	fun openAudioFile(context: Context, sharePath: String?, name: String) {
		if (sharePath != null) {
			val fileUri = FileProvider.getUriForFile(
					context,
					context.applicationContext.packageName + ".app_file_provider",
					File(sharePath)
			)
			val intent = Intent(Intent.ACTION_VIEW)
			intent.setDataAndType(fileUri, "audio/*")
			intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

			val chooser = Intent.createChooser(intent, context.resources.getString(R.string.open_record_with, name))
			chooser.flags = Intent.FLAG_ACTIVITY_NEW_TASK
			context.startActivity(chooser)
		} else {
			Timber.e("There no record selected!")
			Toast.makeText(context, R.string.error_unknown, Toast.LENGTH_LONG).show()
		}
	}

	@JvmStatic
	fun showRecordFileNotAvailable(activity: Activity, path: String?) {
		val details = activity.getString(R.string.details_file_is_not_available, path ?: "")
		val spannable = SpannableString(details)
		if (path != null) {
			spannable.setSpan(
					StyleSpan(Typeface.BOLD),
					details.length - path.length, spannable.length,
					Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
			)
		}

		showDialog(activity,
				activity.getString(R.string.title_file_is_not_available),
				spannable,
				{ }, null
		)
	}

	@JvmStatic
	fun showDialog(activity: Activity, strTitle: String, strContent: CharSequence,
					positiveBtnListener: View.OnClickListener?, negativeBtnListener: View.OnClickListener?) {
		showDialog(activity,
				-1,
				activity.getString(R.string.btn_ok),
				null,
				strTitle,
				strContent,
				-1,
				true,
				positiveBtnListener,
				negativeBtnListener)
	}

	@JvmStatic
	fun showDialog(activity: Activity, resTitle: Int, resContent: Int,
					positiveBtnListener: View.OnClickListener?, negativeBtnListener: View.OnClickListener?) {
		showDialog(activity, -1, R.string.btn_ok, -1, resTitle, resContent, false, positiveBtnListener, negativeBtnListener)
	}

	@JvmStatic
	fun showDialog(activity: Activity,
					drawableRes: Int,
					positiveBtnTextRes: Int,
					negativeBtnTextRes: Int,
					resTitle: Int,
					resContent: Int,
					cancelable: Boolean,
					positiveBtnListener: View.OnClickListener?,
					negativeBtnListener: View.OnClickListener?) {
		showDialog(activity,
				drawableRes,
				if (positiveBtnTextRes > 0) activity.getString(positiveBtnTextRes) else null,
				if (negativeBtnTextRes > 0) activity.getString(negativeBtnTextRes) else null,
				if (resTitle > 0) activity.getString(resTitle) else null,
				if (resContent > 0) activity.getString(resContent) else null,
				-1,
				cancelable,
				positiveBtnListener,
				negativeBtnListener)
	}

	@JvmStatic
	fun showDialogYesNo(activity: Activity,
						drawableRes: Int,
						titleStr: String,
						contentStr: String,
						positiveBtnListener: View.OnClickListener) {
		showDialog(activity,
				drawableRes,
				activity.getString(R.string.btn_yes),
				activity.getString(R.string.btn_no),
				titleStr,
				contentStr,
				-1,
				true,
				positiveBtnListener,
				{ })
	}

	private fun showDialog(activity: Activity,
							drawableRes: Int,
							positiveBtnText: String?,
							negativeBtnText: String?,
							titleStr: CharSequence?,
							contentStr: CharSequence?,
							contentResId: Int,
							cancelable: Boolean,
							positiveBtnListener: View.OnClickListener?,
							negativeBtnListener: View.OnClickListener?) {
		val dialogBuilder = AlertDialog.Builder(activity)
		dialogBuilder.setCancelable(cancelable)
		val view = activity.layoutInflater.inflate(R.layout.dialog_layout, null, false)
		val title = view.findViewById<TextView>(R.id.dialog_title)
		title.text = titleStr
		if (drawableRes > 0) {
			title.setCompoundDrawablesRelativeWithIntrinsicBounds(drawableRes, 0, 0, 0)
		}
		if (contentResId > 0) {
			view.findViewById<TextView>(R.id.dialog_content).text =
					Html.fromHtml(activity.getString(contentResId), Html.FROM_HTML_MODE_COMPACT)
		} else {
			view.findViewById<TextView>(R.id.dialog_content).text = contentStr
		}
		dialogBuilder.setView(view)
		val alertDialog = dialogBuilder.create()
		if (negativeBtnListener != null) {
			val negativeBtn = view.findViewById<Button>(R.id.dialog_negative_btn)
			if (negativeBtnText != null) {
				negativeBtn.text = negativeBtnText
			}
			negativeBtn.setOnClickListener { v ->
				negativeBtnListener.onClick(v)
				alertDialog.dismiss()
			}
		} else {
			view.findViewById<View>(R.id.dialog_negative_btn).visibility = View.GONE
		}
		if (positiveBtnListener != null) {
			val positiveBtn = view.findViewById<Button>(R.id.dialog_positive_btn)
			if (positiveBtnText != null) {
				positiveBtn.text = positiveBtnText
			}
			positiveBtn.setOnClickListener { v ->
				positiveBtnListener.onClick(v)
				alertDialog.dismiss()
			}
		} else {
			view.findViewById<View>(R.id.dialog_positive_btn).visibility = View.GONE
		}
		alertDialog.show()
	}

	@JvmStatic
	fun showRenameDialog(activity: Activity,
						name: String,
						showCheckbox: Boolean,
						positiveBtnListener: OnSetNameClickListener?,
						negativeBtnListener: View.OnClickListener?,
						checkListener: CompoundButton.OnCheckedChangeListener?) {
		val dialogBuilder = AlertDialog.Builder(activity)
		dialogBuilder.setCancelable(true)
		val view = activity.layoutInflater.inflate(R.layout.dialog_rename, null, false)
		val checkBox = view.findViewById<CheckBox>(R.id.check_box)
		checkBox.visibility = if (showCheckbox) View.VISIBLE else View.GONE
		if (checkListener != null) {
			checkBox.setOnCheckedChangeListener(checkListener)
		}
		val editText = view.findViewById<EditText>(R.id.input_name)
		editText.setText(name)
		editText.requestFocus()
		editText.setSelection(editText.text.length)
		editText.addTextChangedListener(object : TextWatcher {
			override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

			override fun afterTextChanged(s: Editable) {
				if (s.length > AppConstants.MAX_RECORD_NAME_LENGTH) {
					s.delete(s.length - 1, s.length)
				}
			}

			override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
		})

		dialogBuilder.setView(view)
		val alertDialog = dialogBuilder.create()
		if (negativeBtnListener != null) {
			val negativeBtn = view.findViewById<Button>(R.id.dialog_negative_btn)
			negativeBtn.setOnClickListener { v ->
				negativeBtnListener.onClick(v)
				alertDialog.dismiss()
			}
		} else {
			view.findViewById<View>(R.id.dialog_negative_btn).visibility = View.GONE
		}
		if (positiveBtnListener != null) {
			val positiveBtn = view.findViewById<Button>(R.id.dialog_positive_btn)
			positiveBtn.setOnClickListener {
				positiveBtnListener.onClick(editText.text.toString())
				alertDialog.dismiss()
			}
		} else {
			view.findViewById<View>(R.id.dialog_positive_btn).visibility = View.GONE
		}
		val window: Window? = alertDialog.window
		window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
		alertDialog.show()
	}

	@JvmStatic
	fun showInfoDialog(activity: Activity, resContent: Int) {
		showDialog(
				activity, -1, activity.getString(R.string.btn_ok), null,
				activity.getString(R.string.info), "", resContent, true, { }, null
		)
	}

	@JvmStatic
	fun showLostRecordsDialog(activity: Activity, lostRecords: List<Record>) {
		if (!activity.isFinishing) {
			val dialog = Dialog(activity)
			dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
			dialog.setCancelable(false)
			val view = activity.layoutInflater.inflate(R.layout.dialog_lost_records_layout, null, false)
			val negativeBtn = view.findViewById<Button>(R.id.dialog_ok_btn)
			negativeBtn.setOnClickListener { dialog.dismiss() }
			val positiveBtn = view.findViewById<Button>(R.id.dialog_details_btn)
			positiveBtn.setOnClickListener {
				activity.startActivity(LostRecordsActivity.getStartIntent(
						activity.applicationContext,
						Mapper.toRecordItemList(lostRecords))
				)
				dialog.dismiss()
			}
			dialog.setContentView(view)
			dialog.show()
		}
	}

	@JvmStatic
	fun getAppVersion(context: Context): String {
		var versionName: String
		try {
			val info = context.packageManager.getPackageInfo(context.packageName, 0)
			versionName = info.versionName ?: "N/A"
		} catch (e: PackageManager.NameNotFoundException) {
			versionName = "N/A"
		}
		return versionName
	}

	fun interface OnSetNameClickListener {
		fun onClick(name: String)
	}
}
