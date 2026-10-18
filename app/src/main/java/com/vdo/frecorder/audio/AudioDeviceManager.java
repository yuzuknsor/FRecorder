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

package com.vdo.frecorder.audio;

import android.content.Context;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import com.vdo.frecorder.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import timber.log.Timber;

/**
 * Manages audio input devices, including USB audio devices and the phone's own
 * built-in microphones.
 *
 * Every input device reported by AudioManager is exposed, sorted with built-in
 * microphones first. Explicitly selecting a built-in microphone (instead of the
 * "system default" pseudo-device) is what allows recording from the phone's own
 * mic while a USB-C headset / audio dongle is plugged in — the framework
 * otherwise routes recording to the headset mic that may not physically exist.
 */
public class AudioDeviceManager {

	public interface AudioDeviceListener {
		void onDevicesChanged(List<AudioDeviceInfo> availableDevices);
	}

	public static final int DEVICE_DEFAULT_MIC = -1;

	private static volatile AudioDeviceManager instance;

	private final AudioManager audioManager;
	private final Handler mainHandler;
	private final List<AudioDeviceListener> listeners;
	private AudioDeviceCallback deviceCallback;

	public static AudioDeviceManager getInstance(Context context) {
		if (instance == null) {
			synchronized (AudioDeviceManager.class) {
				if (instance == null) {
					instance = new AudioDeviceManager(context.getApplicationContext());
				}
			}
		}
		return instance;
	}

	private AudioDeviceManager(Context context) {
		audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
		mainHandler = new Handler(Looper.getMainLooper());
		listeners = new CopyOnWriteArrayList<>();
		registerDeviceCallback();
	}

