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

package com.vdo.frecorder.util

import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RoundRectShape

/**
 * 圆角形状与波纹点击效果的工厂方法。
 */
object RippleUtils {

    @JvmStatic
    fun createRippleShape(
        backgroundColor: Int,
        rippleColor: Int,
        borderColor: Int,
        borderWidthPx: Int,
        cornerRadiusPx: Float
    ): RippleDrawable = RippleDrawable(
        createColorStateList(rippleColor),
        createShape(backgroundColor, borderColor, borderWidthPx, cornerRadiusPx),
        null
    )

    @JvmStatic
    fun createRippleShape(backgroundColor: Int, rippleColor: Int, cornerRadiusPx: Float): RippleDrawable =
        createRippleShape(backgroundColor, rippleColor, 0, 0, cornerRadiusPx)

    @JvmStatic
    fun createShape(
        backgroundColor: Int,
        borderColor: Int,
        borderWidthPx: Int,
        cornerRadiusPx: Float
    ): GradientDrawable {
        val shape = GradientDrawable()
        shape.shape = GradientDrawable.RECTANGLE
        if (cornerRadiusPx > 0) {
            shape.cornerRadii = cornerRadii(cornerRadiusPx)
        }
        shape.setColor(backgroundColor)
        if (borderWidthPx > 0) {
            shape.setStroke(borderWidthPx, borderColor)
        }
        return shape
    }

    @JvmStatic
    fun createShape(backgroundColor: Int, cornerRadiusPx: Float): GradientDrawable {
        val shape = GradientDrawable()
        shape.shape = GradientDrawable.RECTANGLE
        if (cornerRadiusPx > 0) {
            shape.cornerRadii = cornerRadii(cornerRadiusPx)
        }
        shape.setColor(backgroundColor)
        return shape
    }

    @JvmStatic
    fun createRippleMaskShape(rippleColor: Int, cornerRadiusPx: Float): RippleDrawable =
        RippleDrawable(createColorStateList(rippleColor), null, createMaskShape(cornerRadiusPx))

    @JvmStatic
    fun createMaskShape(cornerRadiusPx: Float): ShapeDrawable =
        ShapeDrawable(RoundRectShape(cornerRadii(cornerRadiusPx), null, null))

    @JvmStatic
    fun createColorStateList(pressedColor: Int): ColorStateList =
        ColorStateList(arrayOf(IntArray(0)), intArrayOf(pressedColor))

    @JvmStatic
    fun getBackgroundDrawable(pressedColor: Int, backgroundDrawable: Drawable?): RippleDrawable =
        RippleDrawable(createColorStateList(pressedColor), backgroundDrawable, null)

    @JvmStatic
    fun getColorDrawableFromColor(color: Int): ColorDrawable = ColorDrawable(color)

    private fun cornerRadii(radius: Float) = floatArrayOf(
        radius, radius, radius, radius, radius, radius, radius, radius
    )
}
