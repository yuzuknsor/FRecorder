/*
 * Copyright 2024 Dmytro Ponomarenko
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

package com.vdo.frecorder.audio

/**
 * 一次性录音输入设备覆盖。
 *
 * 「内置麦克风快速录音」页用：用户想用手机自带麦克风录一条，
 * 不动已保存的偏好（可能指向 USB/耳机）。值由 RecordingService 在
 * 下次录音开始时消费后丢弃，不会泄漏到之后的正常录音。
 *
 * 覆盖在 [TIMEOUT_MS] 后过期，避免过期值劫持很久之后才开始的录音。
 */
object RecordingDeviceOverride {

	/** 无覆盖。区别于 AudioDeviceManager.DEVICE_DEFAULT_MIC (-1) 与真实 id (>=0)。 */
	const val NONE = -2

	private const val TIMEOUT_MS = 30_000L

	@Volatile
	private var forcedDeviceId = NONE

	@Volatile
	private var forcedAt = 0L

	@JvmStatic
	@Synchronized
	fun force(deviceId: Int) {
		if (deviceId < 0) {
			clear()
			return
		}
		forcedDeviceId = deviceId
		forcedAt = System.currentTimeMillis()
	}

	@JvmStatic
	@Synchronized
	fun clear() {
		forcedDeviceId = NONE
		forcedAt = 0L
	}

	@JvmStatic
	@Synchronized
	fun isActive(): Boolean = forcedDeviceId != NONE && (System.currentTimeMillis() - forcedAt) < TIMEOUT_MS

	/**
	 * @return 强制的设备 id 并清除覆盖；无生效覆盖时返回 [NONE]。
	 */
	@JvmStatic
	@Synchronized
	fun consume(): Int {
		val id = if (isActive()) forcedDeviceId else NONE
		clear()
		return id
	}
}
