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

import android.animation.Animator
import android.view.View
import android.view.animation.AnimationUtils

/**
 * 统一的位移动画入口：同样的时长与插值器，避免各处手写 animate() 造成手感不一致。
 */
object AnimationUtil {

    private const val DURATION = 250L

    private fun View.animateProperty(block: (android.view.ViewPropertyAnimator) -> Unit) {
        val animator = animate()
            .setDuration(DURATION)
            .setInterpolator(AnimationUtils.loadInterpolator(context, android.R.interpolator.accelerate_decelerate))
        block(animator)
        animator.start()
    }

    @JvmStatic
    fun viewElevationAnimation(view: View, value: Float, listener: Animator.AnimatorListener?) {
        view.animateProperty { it.translationZ(value).setListener(listener) }
    }

    @JvmStatic
    fun viewAnimationX(view: View, value: Float, listener: Animator.AnimatorListener?) {
        view.animateProperty { it.translationX(value).setListener(listener) }
    }

    @JvmStatic
    fun viewAnimationY(view: View, value: Float, listener: Animator.AnimatorListener?) {
        view.animateProperty { it.translationY(value).setListener(listener) }
    }
}
