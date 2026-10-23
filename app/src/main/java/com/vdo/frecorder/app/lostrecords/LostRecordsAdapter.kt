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

package com.vdo.frecorder.app.lostrecords

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.annotation.NonNull
import androidx.recyclerview.widget.RecyclerView
import com.vdo.frecorder.R
import com.vdo.frecorder.util.TimeUtils

/** 丢失录音列表适配器。Kotlin 化（原 LostRecordsAdapter.java）。 */
class LostRecordsAdapter : RecyclerView.Adapter<LostRecordsAdapter.ItemViewHolder>() {

	private val data: MutableList<RecordItem> = ArrayList()

	private var onItemClickListener: OnItemClickListener? = null

	fun setData(list: List<RecordItem>) {
		if (data.isNotEmpty()) {
			data.clear()
		}
		data.addAll(list)
		notifyDataSetChanged()
	}

	fun removeItem(id: Int) {
		var pos = -1
		for (i in data.indices) {
			if (id == data[i].id) {
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

	fun clearData() {
		data.clear()
		notifyDataSetChanged()
	}

	fun getData(): List<RecordItem> = ArrayList(data)

	@NonNull
	override fun onCreateViewHolder(@NonNull viewGroup: ViewGroup, i: Int): ItemViewHolder {
		val v = LayoutInflater.from(viewGroup.context).inflate(R.layout.list_item_lost, viewGroup, false)
		return ItemViewHolder(v)
	}

	override fun onBindViewHolder(@NonNull holder: ItemViewHolder, position: Int) {
		val pos = holder.absoluteAdapterPosition
		if (pos != RecyclerView.NO_POSITION) {
			holder.name.text = data[pos].name
			holder.location.text = data[pos].path
			holder.duration.text = TimeUtils.formatTimeIntervalHourMinSec2(data[pos].duration / 1000)
			holder.view.setOnClickListener {
				onItemClickListener?.onItemClick(data[pos])
			}
			holder.btnDelete.setOnClickListener {
				onItemClickListener?.onRemoveItemClick(data[pos])
			}
		}
	}

	override fun getItemCount(): Int = data.size

	fun setOnItemClickListener(onItemClickListener: OnItemClickListener?) {
		this.onItemClickListener = onItemClickListener
	}

	class ItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
		val view: View = itemView
		val name: TextView = itemView.findViewById(R.id.list_item_name)
		val duration: TextView = itemView.findViewById(R.id.list_item_duration)
		val location: TextView = itemView.findViewById(R.id.list_item_location)
		val btnDelete: ImageButton = itemView.findViewById(R.id.list_item_delete)
	}

	interface OnItemClickListener {
		fun onItemClick(record: RecordItem)
		fun onRemoveItemClick(record: RecordItem)
	}
}
