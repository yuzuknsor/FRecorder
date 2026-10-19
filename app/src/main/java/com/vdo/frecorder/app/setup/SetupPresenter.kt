/*
 * Copyright 2020 Dmytro Ponomarenko
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.vdo.frecorder.app.setup

import com.vdo.frecorder.AppConstants
import com.vdo.frecorder.R
import com.vdo.frecorder.data.Prefs
import java.text.DecimalFormat

/** 首次配置页逻辑。Kotlin 化（原 SetupPresenter.java）。 */
class SetupPresenter(private val prefs: Prefs) : SetupContract.UserActionsListener {

	private val decimalFormat = DecimalFormat("#.#")

	private var view: SetupContract.View? = null

	override fun loadSettings() {
		view?.let { v ->
			v.updateRecordingInfo(prefs.getSettingRecordingFormat())
			v.showChannelCount(prefs.getSettingChannelCount())
			val recordingFormatKey = prefs.getSettingRecordingFormat()
			v.showRecordingFormat(recordingFormatKey)
			updateRecordingFormat(recordingFormatKey)
			v.showNamingFormat(prefs.getSettingNamingFormat())
			v.showRecordingBitrate(prefs.getSettingBitrate())
			v.showSampleRate(prefs.getSettingSampleRate())
			v.showOutputFormat(prefs.getSettingOutputFormat())
			v.showBitDepth(prefs.getSettingBitDepth())
			updateSizePerMin()
		}
	}

	override fun setSettingRecordingBitrate(bitrate: Int) {
		prefs.setSettingBitrate(bitrate)
		showBitrateInfo(bitrate)
		updateSizePerMin()
	}

	private fun showBitrateInfo(bitrate: Int) {
		val text = when (bitrate) {
			AppConstants.RECORD_ENCODING_BITRATE_48000 -> R.string.info_bitrate_48
			AppConstants.RECORD_ENCODING_BITRATE_96000 -> R.string.info_bitrate_96
			AppConstants.RECORD_ENCODING_BITRATE_128000 -> R.string.info_bitrate_128
			AppConstants.RECORD_ENCODING_BITRATE_192000 -> R.string.info_bitrate_192
			AppConstants.RECORD_ENCODING_BITRATE_256000 -> R.string.info_bitrate_256
			AppConstants.RECORD_ENCODING_BITRATE_288000 -> R.string.info_bitrate_288
			else -> -1
		}
		if (text != -1) {
			view?.showInformation(text)
		}
	}

	override fun setSettingSampleRate(rate: Int) {
		prefs.setSettingSampleRate(rate)
		showSampleRateInfo(rate)
		updateSizePerMin()
	}

	private fun showSampleRateInfo(sampleRate: Int) {
		val text = when (sampleRate) {
			AppConstants.RECORD_SAMPLE_RATE_8000 -> R.string.info_sample_rate_8k
			AppConstants.RECORD_SAMPLE_RATE_16000 -> R.string.info_sample_rate_16k
			AppConstants.RECORD_SAMPLE_RATE_22050 -> R.string.info_sample_rate_22k
			AppConstants.RECORD_SAMPLE_RATE_32000 -> R.string.info_sample_rate_32k
			AppConstants.RECORD_SAMPLE_RATE_44100 -> R.string.info_sample_rate_44k
			AppConstants.RECORD_SAMPLE_RATE_48000 -> R.string.info_sample_rate_48k
			else -> -1
		}
		if (text != -1) {
			view?.showInformation(text)
		}
	}

	override fun setSettingChannelCount(count: Int) {
		prefs.setSettingChannelCount(count)
		view?.showInformation(R.string.info_channels)
		updateSizePerMin()
		when (count) {
			AppConstants.RECORD_AUDIO_STEREO -> view?.showInformation(R.string.info_stereo)
			AppConstants.RECORD_AUDIO_MONO -> view?.showInformation(R.string.info_mono)
		}
	}

	override fun setSettingThemeColor(colorKey: String) {
		prefs.setSettingThemeColor(colorKey)
	}

	override fun setSettingNamingFormat(namingKey: String) {
		prefs.setSettingNamingFormat(namingKey)
	}

	override fun setSettingRecordingFormat(formatKey: String) {
		prefs.setSettingRecordingFormat(formatKey)
		updateRecordingFormat(formatKey)
		view?.let {
			it.showInformation(R.string.info_wav)
			it.updateRecordingInfo(formatKey)
		}
		updateSizePerMin()
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

	override fun executeFirstRun() {
		if (prefs.isFirstRun()) {
			prefs.firstRunExecuted()
		}
	}

	override fun resetSettings() {
		prefs.resetSettings()
	}

	override fun bindView(view: SetupContract.View) {
		this.view = view
	}

	override fun unbindView() {
		if (view != null) {
			view = null
		}
	}

	override fun clear() {
		if (view != null) {
			unbindView()
		}
	}

	private fun updateRecordingFormat(formatKey: String) {
		view?.let {
			it.hideBitrateSelector()
			it.showInformation(R.string.info_wav)
		}
	}

	private fun updateSizePerMin() {
		val format = prefs.getSettingRecordingFormat()
		val sampleRate = prefs.getSettingSampleRate()
		val channelsCount = prefs.getSettingChannelCount()
		view?.showSizePerMin(decimalFormat.format(sizePerMin(format, sampleRate, 0, channelsCount) / 1000000f))
	}

	//录音内部一律 WAV
	private fun sizePerMin(recordingFormat: String, sampleRate: Int, bitrate: Int, channels: Int): Long =
			60 * (sampleRate.toLong() * channels * 2)
}
