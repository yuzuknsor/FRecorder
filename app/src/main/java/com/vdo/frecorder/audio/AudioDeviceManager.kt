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

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.vdo.frecorder.R
import timber.log.Timber
import java.util.Collections
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 录音输入设备管理：USB 设备与手机内置麦克风。
 *
 * AudioManager 上报的每个输入设备都会暴露，内置麦克风排前面。
 * 显式选内置麦克风（而非「系统默认」伪设备）才能在插着 USB-C 耳机/声卡时
 * 从手机自带麦克风录音——否则框架会把录音路由到可能不存在的耳机麦。
 *
 * Kotlin 化（原 AudioDeviceManager.java）。
 */
class AudioDeviceManager private constructor(context: Context) {

	interface AudioDeviceListener {
		fun onDevicesChanged(availableDevices: List<AudioDeviceInfo>)
	}

	private val audioManager: AudioManager =
			context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
	private val mainHandler = Handler(Looper.getMainLooper())
	private val listeners = CopyOnWriteArrayList<AudioDeviceListener>()
	private var deviceCallback: AudioDeviceCallback? = null

	init {
		registerDeviceCallback()
	}

	private fun registerDeviceCallback() {
		deviceCallback = object : AudioDeviceCallback() {
			override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
				Timber.d("Audio devices added: %d", addedDevices.size)
				notifyDevicesChanged()
			}

			override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
				Timber.d("Audio devices removed: %d", removedDevices.size)
				notifyDevicesChanged()
			}
		}
		audioManager.registerAudioDeviceCallback(deviceCallback, mainHandler)
	}

	/** 全部可用输入设备，内置麦克风在前。设置页 spinner 的位置 +1 即设备 id。 */
	fun getAvailableInputDevices(): List<AudioDeviceInfo> {
		val devices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
				.filter { isUsableInputDevice(it) }
				.toMutableList()
		//稳定排序：内置麦克风优先
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
			devices.sortBy { groupOf(it) }
		} else {
			Collections.sort(devices) { a, b -> groupOf(a) - groupOf(b) }
		}
		return devices
	}

	private fun isUsableInputDevice(device: AudioDeviceInfo): Boolean = when (device.type) {
		AudioDeviceInfo.TYPE_BUILTIN_MIC,
		AudioDeviceInfo.TYPE_USB_DEVICE,
		AudioDeviceInfo.TYPE_USB_HEADSET,
		AudioDeviceInfo.TYPE_USB_ACCESSORY,
		AudioDeviceInfo.TYPE_WIRED_HEADSET,
		AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> true
		else -> false
	}

	/** 第一个可用的内置麦克风；没有则 null */
	fun getBuiltInMicDevice(): AudioDeviceInfo? {
		return audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
				.firstOrNull { isBuiltInMic(it) }
	}

	/** 按设备 id 查设备；找不到返回 null */
	fun getDeviceById(deviceId: Int): AudioDeviceInfo? {
		if (deviceId == DEVICE_DEFAULT_MIC) return null
		return audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
				.firstOrNull { it.id == deviceId }
	}

	fun registerDeviceListener(listener: AudioDeviceListener) {
		if (!listeners.contains(listener)) {
			listeners.add(listener)
		}
	}

	fun unregisterDeviceListener(listener: AudioDeviceListener) {
		listeners.remove(listener)
	}

	private fun notifyDevicesChanged() {
		val devices = getAvailableInputDevices()
		for (listener in listeners) {
			listener.onDevicesChanged(devices)
		}
	}

	fun release() {
		deviceCallback?.let { audioManager.unregisterAudioDeviceCallback(it) }
	}

	companion object {
		const val DEVICE_DEFAULT_MIC = -1

		@Volatile
		private var instance: AudioDeviceManager? = null

		@JvmStatic
		fun getInstance(context: Context): AudioDeviceManager {
			return instance ?: synchronized(this) {
				instance ?: AudioDeviceManager(context.applicationContext).also { instance = it }
			}
		}

		@JvmStatic
		fun isBuiltInMic(device: AudioDeviceInfo?): Boolean =
				device != null && device.type == AudioDeviceInfo.TYPE_BUILTIN_MIC

		/** 外部输入设备（USB / 有线 / 蓝牙） */
		@JvmStatic
		fun isExternalInputDevice(device: AudioDeviceInfo?): Boolean {
			if (device == null) return false
			return when (device.type) {
				AudioDeviceInfo.TYPE_USB_DEVICE,
				AudioDeviceInfo.TYPE_USB_HEADSET,
				AudioDeviceInfo.TYPE_WIRED_HEADSET,
				AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
				AudioDeviceInfo.TYPE_USB_ACCESSORY -> true
				else -> false
			}
		}

		/** 设备显示名：内置麦附带物理方位（bottom/back/front），便于区分多个内置麦 */
		@JvmStatic
		fun getDeviceDisplayName(device: AudioDeviceInfo?): String {
			return getDeviceDisplayName(null, device)
		}

		@JvmStatic
		fun getDeviceTypeString(device: AudioDeviceInfo?): String {
			return getDeviceTypeString(null, device)
		}

		@JvmStatic
		fun getDeviceTypeString(context: Context?, device: AudioDeviceInfo?): String {
			val resName = typeNameRes(device)
			if (context != null) {
				return context.getString(resName)
			}
			return when (device?.type ?: -1) {
				AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Built-in"
				AudioDeviceInfo.TYPE_USB_DEVICE -> "USB"
				AudioDeviceInfo.TYPE_USB_HEADSET -> "USB Headset"
				AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired"
				AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth"
				AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB"
				else -> "External"
			}
		}

		@JvmStatic
		fun getDeviceDisplayName(context: Context?, device: AudioDeviceInfo?): String {
			if (device == null) {
				return context?.getString(R.string.device_name_default) ?: "Default Microphone"
			}
			var name = device.productName?.toString()?.trim().orEmpty()
			if (name.isEmpty()) {
				name = fallbackName(context, device)
			}
			val address = device.address?.trim().orEmpty()
			if (address.isNotEmpty() && isBuiltInMic(device)) {
				name = "$name · $address"
			}
			return name
		}

		private fun fallbackName(context: Context?, device: AudioDeviceInfo): String {
			val resName = fallbackNameRes(device)
			if (context != null) {
				return context.getString(resName)
			}
			return when (device.type) {
				AudioDeviceInfo.TYPE_USB_DEVICE -> "USB Audio Device"
				AudioDeviceInfo.TYPE_USB_HEADSET -> "USB Headset"
				AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired Headset"
				AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth Device"
				AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB Accessory"
				else -> "Audio Device"
			}
		}

		private fun fallbackNameRes(device: AudioDeviceInfo): Int {
			return when (device.type) {
				AudioDeviceInfo.TYPE_BUILTIN_MIC -> R.string.device_name_builtin
				AudioDeviceInfo.TYPE_USB_DEVICE -> R.string.device_name_usb
				AudioDeviceInfo.TYPE_USB_HEADSET -> R.string.device_name_usb_headset
				AudioDeviceInfo.TYPE_WIRED_HEADSET -> R.string.device_name_wired
				AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> R.string.device_name_bluetooth
				AudioDeviceInfo.TYPE_USB_ACCESSORY -> R.string.device_name_usb
				else -> R.string.device_name_external
			}
		}

		private fun typeNameRes(device: AudioDeviceInfo?): Int {
			if (device == null) {
				return R.string.device_type_default
			}
			return when (device.type) {
				AudioDeviceInfo.TYPE_BUILTIN_MIC -> R.string.device_type_builtin
				AudioDeviceInfo.TYPE_USB_DEVICE -> R.string.device_type_usb
				AudioDeviceInfo.TYPE_USB_HEADSET -> R.string.device_type_usb_headset
				AudioDeviceInfo.TYPE_WIRED_HEADSET -> R.string.device_type_wired
				AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> R.string.device_type_bluetooth
				AudioDeviceInfo.TYPE_USB_ACCESSORY -> R.string.device_type_usb
				else -> R.string.device_type_external
			}
		}

		private fun groupOf(device: AudioDeviceInfo): Int = if (isBuiltInMic(device)) 0 else 1
	}
}