	private void registerDeviceCallback() {
		deviceCallback = new AudioDeviceCallback() {
			@Override
			public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
				Timber.d("Audio devices added: %d", addedDevices.length);
				notifyDevicesChanged();
			}

			@Override
			public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
				Timber.d("Audio devices removed: %d", removedDevices.length);
				notifyDevicesChanged();
			}
		};
		audioManager.registerAudioDeviceCallback(deviceCallback, mainHandler);
	}

	/**
	 * Get all available audio input devices, built-in microphones first.
	 * The list is what the settings spinner shows (position + 1 maps to device id).
	 */
	public List<AudioDeviceInfo> getAvailableInputDevices() {
		List<AudioDeviceInfo> devices = new ArrayList<>();
		AudioDeviceInfo[] allDevices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS);

		for (AudioDeviceInfo device : allDevices) {
			if (isUsableInputDevice(device)) {
				devices.add(device);
			}
		}

		// Stable sort: built-in microphones first, then everything else.
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
			devices.sort(new Comparator<AudioDeviceInfo>() {
				@Override
				public int compare(AudioDeviceInfo a, AudioDeviceInfo b) {
					return Integer.compare(groupOf(a), groupOf(b));
				}
			});
		} else {
			Collections.sort(devices, new Comparator<AudioDeviceInfo>() {
				@Override
				public int compare(AudioDeviceInfo a, AudioDeviceInfo b) {
					return Integer.compare(groupOf(a), groupOf(b));
				}
			});
		}
		return devices;
	}

	private static int groupOf(AudioDeviceInfo device) {
		return isBuiltInMic(device) ? 0 : 1;
	}

	/**
	 * Devices that can actually be used as a recording input.
	 * Some vendors expose telephony / auxiliary inputs that cannot be recorded from,
	 * so they are filtered out.
	 */
	private boolean isUsableInputDevice(AudioDeviceInfo device) {
		int type = device.getType();
		switch (type) {
			case AudioDeviceInfo.TYPE_BUILTIN_MIC:
			case AudioDeviceInfo.TYPE_USB_DEVICE:
			case AudioDeviceInfo.TYPE_USB_HEADSET:
			case AudioDeviceInfo.TYPE_USB_ACCESSORY:
			case AudioDeviceInfo.TYPE_WIRED_HEADSET:
			case AudioDeviceInfo.TYPE_BLUETOOTH_SCO:
				return true;
			default:
				return false;
		}
	}

	/**
	 * True if the device is one of the phone's own microphones.
	 */
	public static boolean isBuiltInMic(AudioDeviceInfo device) {
		return device != null && device.getType() == AudioDeviceInfo.TYPE_BUILTIN_MIC;
	}

	/**
	 * True if the device is an external input device (USB, wired headset, Bluetooth).
	 */
	public static boolean isExternalInputDevice(AudioDeviceInfo device) {
		if (device == null) {
			return false;
		}
		int type = device.getType();
		return type == AudioDeviceInfo.TYPE_USB_DEVICE ||
				type == AudioDeviceInfo.TYPE_USB_HEADSET ||
				type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
				type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
				type == AudioDeviceInfo.TYPE_USB_ACCESSORY;
	}

	/**
	 * Get the first available built-in microphone, or null if none is reported.
	 */
	public AudioDeviceInfo getBuiltInMicDevice() {
		for (AudioDeviceInfo device : audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)) {
			if (isBuiltInMic(device)) {
				return device;
			}
		}
		return null;
	}

	/**
	 * Get an audio device by its ID.
	 * @param deviceId The device ID to look up
	 * @return The AudioDeviceInfo or null if not found
	 */
	public AudioDeviceInfo getDeviceById(int deviceId) {
		if (deviceId == DEVICE_DEFAULT_MIC) {
			return null;
		}

		AudioDeviceInfo[] allDevices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS);
		for (AudioDeviceInfo device : allDevices) {
			if (device.getId() == deviceId) {
				return device;
			}
		}
		return null;
	}

	/**
	 * Get a user-friendly display name for an audio device.
	 */
	public static String getDeviceDisplayName(AudioDeviceInfo device) {
		return getDeviceDisplayName(null, device);
	}

	/**
	 * Get a user-friendly, localized display name for an audio device.
	 * For built-in microphones the physical address (bottom / back / front) is
	 * appended when the framework reports one, so the user can tell them apart.
	 */
	public static String getDeviceDisplayName(Context context, AudioDeviceInfo device) {
		if (device == null) {
			return context != null
					? context.getString(R.string.device_name_default)
					: "Default Microphone";
		}

		String productName = device.getProductName() != null ?
				device.getProductName().toString().trim() : "";

		String name = productName;
		if (name.isEmpty()) {
			name = fallbackName(context, device);
		}

		String address = device.getAddress() != null ? device.getAddress().trim() : "";
		if (!address.isEmpty() && isBuiltInMic(device)) {
			name = name + " · " + address;
		}
		return name;
	}

	private static String fallbackName(Context context, AudioDeviceInfo device) {
		int resName = fallbackNameRes(device);
		if (context != null) {
			return context.getString(resName);
		}
		switch (device.getType()) {
			case AudioDeviceInfo.TYPE_USB_DEVICE: return "USB Audio Device";
			case AudioDeviceInfo.TYPE_USB_HEADSET: return "USB Headset";
			case AudioDeviceInfo.TYPE_WIRED_HEADSET: return "Wired Headset";
			case AudioDeviceInfo.TYPE_BLUETOOTH_SCO: return "Bluetooth Device";
			case AudioDeviceInfo.TYPE_USB_ACCESSORY: return "USB Accessory";
			default: return "Audio Device";
		}
	}

	private static int fallbackNameRes(AudioDeviceInfo device) {
		switch (device.getType()) {
			case AudioDeviceInfo.TYPE_BUILTIN_MIC: return R.string.device_name_builtin; //lint !e5
			case AudioDeviceInfo.TYPE_USB_DEVICE: return R.string.device_name_usb;
			case AudioDeviceInfo.TYPE_USB_HEADSET: return R.string.device_name_usb_headset;
			case AudioDeviceInfo.TYPE_WIRED_HEADSET: return R.string.device_name_wired;
			case AudioDeviceInfo.TYPE_BLUETOOTH_SCO: return R.string.device_name_bluetooth;
			case AudioDeviceInfo.TYPE_USB_ACCESSORY: return R.string.device_name_usb;
			default: return R.string.device_name_external;
		}
	}

	/**
	 * Get the device type as a user-friendly, localized string.
	 */
	public static String getDeviceTypeString(AudioDeviceInfo device) {
		return getDeviceTypeString(null, device);
	}

	public static String getDeviceTypeString(Context context, AudioDeviceInfo device) {
		int resName = typeNameRes(device);
		if (context != null) {
			return context.getString(resName);
		}
		switch (device == null ? -1 : device.getType()) {
			case AudioDeviceInfo.TYPE_BUILTIN_MIC: return "Built-in";
			case AudioDeviceInfo.TYPE_USB_DEVICE: return "USB";
			case AudioDeviceInfo.TYPE_USB_HEADSET: return "USB Headset";
			case AudioDeviceInfo.TYPE_WIRED_HEADSET: return "Wired";
			case AudioDeviceInfo.TYPE_BLUETOOTH_SCO: return "Bluetooth";
			case AudioDeviceInfo.TYPE_USB_ACCESSORY: return "USB";
			default: return "External";
		}
	}

	private static int typeNameRes(AudioDeviceInfo device) {
		if (device == null) {
			return R.string.device_type_default;
		}
		switch (device.getType()) {
			case AudioDeviceInfo.TYPE_BUILTIN_MIC: return R.string.device_type_builtin;
			case AudioDeviceInfo.TYPE_USB_DEVICE: return R.string.device_type_usb;
			case AudioDeviceInfo.TYPE_USB_HEADSET: return R.string.device_type_usb_headset;
			case AudioDeviceInfo.TYPE_WIRED_HEADSET: return R.string.device_type_wired;
			case AudioDeviceInfo.TYPE_BLUETOOTH_SCO: return R.string.device_type_bluetooth;
			case AudioDeviceInfo.TYPE_USB_ACCESSORY: return R.string.device_type_usb;
			default: return R.string.device_type_external;
		}
	}

	public void registerDeviceListener(AudioDeviceListener listener) {
		if (listener != null && !listeners.contains(listener)) {
			listeners.add(listener);
		}
	}

	public void unregisterDeviceListener(AudioDeviceListener listener) {
		listeners.remove(listener);
	}

	private void notifyDevicesChanged() {
		List<AudioDeviceInfo> devices = getAvailableInputDevices();
		for (AudioDeviceListener listener : listeners) {
			listener.onDevicesChanged(devices);
		}
	}

	public void release() {
		if (deviceCallback != null) {
			audioManager.unregisterAudioDeviceCallback(deviceCallback);
			deviceCallback = null;
		}
		listeners.clear();
	}
}
