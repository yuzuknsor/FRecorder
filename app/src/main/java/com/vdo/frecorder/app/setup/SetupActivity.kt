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

package com.vdo.frecorder.app.setup

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.Toast
import com.vdo.frecorder.ARApplication
import com.vdo.frecorder.AppConstants
import com.vdo.frecorder.ColorMap
import com.vdo.frecorder.R
import com.vdo.frecorder.app.main.MainActivity
import com.vdo.frecorder.app.settings.AppSpinnerAdapter
import com.vdo.frecorder.app.settings.SettingsMapper
import com.vdo.frecorder.app.settings.ThemeItem
import com.vdo.frecorder.app.widget.SettingView
import com.vdo.frecorder.audio.AudioDeviceManager
import com.vdo.frecorder.util.AndroidUtils
import com.vdo.frecorder.util.FileUtil

/** 首次配置页。Kotlin 化（原 SetupActivity.java）。 */
class SetupActivity : Activity(), SetupContract.View, View.OnClickListener {

	private lateinit var nameFormatSelector: Spinner
	private var audioSourceSelector: Spinner? = null

	private lateinit var outputFormatSetting: SettingView
	private lateinit var bitDepthSetting: SettingView
	private lateinit var sampleRateSetting: SettingView
	private lateinit var bitrateSetting: SettingView
	private lateinit var channelsSetting: SettingView

	private lateinit var presenter: SetupContract.UserActionsListener
	private lateinit var colorMap: ColorMap
	private lateinit var onThemeColorChangeListener: ColorMap.OnThemeColorChangeListener

	private var availableAudioDevices: List<AudioDeviceInfo> = ArrayList()
	private lateinit var audioDeviceManager: AudioDeviceManager
	private var ignoreAudioSourceSelection = false
	private var ignoreThemeColorSelection = false

	override fun onCreate(savedInstanceState: Bundle?) {
		colorMap = ARApplication.injector.provideColorMap(applicationContext)
		setTheme(colorMap.appThemeResource)
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_setup)

		val btnApply = findViewById<Button>(R.id.btn_apply)
		val btnReset = findViewById<Button>(R.id.btn_reset)
		btnApply.setOnClickListener(this)
		btnReset.setOnClickListener(this)

		outputFormatSetting = findViewById(R.id.setting_output_format)
		val outputFormats = arrayOf("WAV", "MP3 320kbps", "FLAC")
		val outputFormatKeys = arrayOf(
				AppConstants.OUTPUT_FORMAT_WAV,
				AppConstants.OUTPUT_FORMAT_MP3,
				AppConstants.OUTPUT_FORMAT_FLAC
		)
		outputFormatSetting.setData(outputFormats, outputFormatKeys)
		outputFormatSetting.setOnChipCheckListener { key, _, _ -> presenter.setSettingOutputFormat(key) }
		outputFormatSetting.setTitle(R.string.output_format)
		outputFormatSetting.setOnInfoClickListener {
			AndroidUtils.showInfoDialog(this@SetupActivity, R.string.info_output_format)
		}

		bitDepthSetting = findViewById(R.id.setting_bit_depth)
		val bitDepths = arrayOf("16-bit", "24-bit")
		val bitDepthKeys = arrayOf(
				SettingsMapper.BIT_DEPTH_16_KEY,
				SettingsMapper.BIT_DEPTH_24_KEY
		)
		bitDepthSetting.setData(bitDepths, bitDepthKeys)
		bitDepthSetting.setOnChipCheckListener { key, _, _ ->
			presenter.setSettingBitDepth(SettingsMapper.keyToBitDepth(key))
		}
		bitDepthSetting.setTitle(R.string.bit_depth)
		bitDepthSetting.setOnInfoClickListener {
			AndroidUtils.showInfoDialog(this@SetupActivity, R.string.info_bit_depth)
		}

		sampleRateSetting = findViewById(R.id.setting_frequency)
		val sampleRates = resources.getStringArray(R.array.sample_rates2)
		val sampleRatesKeys = arrayOf(
				SettingsMapper.SAMPLE_RATE_8000,
				SettingsMapper.SAMPLE_RATE_16000,
				SettingsMapper.SAMPLE_RATE_22050,
				SettingsMapper.SAMPLE_RATE_32000,
				SettingsMapper.SAMPLE_RATE_44100,
				SettingsMapper.SAMPLE_RATE_48000
		)
		sampleRateSetting.setData(sampleRates, sampleRatesKeys)
		sampleRateSetting.setOnChipCheckListener { key, _, _ ->
			presenter.setSettingSampleRate(SettingsMapper.keyToSampleRate(key))
		}
		sampleRateSetting.setTitle(R.string.sample_rate)
		sampleRateSetting.setOnInfoClickListener {
			AndroidUtils.showInfoDialog(this@SetupActivity, R.string.info_frequency)
		}

