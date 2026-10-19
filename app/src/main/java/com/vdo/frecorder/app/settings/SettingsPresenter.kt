/*
 * Copyright 2020 Dmytro Ponomarenko
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.vdo.frecorder.app.settings

import android.content.Context
import android.media.AudioDeviceInfo
import com.vdo.frecorder.AppConstants
import com.vdo.frecorder.BackgroundQueue
import com.vdo.frecorder.app.AppRecorder
import com.vdo.frecorder.app.AppRecorderCallback
import com.vdo.frecorder.audio.AudioDeviceManager
import com.vdo.frecorder.data.FileRepository
import com.vdo.frecorder.data.Prefs
import com.vdo.frecorder.data.database.LocalRepository
import com.vdo.frecorder.data.database.Record
import com.vdo.frecorder.exception.AppException
import com.vdo.frecorder.util.AndroidUtils
import com.vdo.frecorder.util.FileUtil
import com.vdo.frecorder.util.TimeUtils
import java.io.File
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** 设置页逻辑。Kotlin 化（原 SettingsPresenter.java）。 */
class SettingsPresenter(
		private val localRepository: LocalRepository,
		private val fileRepository: FileRepository,
		private val recordingsTasks: BackgroundQueue,
		private val loadingTasks: BackgroundQueue,
		private val prefs: Prefs,
		private val settingsMapper: SettingsMapper,
		private val appRecorder: AppRecorder,
		private val audioDeviceManager: AudioDeviceManager?
) : SettingsContract.UserActionsListener {

	private var view: SettingsContract.View? = null

	private val decimalFormat: DecimalFormat = DecimalFormat("#.#",
			DecimalFormatSymbols(Locale.getDefault()).apply { decimalSeparator = '.' })

	private var appRecorderCallback: AppRecorderCallback? = null
	private var deviceListener: AudioDeviceManager.AudioDeviceListener? = null

	override fun loadSettings() {
		view?.showProgress()
		loadingTasks.postRunnable {
			val durations = localRepository.getRecordsDurations()
			var totalDuration = 0L
			for (i in durations.indices) {
				totalDuration += durations[i]
			}
			val finalTotalDuration = totalDuration
			AndroidUtils.runOnUIThread {
				view?.let { v ->
					v.showTotalRecordsDuration(TimeUtils.formatTimeIntervalHourMinSec(finalTotalDuration / 1000))
					v.showRecordsCount(durations.size)
					updateAvailableSpace()
					v.hideProgress()
				}
			}
		}
		view?.let { v ->
			v.updateRecordingInfo(prefs.getSettingRecordingFormat())

			val isPublicDir = prefs.isStoreDirPublic
			v.showStoreInPublicDir(isPublicDir)
			if (isPublicDir) {
				v.showRecordsLocation(fileRepository.getRecordingDir().absolutePath)
			} else {
				v.hideRecordsLocation()
			}
			v.showAskToRenameAfterRecordingStop(prefs.isAskToRenameAfterStopRecording())
			v.showKeepScreenOn(prefs.isKeepScreenOn)
			v.showChannelCount(prefs.getSettingChannelCount())
			val recordingFormatKey = prefs.getSettingRecordingFormat()
			v.showRecordingFormat(recordingFormatKey)
			updateRecordingFormat(recordingFormatKey)
			v.showNamingFormat(prefs.getSettingNamingFormat())
			v.showRecordingBitrate(prefs.getSettingBitrate())
			v.showRecordingSampleRate(prefs.getSettingSampleRate())
			v.showOutputFormat(prefs.getSettingOutputFormat())
			v.showBitDepth(prefs.getSettingBitDepth())
			//scoped storage 需要
			v.showDirectorySetting(prefs.isShowDirectorySetting())

			loadAudioSourceSetting()

			v.showGainBoostLevel(prefs.getGainBoostLevel())

			v.showNoiseReductionEnabled(prefs.isNoiseReductionEnabled())
		}
	}

	private fun loadAudioSourceSetting() {
		val v = view ?: return
		val manager = audioDeviceManager ?: return
		val devices: List<AudioDeviceInfo> = manager.getAvailableInputDevices()
		var selectedDevice = prefs.getSettingAudioSource()

		//选中的设备可能已拔掉
		if (selectedDevice != AppConstants.AUDIO_SOURCE_DEFAULT_MIC) {
			if (manager.getDeviceById(selectedDevice) == null) {
				selectedDevice = AppConstants.AUDIO_SOURCE_DEFAULT_MIC
				prefs.setSettingAudioSource(selectedDevice)
			}
		}
		v.showAudioSourceSetting(selectedDevice, devices)
	}

	override fun storeInPublicDir(context: Context, b: Boolean) {
		prefs.isStoreDirPublic = b
		fileRepository.updateRecordingDir(context, prefs)
		view?.let { v ->
			if (b) {
				v.showRecordsLocation(fileRepository.getRecordingDir().absolutePath)
			} else {
				v.hideRecordsLocation()
			}
		}
	}

	override fun keepScreenOn(keep: Boolean) {
		prefs.isKeepScreenOn = keep
	}

	override fun askToRenameAfterRecordingStop(b: Boolean) {
		prefs.setAskToRenameAfterStopRecording(b)
	}

	override fun setSettingRecordingBitrate(bitrate: Int) {
		prefs.setSettingBitrate(bitrate)
		updateAvailableSpace()
	}

	override fun setSettingSampleRate(rate: Int) {
		prefs.setSettingSampleRate(rate)
		updateAvailableSpace()
	}

	override fun setSettingChannelCount(count: Int) {
		prefs.setSettingChannelCount(count)
		updateAvailableSpace()
	}

	override fun setSettingThemeColor(colorKey: String) {
		prefs.setSettingThemeColor(colorKey)
	}

	override fun setSettingNamingFormat(namingKey: String) {
		prefs.setSettingNamingFormat(namingKey)
	}

	override fun setSettingRecordingFormat(formatKey: String) {
		prefs.setSettingRecordingFormat(formatKey)
		view?.let { v ->
			v.updateRecordingInfo(formatKey)
			v.showChannelCount(prefs.getSettingChannelCount())
		}
		updateRecordingFormat(formatKey)
		updateAvailableSpace()
	}

	override fun setSettingOutputFormat(outputFormatKey: String) {
		prefs.setSettingOutputFormat(outputFormatKey)
	}

	override fun setSettingBitDepth(bitDepth: Int) {
		prefs.setSettingBitDepth(bitDepth)
	}

	override fun setSettingAudioSource(deviceId: Int) {
		prefs.setSettingAudioSource(deviceId)
	}

	override fun setGainBoostLevel(level: Int) {
		prefs.setGainBoostLevel(level)
	}

	override fun setNoiseReductionEnabled(enabled: Boolean) {
		prefs.setNoiseReductionEnabled(enabled)
	}

	override fun refreshAudioDevices() {
		loadAudioSourceSetting()
	}

	override fun deleteAllRecords() {
		recordingsTasks.postRunnable {
			//原实现就是空转（清库路径已废弃）
		}
	}

	override fun bindView(view: SettingsContract.View) {
		this.view = view

		if (appRecorder.isRecording()) {
			view.disableAudioSettings()
			val callback = object : AppRecorderCallback {
				override fun onRecordingStarted(file: File) {}
				override fun onRecordingPaused() {}
				override fun onRecordingResumed() {}
				override fun onRecordingStopped(file: File, record: Record?) {
					view.enableAudioSettings()
				}
				override fun onRecordingProgress(mills: Long, amp: Int) {}
				override fun onError(throwable: AppException) {}
			}
			appRecorderCallback = callback
			appRecorder.addRecordingCallback(callback)
		} else {
			view.enableAudioSettings()
		}

		//USB 设备插拔监听
		audioDeviceManager?.let { manager ->
			val listener = object : AudioDeviceManager.AudioDeviceListener {
				override fun onDevicesChanged(availableDevices: List<AudioDeviceInfo>) {
					AndroidUtils.runOnUIThread { this@SettingsPresenter.loadAudioSourceSetting() }
				}
			}
			deviceListener = listener
			manager.registerDeviceListener(listener)
		}
	}

	override fun unbindView() {
		if (view != null) {
			view = null
			appRecorderCallback?.let { appRecorder.removeRecordingCallback(it) }
			audioDeviceManager?.let { manager ->
				deviceListener?.let { manager.unregisterDeviceListener(it) }
				deviceListener = null
			}
		}
	}

	override fun clear() {
		if (view != null) {
			unbindView()
		}
	}

	override fun resetSettings() {
		prefs.resetSettings()
	}

	override fun onRecordsLocationClick() {
		view?.openRecordsLocation(fileRepository.getRecordingDir())
	}

	private fun updateAvailableSpace() {
		val space = FileUtil.getFree(fileRepository.getRecordingDir())
		val format = prefs.getSettingRecordingFormat()
		val sampleRate = prefs.getSettingSampleRate()
		val channelsCount = prefs.getSettingChannelCount()
		val time = spaceToTimeSecs(space, format, sampleRate, 0, channelsCount)
		view?.let { v ->
			v.showAvailableSpace(TimeUtils.formatTimeIntervalHourMinSec(time))
			v.showSizePerMin(decimalFormat.format(sizePerMin(format, sampleRate, 0, channelsCount) / 1000000f))
			v.showInformation(
					settingsMapper.convertFormatsToString(format) + AppConstants.SEPARATOR +
							settingsMapper.convertSampleRateToString(sampleRate) + AppConstants.SEPARATOR +
							settingsMapper.convertChannelsToString(channelsCount)
			)
		}
	}

	//录音内部一律 WAV
	private fun spaceToTimeSecs(spaceBytes: Long, recordingFormat: String, sampleRate: Int, bitrate: Int, channels: Int): Long =
			1000 * (spaceBytes / (sampleRate.toLong() * channels * 2))

	private fun sizePerMin(recordingFormat: String, sampleRate: Int, bitrate: Int, channels: Int): Long =
			60 * (sampleRate.toLong() * channels * 2)

	private fun updateRecordingFormat(formatKey: String) {
		//只录 WAV，码率选择器不需要
		view?.hideBitrateSelector()
	}
}
