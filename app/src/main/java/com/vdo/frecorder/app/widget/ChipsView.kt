/*
 * Copyright 2020 Dmytro Ponomarenko
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.vdo.frecorder.app.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.vdo.frecorder.R
import com.vdo.frecorder.util.AndroidUtils
import com.vdo.frecorder.util.RippleUtils
import java.util.ArrayList
import java.util.Collections

/** 流式标签选择器（Chips）。Kotlin 化（原 ChipsView.java）。 */
class ChipsView : FrameLayout {

	private val DRAWABLE_WIDTH: Int

	private var chipColor = Color.GREEN
	private var rippleColor = Color.RED
	private var borderColor = Color.WHITE
	private var chipTextSelectedColor = Color.WHITE
	private var chipMargin = 0
	private var rowCount = 0
	private var posY = 0f
	private var orderEffective = true
	private var multiSelect = true
	private var chipsEnabled = true

	private var heightAnimator: ValueAnimator? = null

	private var chipRadius = 0
	private var borderWidth = 0
	private var chipTextSize = 0
	private var chipPadding = 0
	private var drawablePadding = 0
	private var chipTextColor = Color.WHITE

	private lateinit var chips: MutableList<Chip>
	private var WIDTH = 1f
	private var ROW_HEIGHT = 1f

	private var listener: OnCheckListener? = null

	constructor(context: Context) : super(context) {
		applyAttributes(context, null, 0)
	}

	constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
		applyAttributes(context, attrs, 0)
	}

	constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
		applyAttributes(context, attrs, defStyleAttr)
	}

	init {
		val density = AndroidUtils.getDisplayDensity()
		chipRadius = (8 * density).toInt()
		borderWidth = (2 * density).toInt()
		chipTextSize = (12 * density).toInt()
		chipPadding = (8 * density).toInt()
		drawablePadding = chipPadding / 2
		DRAWABLE_WIDTH = (20 * density).toInt()
		chipMargin = (6 * density).toInt()
	}

	private fun applyAttributes(context: Context, attrs: AttributeSet?, defStyleAttr: Int) {
		chips = ArrayList()
		//读取自定义属性
		val a = context.obtainStyledAttributes(attrs, R.styleable.ChipsView, defStyleAttr, 0)
		try {
			orderEffective = a.getBoolean(R.styleable.ChipsView_orderEffective, orderEffective)
			multiSelect = a.getBoolean(R.styleable.ChipsView_multiSelect, multiSelect)
			chipColor = a.getColor(R.styleable.ChipsView_chipColor, chipColor)
			rippleColor = a.getColor(R.styleable.ChipsView_rippleColor, rippleColor)
			borderColor = a.getColor(R.styleable.ChipsView_borderColor, borderColor)
			chipTextColor = a.getColor(R.styleable.ChipsView_chipTextColor, chipTextColor)
			chipTextSelectedColor = a.getColor(R.styleable.ChipsView_chipTextSelectedColor, chipTextSelectedColor)
			chipRadius = a.getDimensionPixelSize(R.styleable.ChipsView_chipRadius, chipRadius)
			borderWidth = a.getDimensionPixelSize(R.styleable.ChipsView_borderWidth, borderWidth)
			chipTextSize = a.getDimensionPixelSize(R.styleable.ChipsView_chipTextSize, chipTextSize)
			chipPadding = a.getDimensionPixelSize(R.styleable.ChipsView_chipPadding, chipPadding)
			drawablePadding = chipPadding / 2
			chipMargin = a.getDimensionPixelSize(R.styleable.ChipsView_chipMargin, chipMargin)
			ROW_HEIGHT = (2 * chipPadding + DRAWABLE_WIDTH).toFloat()
		} finally {
			a.recycle()
		}
	}

	override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
		super.onLayout(changed, left, top, right, bottom)
		WIDTH = (width - paddingStart - paddingEnd).toFloat()
	}

	override fun setVisibility(visibility: Int) {
		super.setVisibility(visibility)
		post { updateChipPositions() }
	}

	fun createChipView(id: Int, key: String, context: Context, name: String, color: Int, checked: Boolean): TextView {
		val textView = TextView(context)
		val lp = LayoutParams(LayoutParams.WRAP_CONTENT, ROW_HEIGHT.toInt())
		textView.layoutParams = lp
		if (checked) {
			setSelected(context, textView, color)
		} else {
			setUnselected(textView, color)
		}
		setRipple(textView)
		textView.text = name
		textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, chipTextSize.toFloat())
		textView.id = id
		textView.visibility = INVISIBLE
		textView.setTypeface(textView.typeface, Typeface.NORMAL)
		textView.setOnClickListener {
			val pos = findById(key)
			if (pos >= 0 && chipsEnabled) {
				if (chips[pos].isSelected) {
					if (multiSelect) {
						setUnselected(chips[pos].view, color)
						listener?.onCheck(key, name, false)
					}
				} else {
					setSelected(context, chips[pos].view, color)
					if (!multiSelect) {
						unselectAll(pos)
					}
					listener?.onCheck(key, name, true)
				}
				chips[pos].isSelected = !chips[pos].isSelected
			}
		}
		return textView
	}

	override fun isEnabled(): Boolean {
		return chipsEnabled
	}

	override fun setEnabled(enabled: Boolean) {
		chipsEnabled = enabled
	}

	fun setSelected(key: String) {
		val pos = findById(key)
		if (pos >= 0) {
			if (!multiSelect) {
				unselectAll(pos)
			}
			setSelected(context, chips[pos].view, chips[pos].color)
			chips[pos].isSelected = true
		}
	}

	fun getSelected(): String? {
		for (i in chips.indices) {
			if (chips[i].isSelected) {
				return chips[i].key
			}
		}
		return null
	}

	private fun setSelected(context: Context, textView: TextView, chipColor: Int) {
		setChecked(textView, chipColor)
		textView.setCompoundDrawablesWithIntrinsicBounds(
				ContextCompat.getDrawable(context, R.drawable.ic_check_circle), null, null, null)
		textView.compoundDrawablePadding = drawablePadding
		textView.setPadding(chipPadding, chipPadding, chipPadding, chipPadding)
		textView.setTextColor(chipTextSelectedColor)
	}

	private fun setUnselected(textView: TextView, chipColor: Int) {
		setUnchecked(textView, chipColor)
		textView.setTextColor(chipTextColor)
		textView.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null)
		textView.setPadding(chipPadding + DRAWABLE_WIDTH / 2 + drawablePadding / 2, chipPadding, chipPadding + DRAWABLE_WIDTH / 2 + drawablePadding / 2, chipPadding)
	}

	private fun setChecked(view: View, chipColor: Int) {
		view.background = RippleUtils.createShape(chipColor, borderColor, borderWidth, chipRadius.toFloat())
	}

	private fun setUnchecked(view: View, chipColor: Int) {
		view.background = RippleUtils.createShape(chipColor, chipRadius.toFloat())
	}

	private fun setRipple(view: View) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			view.foreground = RippleUtils.createRippleMaskShape(rippleColor, chipRadius.toFloat())
		}
	}

	fun unselectAll(pos: Int) {
		for (i in chips.indices) {
			if (i != pos) {
				setUnselected(chips[i].view, chips[i].color)
				chips[i].isSelected = false
			}
		}
	}

	fun setData(names: Array<String>?, keys: Array<String>?, colors: IntArray?) {
		clearChips()
		if (names != null && keys != null) {
			if (WIDTH <= 1) {
				WIDTH = (AndroidUtils.getScreenWidth(context) - paddingStart - paddingEnd).toFloat()
			}
			if (names.size >= 1) {
				for (i in names.indices) {
					if (colors != null) {
						chips.add(Chip(keys[i], createChipView(i, keys[i], context, names[i], colors[i], false), names[i], colors[i]))
					} else {
						chips.add(Chip(keys[i], createChipView(i, keys[i], context, names[i], chipColor, false), names[i], chipColor))
					}
					addView(chips[i].view)
				}
				post { updateChipPositions() }
			}
		}
	}

	fun removeChip(keys: Array<String>) {
		for (key in keys) {
			val pos = findById(key)
			if (pos >= 0) {
				removeViewAt(pos)
				chips.removeAt(pos)
			}
		}
		updateChipPositions()
	}

	fun addChip(keys: Array<String>, names: Array<String>) {
		for (i in keys.indices) {
			val key = keys[i]
			val name = names[i]
			val pos = findById(key)
			if (pos == -1) {
				chips.add(Chip(key, createChipView(chips.size, key, context, name, chipColor, false), name, chipColor))
				addView(chips[chips.size - 1].view)
			}
		}
		post { updateChipPositions() }
	}

	private fun updateChipPositions() {
		//计算各 chip 尺寸
		for (i in chips.indices) {
			val chip = chips[i]
			chip.width = chip.view.width
			chip.height = ROW_HEIGHT.toInt()

			chips[i] = chip
		}
		if (orderEffective) {
			Collections.sort(chips)
		}

		rowCount = 0
		posY = 0f
		if (chips.isNotEmpty() && WIDTH >= chips[0].width) {
			val temp = ArrayList(chips)
			if (orderEffective) {
				calculatePositions(temp)
			} else {
				calculatePositionsDefault(temp)
			}

			for (i in chips.indices) {
				chips[i].view.translationX = chips[i].posX
				chips[i].view.translationY = chips[i].posY
				chips[i].view.visibility = VISIBLE
			}
		}
		heightAnimator()
	}

	private fun heightAnimator() {
		val lp = layoutParams
		if (heightAnimator != null && heightAnimator!!.isStarted) {
			heightAnimator!!.cancel()
		}
		val newHeight = (ROW_HEIGHT + chipMargin).toInt() * rowCount - chipMargin + paddingTop + paddingBottom
		heightAnimator = ValueAnimator.ofInt(lp.height, newHeight)
		heightAnimator!!.interpolator = AccelerateDecelerateInterpolator()
		heightAnimator!!.duration = HEIGHT_ANIMATION_DURATION.toLong()
		heightAnimator!!.addUpdateListener { animation ->
			lp.height = animation.animatedValue as Int
			layoutParams = lp
		}
		heightAnimator!!.start()
	}

	private fun calculatePositionsDefault(temp: MutableList<Chip>) {
		var posX = 0f
		rowCount++
		if (temp.isNotEmpty()) {
			var availableWidth = WIDTH
			for (i in temp.indices) {
				if (availableWidth < temp[i].width) {
					rowCount++
					posY += ROW_HEIGHT + chipMargin
					availableWidth = WIDTH
					posX = 0f
				}
				chips[i].posX = posX
				chips[i].posY = posY

				val width = temp[i].width + chipMargin
				posX += width
				availableWidth -= width
			}
			posY += ROW_HEIGHT + chipMargin
		}
	}

	private fun calculatePositions(temp: MutableList<Chip>) {
		var posX = 0f
		rowCount++
		if (temp.isNotEmpty()) {
			var availableWidth = WIDTH
			for (i in temp.size - 1 downTo 0) {
				if (temp[0].width <= availableWidth) {
					if (temp[i].width <= availableWidth) {
						val pos = findById(temp[i].key)
						if (pos >= 0) {
							chips[pos].posX = posX
							chips[pos].posY = posY
						}

						val width = temp[i].width + chipMargin
						posX += width
						availableWidth -= width

						temp.removeAt(i)
					}
				}
			}
			posY += ROW_HEIGHT + chipMargin
			if (temp.isNotEmpty()) {
				calculatePositions(temp)
			}
		}
	}

	private fun findById(key: String): Int {
		for (i in chips.indices) {
			if (chips[i].key == key) {
				return i
			}
		}
		return -1
	}

	fun clearChips() {
		rowCount = 0
		posY = 0f
		chips.clear()
		removeAllViews()
	}

	fun setOnChipCheckListener(l: OnCheckListener?) {
		this.listener = l
	}

	fun interface OnCheckListener {
		fun onCheck(key: String, name: String, checked: Boolean)
	}

	private class Chip(
			var key: String,
			var view: TextView,
			var text: String,
			var color: Int
	) : Comparable<Chip> {

		var posX = 0f
		var posY = 0f
		var isSelected = false
		var width = 0
		var height = 0

		override fun compareTo(other: Chip): Int {
			return Integer.compare(width, other.width)
		}
	}

	companion object {
		private const val HEIGHT_ANIMATION_DURATION = 300
	}
}