		bitrateSetting = findViewById(R.id.setting_bitrate)
		val rates = resources.getStringArray(R.array.bit_rates2)
		val rateKeys = arrayOf(
				SettingsMapper.BITRATE_48000,
				SettingsMapper.BITRATE_96000,
				SettingsMapper.BITRATE_128000,
				SettingsMapper.BITRATE_192000,
				SettingsMapper.BITRATE_256000,
				SettingsMapper.BITRATE_288000
		)
		bitrateSetting.setData(rates, rateKeys)
		bitrateSetting.setOnChipCheckListener { key, _, _ ->
			presenter.setSettingRecordingBitrate(SettingsMapper.keyToBitrate(key))
		}
		bitrateSetting.setTitle(R.string.bitrate)
		bitrateSetting.setOnInfoClickListener {
			AndroidUtils.showInfoDialog(this@SetupActivity, R.string.info_bitrate)
		}

		channelsSetting = findViewById(R.id.setting_channels)
		val recChannels = resources.getStringArray(R.array.channels)
		val recChannelsKeys = arrayOf(
				SettingsMapper.CHANNEL_COUNT_STEREO,
				SettingsMapper.CHANNEL_COUNT_MONO
		)
		channelsSetting.setData(recChannels, recChannelsKeys)
		channelsSetting.setOnChipCheckListener { key, _, _ ->
			presenter.setSettingChannelCount(SettingsMapper.keyToChannelCount(key))
		}
		channelsSetting.setTitle(R.string.channels)
		channelsSetting.setOnInfoClickListener {
			AndroidUtils.showInfoDialog(this@SetupActivity, R.string.info_channels)
		}

		presenter = ARApplication.injector.provideSetupPresenter(applicationContext)
		audioDeviceManager = ARApplication.injector.provideAudioDeviceManager(applicationContext)

		initThemeColorSelector()
		initNameFormatSelector()
		initAudioSourceSelector()

