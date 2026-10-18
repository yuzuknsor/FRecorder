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

/**
 * One-shot override of the recording input device.
 *
 * Used by the "quick record with built-in microphone" screen: the user wants a
 * single take from the phone's own mic without touching the saved preference
 * (which may point at a USB / headset device). The value is consumed by
 * RecordingService on the next recording start and then discarded, so the
 * override never leaks into later, normal recordings.
 *
 * The override expires after {@link #TIMEOUT_MS} so a stale value can never
 * hijack a recording that is started much later.
 */
public final class RecordingDeviceOverride {

	/** No override active. Distinct from AudioDeviceManager.DEVICE_DEFAULT_MIC (-1) and real ids (>= 0). */
	public static final int NONE = -2;

	private static final long TIMEOUT_MS = 30_000L;

	private static volatile int forcedDeviceId = NONE;
	private static volatile long forcedAt = 0L;

	private RecordingDeviceOverride() { }

	public static synchronized void force(int deviceId) {
		if (deviceId < 0) {
			clear();
			return;
		}
		forcedDeviceId = deviceId;
		forcedAt = System.currentTimeMillis();
	}

	public static synchronized void clear() {
		forcedDeviceId = NONE;
		forcedAt = 0L;
	}

	public static synchronized boolean isActive() {
		return forcedDeviceId != NONE && (System.currentTimeMillis() - forcedAt) < TIMEOUT_MS;
	}

	/**
	 * @return the forced device id and clears the override, or {@link #NONE} if
	 * no override is active.
	 */
	public static synchronized int consume() {
		int id = isActive() ? forcedDeviceId : NONE;
		clear();
		return id;
	}
}
