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

package com.vdo.frecorder.app.settings

import android.app.Activity
import android.content.res.Resources
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import com.vdo.frecorder.R

/** 下拉项（名称 + 色值）。 */
class ThemeItem(val colorName: String, val color: Int)

/** 主题色/命名格式下拉框适配器。Kotlin 化（原 AppSpinnerAdapter.java）。 */
class AppSpinnerAdapter(
		context: Activity,
		res: Int,
		txtRes: Int,
		private val data: List<ThemeItem>,
		private val iconRes: Int
) : ArrayAdapter<ThemeItem>(context, res, txtRes, data) {

	private val inflater: LayoutInflater = context.layoutInflater

	override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
		return getView(convertView, position, parent, true)
	}

	override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
		return getView(convertView, position, parent, false)
	}

	private fun getView(convertView: View?, position: Int, parent: ViewGroup, showDrawable: Boolean): View {
		val view = convertView ?: inflater.inflate(R.layout.list_item_spinner, parent, false)
		val txtColor = view.findViewById<TextView>(R.id.txtItem)
		txtColor.text = data[position].colorName
		if (!showDrawable) {
			txtColor.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0)
			val r = context.resources
			val n = r.getDimension(R.dimen.spacing_xsmall)
			txtColor.setPadding(r.getDimension(R.dimen.spacing_xxdouble).toInt(), n.toInt(), n.toInt(), n.toInt())
			txtColor.setBackgroundColor(data[position].color)
		} else {
			@Suppress("DEPRECATION")
			txtColor.setBackgroundColor(context.resources.getColor(R.color.transparent))
			txtColor.setCompoundDrawablesRelativeWithIntrinsicBounds(iconRes, 0, 0, 0)
			if (iconRes <= 0) {
				val r = context.resources
				val n = r.getDimension(R.dimen.spacing_normal)
				txtColor.setPadding(r.getDimension(R.dimen.spacing_xxdouble).toInt(), n.toInt(), n.toInt(), n.toInt())
			}
		}
		return txtColor
	}
}
