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

package com.vdo.frecorder.app.records

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.annotation.NonNull
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.vdo.frecorder.AppConstants
import com.vdo.frecorder.R
import com.vdo.frecorder.app.settings.SettingsMapper
import com.vdo.frecorder.app.widget.SimpleWaveformView
import com.vdo.frecorder.util.AndroidUtils
import com.vdo.frecorder.util.TimeUtils
import java.util.ArrayList
import java.util.Calendar

/** 录音列表适配器（含日期分组头、多选、回收站入口）。Kotlin 化（原 RecordsAdapter.java）。 */
class RecordsAdapter(private val settingsMapper: SettingsMapper) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

	private var data: MutableList<ListItem> = ArrayList()
	private val selected: MutableList<Int> = ArrayList()
	private var isMultiSelectMode = false

	private var showDateHeaders = true
	private var activeItem = -1
	private var btnTrash: View? = null
	private var showTrash = false

	private var itemClickListener: ItemClickListener? = null
	private var btnTrashClickListener: BtnTrashClickListener? = null
	private var onAddToBookmarkListener: OnAddToBookmarkListener? = null
	private var onItemOptionListener: OnItemOptionListener? = null
	private var onMultiSelectModeListener: OnMultiSelectModeListener? = null

	@NonNull
	override fun onCreateViewHolder(@NonNull viewGroup: ViewGroup, type: Int): RecyclerView.ViewHolder {
		if (type == ListItem.ITEM_TYPE_HEADER) {
			return UniversalViewHolder(createHeaderView(viewGroup.context))
		} else if (type == ListItem.ITEM_TYPE_FOOTER) {
			val view = View(viewGroup.context)
			val height = viewGroup.context.resources.getDimension(R.dimen.panel_height).toInt()

			val lp = LinearLayout.LayoutParams(
					LinearLayout.LayoutParams.MATCH_PARENT, height)
			view.layoutParams = lp
			return UniversalViewHolder(view)
		} else if (type == ListItem.ITEM_TYPE_DATE) {
			//日期分组头，纯代码构建
			val textView = TextView(viewGroup.context)
			val lp = ViewGroup.LayoutParams(
					ViewGroup.LayoutParams.MATCH_PARENT,
					ViewGroup.LayoutParams.WRAP_CONTENT)
			textView.layoutParams = lp
			textView.setTypeface(textView.typeface, Typeface.BOLD)

			textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, viewGroup.context.resources.getDimension(R.dimen.text_medium))

			val pad = viewGroup.context.resources.getDimension(R.dimen.spacing_small).toInt()
			textView.setPadding(pad, pad, pad, pad)
			textView.gravity = Gravity.CENTER

			return UniversalViewHolder(textView)
		} else {
			val v = LayoutInflater.from(viewGroup.context).inflate(R.layout.list_item, viewGroup, false)
			return ItemViewHolder(v, OnItemClickListener { position ->
				if (isMultiSelectMode) {
					if (!selected.contains(position) && data[position].duration != 0L) {
						selected.add(position)
					} else {
						val pos = selected.indexOf(position)
						if (pos != -1) {
							selected.removeAt(pos)
							if (selected.isEmpty()) {
								isMultiSelectMode = !isMultiSelectMode
								notifyDataSetChanged()
								onMultiSelectModeListener?.onMultiSelectMode(false)
							}
						}
					}
					onMultiSelectModeListener?.onSelectDeselect(selected.size)
					notifyItemChanged(position)
				} else {
					if (itemClickListener != null && data.size > position) {
						itemClickListener!!.onItemClick(v, data[position].id, data[position].path, position)
					}
				}
			}, OnItemLongClickListener { position ->
				isMultiSelectMode = !isMultiSelectMode
				notifyDataSetChanged()
				onMultiSelectModeListener?.onMultiSelectMode(isMultiSelectMode)
				if (isMultiSelectMode) {
					if (!selected.contains(position) && data[position].duration != 0L) {
						selected.add(position)
						notifyItemChanged(position)
					}
				} else {
					selected.clear()
					onMultiSelectModeListener?.onMultiSelectMode(false)
					notifyDataSetChanged()
				}
				onMultiSelectModeListener?.onSelectDeselect(selected.size)
			})
		}
	}

	override fun onBindViewHolder(@NonNull viewHolder: RecyclerView.ViewHolder, pos: Int) {
		if (viewHolder.itemViewType == ListItem.ITEM_TYPE_NORMAL) {
			val holder = viewHolder as ItemViewHolder
			val p = holder.absoluteAdapterPosition
			val item = data[p]
			holder.name.text = item.name
			holder.description.text = item.durationStr
			holder.created.text = item.addedTimeStr
			if (item.isBookmarked) {
				holder.btnBookmark.setImageResource(R.drawable.ic_bookmark_small)
			} else {
				holder.btnBookmark.setImageResource(R.drawable.ic_bookmark_bordered_small)
			}
			if (viewHolder.layoutPosition == activeItem) {
				holder.view.setBackgroundResource(R.color.selected_item_color)
			} else {
				if (selected.contains(p)) {
					holder.view.setBackgroundResource(R.color.selected_item_color)
				} else {
					holder.view.setBackgroundResource(android.R.color.transparent)
				}
			}

			holder.btnBookmark.setOnClickListener {
				if (onAddToBookmarkListener != null && data.size > p) {
					if (item.isBookmarked) {
						onAddToBookmarkListener!!.onRemoveFromBookmarks(item.id.toInt())
					} else {
						onAddToBookmarkListener!!.onAddToBookmarks(item.id.toInt())
					}
				}
			}
			holder.btnMore.setOnClickListener { showMenu(it, p) }
			holder.waveformView.setWaveform(item.amps)
			if (isMultiSelectMode || item.duration == 0L) {
				holder.btnMore.visibility = View.GONE
			} else {
				holder.btnMore.visibility = View.VISIBLE
			}
			updateInformation(holder.info, item.format, item.sampleRate, item.size, item.bitrate)
		} else if (viewHolder.itemViewType == ListItem.ITEM_TYPE_DATE) {
			val holder = viewHolder as UniversalViewHolder
			(holder.view as TextView).text =
					TimeUtils.formatDateSmartLocale(
							data[viewHolder.absoluteAdapterPosition].added,
							holder.view.context
					)
		}
	}

	override fun onViewAttachedToWindow(@NonNull holder: RecyclerView.ViewHolder) {
		super.onViewAttachedToWindow(holder)
		if (holder.itemViewType == ListItem.ITEM_TYPE_HEADER) {
			btnTrash = holder.itemView.findViewById(R.id.btn_trash)
			if (btnTrash != null) {
				if (btnTrashClickListener != null) {
					btnTrash!!.setOnClickListener { btnTrashClickListener!!.onClick() }
				}
				if (showTrash) {
					btnTrash!!.visibility = View.VISIBLE
				} else {
					btnTrash!!.visibility = View.GONE
				}
			}
		}
	}

	override fun onViewDetachedFromWindow(@NonNull holder: RecyclerView.ViewHolder) {
		super.onViewDetachedFromWindow(holder)
		if (holder.itemViewType == ListItem.ITEM_TYPE_HEADER) {
			btnTrash = null
		}
	}

	fun showTrash(show: Boolean) {
		showTrash = show
		if (btnTrash != null) {
			if (showTrash) {
				btnTrash!!.visibility = View.VISIBLE
			} else {
				btnTrash!!.visibility = View.GONE
			}
		}
	}

	private fun showMenu(v: View, pos: Int) {
		val popup = PopupMenu(v.context, v)
		popup.setOnMenuItemClickListener { item ->
			if (onItemOptionListener != null && data.size > pos) {
				onItemOptionListener!!.onItemOptionSelected(item.itemId, data[pos])
			}
			false
		}
		val inflater: MenuInflater = popup.menuInflater
		inflater.inflate(R.menu.menu_more, popup.menu)
		AndroidUtils.insertMenuItemIcons(v.context, popup)
		popup.show()
	}

	fun setActiveItem(activeItem: Int) {
		val prev = this.activeItem
		this.activeItem = activeItem
		notifyItemChanged(prev)
		notifyItemChanged(activeItem)
	}

	fun findPositionById(id: Long): Int {
		if (id >= 0) {
			for (i in data.indices) {
				if (data[i].id == id) {
					return i
				}
			}
		}
		return -1
	}

	override fun getItemCount(): Int = data.size

	override fun getItemViewType(position: Int): Int = data[position].type

	fun setData(d: List<ListItem>, order: Int) {
		updateShowHeader(order)
		data = if (showDateHeaders) {
			addDateHeaders(d.toMutableList())
		} else {
			d.toMutableList()
		}
		data.add(0, ListItem.createHeaderItem())
		notifyDataSetChanged()
	}

	fun addData(d: List<ListItem>, order: Int) {
		if (data.isNotEmpty()) {
			updateShowHeader(order)
			if (showDateHeaders) {
				if (findFooter() >= 0) {
					data.addAll(data.size - 1, addDateHeaders(d.toMutableList()))
				} else {
					data.addAll(addDateHeaders(d.toMutableList()))
				}
			} else {
				if (findFooter() >= 0) {
					data.addAll(data.size - 1, d)
				} else {
					data.addAll(d)
				}
			}
			notifyItemRangeInserted(data.size - d.size, d.size)
		}
	}

	private fun updateShowHeader(order: Int) {
		showDateHeaders = order == AppConstants.SORT_DATE || order == AppConstants.SORT_DATE_DESC
	}

	fun getItem(pos: Int): ListItem = data[pos]

	private fun addDateHeaders(data: MutableList<ListItem>): MutableList<ListItem> {
		if (data.isNotEmpty()) {
			if (!hasDateHeader(data, data[0].added)) {
				data.add(0, ListItem.createDateItem(data[0].added))
			}
			val d1 = Calendar.getInstance()
			d1.timeInMillis = data[0].added
			val d2 = Calendar.getInstance()
			var i = 1
			while (i < data.size) {
				d1.timeInMillis = data[i - 1].added
				d2.timeInMillis = data[i].added
				if (!TimeUtils.isSameDay(d1, d2) && !hasDateHeader(data, data[i].added)) {
					data.add(i, ListItem.createDateItem(data[i].added))
				}
				i++
			}
		}
		return data
	}

	fun deleteItem(id: Long) {
		var i = 0
		while (i < data.size) {
			if (data[i].id == id) {
				data.removeAt(i)
				notifyItemRemoved(i)
				//这行会触发删除动画，并刷新被删项之后的列表项
				notifyItemRangeChanged(i, itemCount)
				break
			}
			i++
		}
	}

	fun getAudioRecordsCount(): Int {
		var count = 0
		for (i in data.indices) {
			if (data[i].type == ListItem.ITEM_TYPE_NORMAL) {
				count++
			}
		}
		return count
	}

	fun showFooter() {
		if (findFooter() == -1) {
			data.add(ListItem.createFooterItem())
			notifyItemInserted(data.size - 1)
		}
	}

	fun hideFooter() {
		val pos = findFooter()
		if (pos != -1) {
			data.removeAt(pos)
			notifyItemRemoved(pos)
		}
	}

	fun getNextTo(id: Long): Long {
		if (id >= 0) {
			var i = 0
			while (i < data.size - 1) {
				if (data[i].id == id) {
					return if (data[i + 1].id == -1L && i + 2 < data.size) {
						data[i + 2].id
					} else {
						data[i + 1].id
					}
				}
				i++
			}
		}
		return -1
	}

	fun getPrevTo(id: Long): Long {
		if (id >= 0) {
			for (i in 1 until data.size) {
				if (data[i].id == id) {
					return if (data[i - 1].id == -1L && i - 2 >= 0) {
						data[i - 2].id
					} else {
						data[i - 1].id
					}
				}
			}
		}
		return -1
	}

	private fun findFooter(): Int {
		for (i in data.size - 1 downTo 0) {
			if (data[i].type == ListItem.ITEM_TYPE_FOOTER) {
				return i
			}
		}
		return -1
	}

	fun markAddedToBookmarks(id: Int) {
		for (i in data.indices) {
			if (data[i].id.toInt() == id) {
				data[i].isBookmarked = true
				notifyItemChanged(i)
			}
		}
	}

	fun markRemovedFromBookmarks(id: Int) {
		for (i in data.indices) {
			if (data[i].id.toInt() == id) {
				data[i].isBookmarked = false
				notifyItemChanged(i)
			}
		}
	}

	private fun updateInformation(view: TextView, format: String, sampleRate: Int, size: Long, bitrate: Int) {
		var info = settingsMapper.formatSize(size) + AppConstants.SEPARATOR +
				settingsMapper.convertFormatsToString(format) + AppConstants.SEPARATOR +
				settingsMapper.convertSampleRateToString(sampleRate)
		if (AppConstants.FORMAT_MP3 == format && bitrate > 0) {
			info += AppConstants.SEPARATOR + settingsMapper.formatBitrate(bitrate / 1000)
		}
		view.text = info
	}

	private fun hasDateHeader(data: List<ListItem>, time: Long): Boolean {
		for (i in data.size - 1 downTo 0) {
			if (data[i].type == ListItem.ITEM_TYPE_DATE) {
				val d1 = Calendar.getInstance()
				d1.timeInMillis = data[i].added
				val d2 = Calendar.getInstance()
				d2.timeInMillis = time
				if (TimeUtils.isSameDay(d1, d2)) {
					return true
				}
			}
		}
		return false
	}

	private fun createHeaderView(context: Context): View {
		val container = LinearLayout(context)
		container.orientation = LinearLayout.VERTICAL

		val headerView = View(context)
		val height = context.resources.getDimension(R.dimen.toolbar_height).toInt()
		val headerParams = LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT, height)
		headerView.layoutParams = headerParams

		val btnLayout = LinearLayout(context)
		btnLayout.orientation = LinearLayout.VERTICAL
		btnLayout.id = R.id.btn_trash
		val frameParams = ViewGroup.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.WRAP_CONTENT)
		btnLayout.layoutParams = frameParams
		btnLayout.isClickable = true
		btnLayout.isFocusable = true
		btnLayout.setBackgroundResource(R.drawable.button_translcent)

		val pad = context.resources.getDimension(R.dimen.spacing_normal).toInt()
		val medium = context.resources.getDimension(R.dimen.spacing_medium).toInt()
		val buttonTrash = TextView(context)
		val btnParams = ViewGroup.LayoutParams(
				ViewGroup.LayoutParams.WRAP_CONTENT,
				ViewGroup.LayoutParams.WRAP_CONTENT)
		buttonTrash.layoutParams = btnParams
		buttonTrash.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
		buttonTrash.setText(R.string.trash)
		buttonTrash.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_delete, 0, 0, 0)
		buttonTrash.compoundDrawablePadding = pad * 2
		buttonTrash.setTextSize(TypedValue.COMPLEX_UNIT_PX, context.resources.getDimension(R.dimen.text_large))
		buttonTrash.setTextColor(ContextCompat.getColor(context, R.color.white))
		buttonTrash.setPadding(pad, medium, pad, medium)
		buttonTrash.gravity = Gravity.CENTER

		val dividerColor = ContextCompat.getColor(context, R.color.divider)
		val dividerSize = context.resources.getDimension(R.dimen.divider).toInt()

		val dividerView = View(context)
		val dividerParams = ViewGroup.LayoutParams(
				ViewGroup.LayoutParams.WRAP_CONTENT,
				dividerSize)
		dividerView.layoutParams = dividerParams
		dividerView.setBackgroundColor(dividerColor)

		val dividerView2 = View(context)
		val dividerParams2 = ViewGroup.LayoutParams(
				ViewGroup.LayoutParams.WRAP_CONTENT,
				dividerSize)
		dividerView2.layoutParams = dividerParams2
		dividerView2.setBackgroundColor(dividerColor)

		btnLayout.addView(dividerView)
		btnLayout.addView(buttonTrash)
		btnLayout.addView(dividerView2)

		container.addView(headerView)
		container.addView(btnLayout)
		return container
	}

	fun getSelected(): List<Int> = ArrayList(selected)

	fun cancelMultiSelect() {
		selected.clear()
		isMultiSelectMode = false
		notifyDataSetChanged()
		onMultiSelectModeListener?.onMultiSelectMode(false)
	}

	fun setBtnTrashClickListener(btnTrashClickListener: BtnTrashClickListener?) {
		this.btnTrashClickListener = btnTrashClickListener
	}

	fun setItemClickListener(itemClickListener: ItemClickListener?) {
		this.itemClickListener = itemClickListener
	}

	fun setOnAddToBookmarkListener(onAddToBookmarkListener: OnAddToBookmarkListener?) {
		this.onAddToBookmarkListener = onAddToBookmarkListener
	}

	fun interface ItemClickListener {
		fun onItemClick(view: View, id: Long, path: String, position: Int)
	}

	fun interface BtnTrashClickListener {
		fun onClick()
	}

	fun setOnItemOptionListener(onItemOptionListener: OnItemOptionListener?) {
		this.onItemOptionListener = onItemOptionListener
	}

	fun setOnMultiSelectModeListener(listener: OnMultiSelectModeListener?) {
		this.onMultiSelectModeListener = listener
	}

	interface OnAddToBookmarkListener {
		fun onAddToBookmarks(id: Int)
		fun onRemoveFromBookmarks(id: Int)
	}

	fun interface OnItemOptionListener {
		fun onItemOptionSelected(menuId: Int, item: ListItem)
	}

	class ItemViewHolder(
			itemView: View,
			onItemClickListener: OnItemClickListener?,
			longClickListener: OnItemLongClickListener?
	) : RecyclerView.ViewHolder(itemView) {
		val view: View = itemView
		val name: TextView = itemView.findViewById(R.id.list_item_name)
		val description: TextView = itemView.findViewById(R.id.list_item_description)
		val created: TextView = itemView.findViewById(R.id.list_item_date)
		val info: TextView = itemView.findViewById(R.id.list_item_info)
		val btnBookmark: ImageButton = itemView.findViewById(R.id.list_item_bookmark)
		val waveformView: SimpleWaveformView = itemView.findViewById(R.id.list_item_waveform)
		val btnMore: ImageButton = itemView.findViewById(R.id.list_item_more)

		init {
			view.setOnClickListener {
				val pos = absoluteAdapterPosition
				if (pos != RecyclerView.NO_POSITION && onItemClickListener != null) {
					onItemClickListener.onItemClick(pos)
				}
			}
			view.setOnLongClickListener {
				val pos = absoluteAdapterPosition
				if (pos != RecyclerView.NO_POSITION && longClickListener != null) {
					longClickListener.onItemLongClick(pos)
				}
				false
			}
		}
	}

	class UniversalViewHolder(val view: View) : RecyclerView.ViewHolder(view)

	fun interface OnItemClickListener {
		fun onItemClick(position: Int)
	}

	fun interface OnItemLongClickListener {
		fun onItemLongClick(position: Int)
	}

	interface OnMultiSelectModeListener {
		fun onMultiSelectMode(selected: Boolean)
		fun onSelectDeselect(selectedCount: Int)
	}
}
