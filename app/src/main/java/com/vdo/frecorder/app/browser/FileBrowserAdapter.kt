/*
 * Copyright 2020 Dmytro Ponomarenko
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

package com.vdo.frecorder.app.browser

import android.content.Context
import android.content.res.Resources
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.NonNull
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.vdo.frecorder.AppConstants
import com.vdo.frecorder.R
import com.vdo.frecorder.app.info.RecordInfo
import com.vdo.frecorder.app.settings.SettingsMapper
import com.vdo.frecorder.util.RippleUtils
import com.vdo.frecorder.util.TimeUtils

/** 文件浏览器列表适配器。Kotlin 化（原 FileBrowserAdapter.java）。 */
class FileBrowserAdapter(context: Context, private val settingsMapper: SettingsMapper) :
		RecyclerView.Adapter<FileBrowserAdapter.ItemViewHolder>() {

	private val data: MutableList<RecordInfo> = ArrayList()
	private var onItemClickListener: OnItemClickListener? = null
	private var colorInTrash: Int
	private var colorFound: Int
	private var colorNotFound: Int
	private var radius: Int

	init {
		try {
			radius = context.resources.getDimension(R.dimen.spacing_tiny).toInt()
			colorInTrash = ContextCompat.getColor(context, R.color.md_yellow_800F)
			colorFound = ContextCompat.getColor(context, R.color.md_green_600)
			colorNotFound = ContextCompat.getColor(context, R.color.md_red_700)
		} catch (e: Resources.NotFoundException) {
			radius = 8
			colorInTrash = Color.YELLOW
			colorFound = Color.GREEN
			colorNotFound = Color.RED
		}
	}

	fun setData(list: List<RecordInfo>) {
		if (data.isNotEmpty()) {
			data.clear()
		}
		data.addAll(list)
		notifyDataSetChanged()
	}

	fun removeItem(path: String) {
		var pos = -1
		for (i in data.indices) {
			if (path == data[i].location) {
				pos = i
			}
		}
		if (pos >= 0 && pos < data.size) {
			data.removeAt(pos)
			notifyItemRemoved(pos)
			//这行会触发删除动画，并刷新被删项之后的列表项
			notifyItemRangeChanged(pos, itemCount)
		}
	}

	fun setRecordInDatabase(path: String) {
		var pos = -1
		for (i in data.indices) {
			if (path == data[i].location) {
				pos = i
			}
		}
		if (pos >= 0 && pos < data.size) {
			data[pos].isInDatabase = true
			notifyItemChanged(pos)
		}
	}

	fun clearData() {
		data.clear()
		notifyDataSetChanged()
	}

	fun getData(): List<RecordInfo> = data

	@NonNull
	override fun onCreateViewHolder(@NonNull viewGroup: ViewGroup, i: Int): ItemViewHolder {
		val v = LayoutInflater.from(viewGroup.context).inflate(R.layout.list_item_file_browser, viewGroup, false)
		return ItemViewHolder(v)
	}

	override fun onBindViewHolder(@NonNull holder: ItemViewHolder, position: Int) {
		val pos = holder.absoluteAdapterPosition
		if (pos != RecyclerView.NO_POSITION) {
			val rec = data[pos]
			holder.name.text = rec.name
			if (rec.isInTrash) {
				holder.status.setText(R.string.in_trash)
				holder.status.background = RippleUtils.createShape(colorInTrash, radius.toFloat())
			} else if (!rec.isInDatabase) {
				holder.status.setText(R.string.not_found_in_the_app)
				holder.status.background = RippleUtils.createShape(colorNotFound, radius.toFloat())
			} else {
				holder.status.setText(R.string.found_in_the_app)
				holder.status.background = RippleUtils.createShape(colorFound, radius.toFloat())
			}
			updateInformation(holder.info, rec.format, rec.sampleRate, rec.size, rec.duration / 1000, rec.bitrate)
			if (rec.isInDatabase || rec.isInTrash) {
				holder.btnDelete.visibility = View.GONE
				holder.btnImport.visibility = View.GONE
			} else {
				holder.btnImport.visibility = View.VISIBLE
				holder.btnDelete.visibility = View.VISIBLE
			}
			holder.view.setOnClickListener {
				if (onItemClickListener != null) {
					onItemClickListener!!.onItemClick(data[pos])
				}
			}
			holder.btnImport.setOnClickListener {
				if (onItemClickListener != null) {
					onItemClickListener!!.onImportItemClick(data[pos])
				}
			}
			holder.btnDownload.setOnClickListener {
				if (onItemClickListener != null) {
					onItemClickListener!!.onSaveAsItemClick(data[pos])
				}
			}
			holder.btnDelete.setOnClickListener {
				if (onItemClickListener != null) {
					onItemClickListener!!.onRemoveItemClick(data[pos])
				}
			}
		}
	}

	override fun getItemCount(): Int = data.size

	private fun updateInformation(view: TextView, format: String, sampleRate: Int, size: Long, duration: Long, bitrate: Int) {
		var info = settingsMapper.formatSize(size) + AppConstants.SEPARATOR +
				settingsMapper.convertFormatsToString(format) + AppConstants.SEPARATOR +
				settingsMapper.convertSampleRateToString(sampleRate)
		if (AppConstants.FORMAT_MP3 == format && bitrate > 0) {
			info += AppConstants.SEPARATOR + settingsMapper.formatBitrate(bitrate / 1000)
		}
		info += AppConstants.SEPARATOR + TimeUtils.formatTimeIntervalHourMinSec2(duration)
		view.text = info
	}

	fun setOnItemClickListener(onItemClickListener: OnItemClickListener?) {
		this.onItemClickListener = onItemClickListener
	}

	class ItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
		val view: View = itemView
		val name: TextView = itemView.findViewById(R.id.list_item_name)
		val info: TextView = itemView.findViewById(R.id.list_item_info)
		val status: TextView = itemView.findViewById(R.id.list_item_status)
		val actionPanel: LinearLayout = itemView.findViewById(R.id.list_item_action_panel)
		val btnImport: TextView = itemView.findViewById(R.id.list_item_btn_import)
		val btnDownload: TextView = itemView.findViewById(R.id.list_item_btn_download)
		val btnDelete: TextView = itemView.findViewById(R.id.list_item_btn_delete)

		init {
			btnImport.background = RippleUtils.createRippleShape(
					ContextCompat.getColor(btnDelete.context, R.color.white_transparent_80),
					ContextCompat.getColor(btnDelete.context, R.color.white_transparent_50),
					btnImport.context.resources.getDimension(R.dimen.spacing_normal)
			)
			btnDownload.background = RippleUtils.createRippleShape(
					ContextCompat.getColor(btnDelete.context, R.color.white_transparent_80),
					ContextCompat.getColor(btnDelete.context, R.color.white_transparent_50),
					btnDownload.context.resources.getDimension(R.dimen.spacing_normal)
			)
			btnDelete.background = RippleUtils.createRippleShape(
					ContextCompat.getColor(btnDelete.context, R.color.white_transparent_80),
					ContextCompat.getColor(btnDelete.context, R.color.white_transparent_50),
					btnDelete.context.resources.getDimension(R.dimen.spacing_normal)
			)
		}
	}

	interface OnItemClickListener {
		fun onItemClick(record: RecordInfo)
		fun onImportItemClick(record: RecordInfo)
		fun onSaveAsItemClick(record: RecordInfo)
		fun onRemoveItemClick(record: RecordInfo)
	}
}