		// 安装向导期间请求录音权限
		requestRecordingPermission()
	}

	private fun initThemeColorSelector() {
		val themeColor = findViewById<Spinner>(R.id.themeColor)
		val items: MutableList<ThemeItem> = ArrayList()
		val values = resources.getStringArray(R.array.theme_colors2)
		val colorRes = colorMap.getColorResources()
		for (i in values.indices) {
			items.add(ThemeItem(values[i], applicationContext.resources.getColor(colorRes[i])))
		}
		val adapter = AppSpinnerAdapter(this@SetupActivity,
				R.layout.list_item_spinner, R.id.txtItem, items, R.drawable.ic_color_lens)

		onThemeColorChangeListener = object : ColorMap.OnThemeColorChangeListener {
			override fun onThemeColorChange(colorKey: String) {
				setTheme(colorMap.appThemeResource)
				recreate()
			}
		}
		colorMap.addOnThemeColorChangeListener(onThemeColorChangeListener)

		ignoreThemeColorSelection = true
		themeColor.adapter = adapter
		val selected = SettingsMapper.colorKeyToPosition(colorMap.getSelected())
		themeColor.setSelection(selected)
		themeColor.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
			override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
				if (ignoreThemeColorSelection) {
					ignoreThemeColorSelection = false
					return
				}
				val colorKey = SettingsMapper.positionToColorKey(position)
				colorMap.updateColorMap(colorKey)
				presenter.setSettingThemeColor(colorKey)
			}

			override fun onNothingSelected(parent: AdapterView<*>?) {}
		}
	}

	private fun initNameFormatSelector() {
		nameFormatSelector = findViewById(R.id.name_format)
		val items: MutableList<ThemeItem> = ArrayList()
		val values = arrayOf(
				getString(R.string.naming) + " " + FileUtil.generateRecordNameCounted(1) + ".wav",
				getString(R.string.naming) + " " + FileUtil.generateRecordNameDateVariant() + ".wav",
				getString(R.string.naming) + " " + FileUtil.generateRecordNameDateUS() + ".wav",
				getString(R.string.naming) + " " + FileUtil.generateRecordNameDateISO8601() + ".wav",
				getString(R.string.naming) + " " + FileUtil.generateRecordNameMills() + ".wav"
		)
		for (value in values) {
			items.add(ThemeItem(value,
					applicationContext.resources.getColor(colorMap.primaryColorRes)))
		}
		val adapter = AppSpinnerAdapter(this@SetupActivity,
				R.layout.list_item_spinner, R.id.txtItem, items, R.drawable.ic_title)
		nameFormatSelector.adapter = adapter

		nameFormatSelector.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
			override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
				presenter.setSettingNamingFormat(SettingsMapper.positionToNamingFormat(position))
			}

			override fun onNothingSelected(parent: AdapterView<*>?) {}
		}
	}

	private fun initAudioSourceSelector() {
		audioSourceSelector = findViewById(R.id.setup_audio_source_selector) ?: return

		audioSourceSelector!!.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
			override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
				if (ignoreAudioSourceSelection) return
				val deviceId: Int
				if (position == 0) {
					deviceId = AppConstants.AUDIO_SOURCE_DEFAULT_MIC
				} else {
					deviceId = availableAudioDevices[position - 1].id
				}
				presenter.setSettingAudioSource(deviceId)
			}

			override fun onNothingSelected(parent: AdapterView<*>?) {}
		}
		loadAudioDevices()
	}

	private fun loadAudioDevices() {
		val selector = audioSourceSelector ?: return

		availableAudioDevices = audioDeviceManager.getAvailableInputDevices()
		val deviceNames: MutableList<String> = ArrayList()
		deviceNames.add(getString(R.string.audio_source_default_mic))

		for (device in availableAudioDevices) {
			deviceNames.add(AudioDeviceManager.getDeviceDisplayName(this, device) +
					" (" + AudioDeviceManager.getDeviceTypeString(this, device) + ")")
		}

		val adapter = ArrayAdapter(this,
				android.R.layout.simple_spinner_item, deviceNames)
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

		ignoreAudioSourceSelection = true
		selector.adapter = adapter
		selector.setSelection(0)
		ignoreAudioSourceSelection = false
	}

	private fun requestRecordingPermission() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
				requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_CODE_RECORD_AUDIO)
			}
		}
	}

	override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
		if (requestCode == REQ_CODE_RECORD_AUDIO) {
			if (grantResults.isNotEmpty() && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
				Toast.makeText(this, R.string.error_permission_denied, Toast.LENGTH_LONG).show()
			}
		}
	}

	override fun onClick(v: View) {
		val id = v.id
		if (id == R.id.btn_apply) {
			presenter.executeFirstRun()
			startActivity(MainActivity.getStartIntent(applicationContext))
			finish()
		} else if (id == R.id.btn_reset) {
			presenter.resetSettings()
			presenter.loadSettings()
		}
	}

	override fun onStart() {
		super.onStart()
		presenter.bindView(this)
		presenter.loadSettings()
	}

	override fun onStop() {
		super.onStop()
		presenter.unbindView()
	}

	override fun onDestroy() {
		super.onDestroy()
		colorMap.removeOnThemeColorChangeListener(onThemeColorChangeListener)
	}

	@Deprecated("Deprecated in Java")
	override fun onBackPressed() {
		super.onBackPressed()
		ARApplication.injector.releaseSetupPresenter()
	}

	override fun showRecordingBitrate(bitrate: Int) {
		bitrateSetting.setSelected(SettingsMapper.bitrateToKey(bitrate))
	}

	override fun showSampleRate(rate: Int) {
		sampleRateSetting.setSelected(SettingsMapper.sampleRateToKey(rate))
	}

	override fun showChannelCount(count: Int) {
		channelsSetting.setSelected(SettingsMapper.channelCountToKey(count))
	}

	override fun showNamingFormat(namingKey: String) {
		nameFormatSelector.setSelection(SettingsMapper.namingFormatToPosition(namingKey))
	}

	override fun showRecordingFormat(formatKey: String) {
		//录音格式现在固定 WAV，无需 UI
	}

	override fun showOutputFormat(outputFormatKey: String) {
		outputFormatSetting.setSelected(outputFormatKey)
	}

	override fun showBitDepth(bitDepth: Int) {
		bitDepthSetting.setSelected(SettingsMapper.bitDepthToKey(bitDepth))
	}

	override fun showBitrateSelector() {
		bitrateSetting.visibility = View.VISIBLE
	}

	override fun hideBitrateSelector() {
		bitrateSetting.visibility = View.GONE
	}

	override fun showInformation(infoResId: Int) {
	}

	override fun showSizePerMin(size: String) {
	}

	override fun updateRecordingInfo(format: String) {
		//录音格式现在只有 WAV；所有采样率与声道均可用
	}

	override fun showProgress() {
	}

	override fun hideProgress() {
	}

	override fun showError(message: String) {
		Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
	}

	override fun showError(resId: Int) {
		Toast.makeText(applicationContext, resId, Toast.LENGTH_LONG).show()
	}

	override fun showMessage(resId: Int) {
		Toast.makeText(applicationContext, resId, Toast.LENGTH_LONG).show()
	}

	companion object {
		private const val REQ_CODE_RECORD_AUDIO = 501

		@JvmStatic
		fun getStartIntent(context: Context): Intent {
			val intent = Intent(context, SetupActivity::class.java)
			intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
			return intent
		}
	}
}
