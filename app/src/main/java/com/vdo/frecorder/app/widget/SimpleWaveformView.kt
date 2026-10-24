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

package com.vdo.frecorder.app.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.vdo.frecorder.R
import com.vdo.frecorder.util.AndroidUtils

/** 列表项用的小波形图。Kotlin 化（原 SimpleWaveformView.java）。 */
class SimpleWaveformView : View {

	private lateinit var waveformPaint: Paint

	private var waveformData: IntArray? = null

	private var waveForm: IntArray? = null

	private var isInitialized = false

	private val empty = IntArray(0)

	/**
	 * 防止在视图测量完成前调用 [adjustWaveformHeights]——
	 * 该方法要用到 onMeasure 中计算出的测量高度。
	 */
	private var isMeasured = false

	constructor(context: Context) : super(context) {
		init(context)
	}

	constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
		init(context)
	}

	constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
		init(context)
	}

	private fun init(context: Context) {
		isFocusable = false

		waveformPaint = Paint()
		waveformPaint.style = Paint.Style.STROKE
		waveformPaint.strokeWidth = AndroidUtils.dpToPx(1)
		waveformPaint.strokeJoin = Paint.Join.ROUND
		waveformPaint.isAntiAlias = true
		@Suppress("DEPRECATION")
		waveformPaint.color = context.resources.getColor(waveformColorRes)

		waveForm = null
		isInitialized = false
	}

	fun setWaveform(frameGains: IntArray?) {
		if (frameGains != null) {
			waveForm = frameGains
			waveformData = frameGains
			if (isMeasured) {
				adjustWaveformHeights(waveForm!!)
			}
		} else {
			if (isMeasured) {
				adjustWaveformHeights(empty)
			}
		}
		requestLayout()
	}

	override fun setSelected(selected: Boolean) {
		super.setSelected(selected)
		@Suppress("DEPRECATION")
		if (selected) {
			waveformPaint.color = context.resources.getColor(R.color.md_grey_500)
		} else {
			@Suppress("DEPRECATION")
			waveformPaint.color = context.resources.getColor(R.color.md_grey_700)
		}
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		super.onMeasure(widthMeasureSpec, heightMeasureSpec)

		if (!isMeasured) isMeasured = true
		//把测量尺寸与本视图的约束做调和，得出最终测量宽高
		val width = MeasureSpec.getSize(widthMeasureSpec)

		setMeasuredDimension(
				resolveSize(width, widthMeasureSpec),
				heightMeasureSpec)
	}

	override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
		super.onLayout(changed, left, top, right, bottom)
		if (isMeasured && !isInitialized) {
			if (waveForm != null) {
				adjustWaveformHeights(waveForm!!)
			} else {
				adjustWaveformHeights(empty)
			}
		}
	}

	override fun onDraw(canvas: Canvas) {
		super.onDraw(canvas)
		if (waveformData == null) {
			return
		}
		drawWaveForm(canvas)
	}

	private fun drawWaveForm(canvas: Canvas) {
		val data = waveformData ?: return
		var width = data.size
		val half = measuredHeight / 2

		if (width > measuredWidth) {
			width = measuredWidth
		}

		val dpi = AndroidUtils.dpToPx(1)
		val lines = FloatArray(width * 4 + 4)
		var step = 0
		for (i in 0 until width) {
			lines[step] = i * dpi
			lines[step + 1] = half + data[i].toFloat()
			lines[step + 2] = i * dpi
			lines[step + 3] = half - data[i].toFloat()
			step += 4
		}
		//水平零线
		lines[step] = 0f
		lines[step + 1] = half.toFloat()
		lines[step + 2] = width * dpi
		lines[step + 3] = half.toFloat()
		canvas.drawLines(lines, 0, lines.size, waveformPaint)
	}

	/**
	 * 新音频文件加载时调用一次。
	 */
	private fun adjustWaveformHeights(frameGains: IntArray) {
		val numFrames = frameGains.size
		//一帧对应屏幕上一像素
		val smoothedGains = frameGains

		//把范围限制到 0 - 255
		var maxGain = 1.0
		for (i in 0 until numFrames) {
			if (smoothedGains[i] > maxGain) {
				maxGain = smoothedGains[i].toDouble()
			}
		}
		var scaleFactor = 1.0
		if (maxGain > 255.0) {
			scaleFactor = 255 / maxGain
		}

		//构建 256 桶直方图，找出缩放后的新最大值
		maxGain = 0.0
		val gainHist = IntArray(256)
		for (i in 0 until numFrames) {
			var smoothedGain = (smoothedGains[i] * scaleFactor).toInt()
			if (smoothedGain < 0) smoothedGain = 0
			if (smoothedGain > 255) smoothedGain = 255

			if (smoothedGain > maxGain) maxGain = smoothedGain.toDouble()

			gainHist[smoothedGain]++
		}

		//下限校准到 5%
		var minGain = 0.0
		var sum = 0
		while (minGain < 255 && sum < numFrames / 20) {
			sum += gainHist[minGain.toInt()]
			minGain++
		}

		//上限校准到 99%
		sum = 0
		while (maxGain > 2 && sum < numFrames / 100) {
			sum += gainHist[maxGain.toInt()]
			maxGain--
		}

		//计算高度
		val heights = DoubleArray(numFrames)
		var range = maxGain - minGain
		if (range <= 0) {
			range = 1.0
		}
		for (i in 0 until numFrames) {
			var value = (smoothedGains[i] * scaleFactor - minGain) / range
			if (value < 0.0) value = 0.0
			if (value > 1.0) value = 1.0
			heights[i] = value * value
		}

		val halfHeight = measuredHeight / 2

		waveformData = IntArray(numFrames)
		for (i in 0 until numFrames) {
			waveformData!![i] = (heights[i] * halfHeight).toInt()
		}

		isInitialized = true
	}

	companion object {
		@Suppress("StaticFieldLeak")
		private var waveformColorRes = 0

		@JvmStatic
		fun setWaveformColorRes(waveformColorRes: Int) {
			SimpleWaveformView.waveformColorRes = waveformColorRes
		}
	}
}
