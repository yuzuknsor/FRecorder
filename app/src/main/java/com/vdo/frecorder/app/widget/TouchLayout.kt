/*
 * Copyright 2018 Dmytro Ponomarenko
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.vdo.frecorder.app.widget

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.FrameLayout
import com.vdo.frecorder.util.AndroidUtils
import timber.log.Timber

/** 可拖拽的面板容器（上/下越过阈值即回调关闭）。Kotlin 化（原 TouchLayout.java）。 */
class TouchLayout : FrameLayout {

	private var action = ACTION_NONE

	private var realDy = 0f
	private var cumulatedDy = 0f

	private var startY = 0f

	private var returnPositionY = 0f

	//像素值换算为移动曲线函数中使用的系数
	private val k = MAX_MOVE / (Math.PI / 2).toFloat()

	private var onThresholdListener: ThresholdListener? = null

	constructor(context: Context) : super(context) {
		init()
	}

	constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
		init()
	}

	constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle) {
		init()
	}

	private fun init() {
		setOnTouchListener { _, motionEvent ->
			when (motionEvent.action and MotionEvent.ACTION_MASK) {
				MotionEvent.ACTION_DOWN -> {
					Timber.v("DOWN")
					action = ACTION_DRAG
					startY = motionEvent.y
					cumulatedDy = 0f
					realDy = 0f

					onThresholdListener?.onTouchDown()
				}
				MotionEvent.ACTION_MOVE -> {
					if (action == ACTION_DRAG) {
						realDy = motionEvent.y - startY
						cumulatedDy += realDy
						cumulatedDy = k * kotlin.math.atan(cumulatedDy / k)
						translationY = cumulatedDy + returnPositionY
					}
				}
				MotionEvent.ACTION_POINTER_DOWN -> {
					Timber.v("ZOOM")
					action = ACTION_ZOOM
				}
				MotionEvent.ACTION_POINTER_UP -> {
					Timber.v("DRAG")
					action = ACTION_NONE
				}
				MotionEvent.ACTION_UP -> {
					Timber.v("UP")
					performClick()

					animate().translationY(returnPositionY).start()

					if (cumulatedDy < -TOP_THRESHOLD) {
						onThresholdListener?.onTopThreshold()
					} else if (cumulatedDy > BOTTOM_THRESHOLD) {
						onThresholdListener?.onBottomThreshold()
					}
					onThresholdListener?.onTouchUp()
				}
			}
			true
		}
	}

	fun setOnThresholdListener(onThresholdListener: ThresholdListener?) {
		this.onThresholdListener = onThresholdListener
	}

	interface ThresholdListener {
		fun onTopThreshold()
		fun onBottomThreshold()
		fun onTouchDown()
		fun onTouchUp()
	}

	companion object {
		private const val ACTION_NONE = -1
		private const val ACTION_DRAG = 1
		private const val ACTION_ZOOM = 2

		private val MAX_MOVE = AndroidUtils.dpToPx(250).toInt() //dip
		private val TOP_THRESHOLD = (MAX_MOVE * 0.25).toInt() //dip
		private val BOTTOM_THRESHOLD = (MAX_MOVE * 0.40).toInt() //dip
	}
}
