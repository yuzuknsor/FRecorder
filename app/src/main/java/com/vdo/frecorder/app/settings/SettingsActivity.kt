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

package com.vdo.frecorder.app.settings

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.text.Html
import android.text.SpannableStringBuilder
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.vdo.frecorder.ARApplication
import com.vdo.frecorder.AppConstants
import com.vdo.frecorder.ColorMap
import com.vdo.frecorder.R
import com.vdo.frecorder.app.browser.FileBrowserActivity
import com.vdo.frecorder.app.moverecords.MoveRecordsActivity
import com.vdo.frecorder.app.quickmic.QuickMicActivity
import com.vdo.frecorder.app.trash.TrashActivity
import com.vdo.frecorder.app.widget.SettingView
import com.vdo.frecorder.audio.AudioDeviceManager
import com.vdo.frecorder.data.Prefs
import com.vdo.frecorder.util.AndroidUtils
import com.vdo.frecorder.util.FileUtil
import com.vdo.frecorder.util.RippleUtils
import timber.log.Timber
import java.io.File

/** 设置页。Kotlin 化（原 SettingsActivity.java）。 */
class SettingsActivity : Activity(), SettingsContract.View, View.OnClickListener {

	private lateinit var txtTotalDuration: TextView
	private lateinit var txtRecordsCount: TextView
	private lateinit var txtAvailableSpace: TextView
	private lateinit var txtSizePerMin: TextView
	private lateinit var txtInformation: TextView
	private lateinit var txtLocation: TextView
	private lateinit var txtStorageInfo: TextView
	private lateinit var txtFileBrowser: TextView
	private lateinit var btnView: TextView
	private lateinit var migratePublicStoragePanel: View
	private lateinit var panelPublicDir: View

	private lateinit var swPublicDir: Switch
	private lateinit var swKeepScreenOn: Switch
	private lateinit var swAskToRename: Switch
	private lateinit var swNoiseReduction: Switch
	private lateinit var btnNoiseReductionConfigure: TextView

	private lateinit var nameFormatSelector: Spinner
	private lateinit var audioSourceSelector: Spinner

	private lateinit var formatSetting: SettingView
	private lateinit var sampleRateSetting: SettingView
	private lateinit var bitrateSetting: SettingView
	private lateinit var channelsSetting: SettingView
	private lateinit var outputFormatSetting: SettingView
	private lateinit var bitDepthSetting: SettingView
	private lateinit var gainBoostSetting: SettingView
	private lateinit var btnReset: Button

	private var availableAudioDevices: List<AudioDeviceInfo> = ArrayList()
	private var ignoreAudioSourceSelection = false

	private lateinit var presenter: SettingsContract.UserActionsListener
	private lateinit var colorMap: ColorMap
	private lateinit var onThemeColorChangeListener: ColorMap.OnThemeColorChangeListener

	private val publicDirListener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
		presenter.storeInPublicDir(applicationContext, isChecked)
		if (isChecked) {
			showDialogPublicDirInfo()
		} else {
			showDialogPrivateDirInfo()
		}
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		colorMap = ARApplication.injector.provideColorMap(applicationContext)
		setTheme(colorMap.appThemeResource)
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_settings)

		btnView = findViewById(R.id.btnView)

		btnView.background = RippleUtils.createRippleShape(
				ContextCompat.getColor(applicationContext, R.color.white_transparent_80),
				ContextCompat.getColor(applicationContext, R.color.white_transparent_50),
				applicationContext.resources.getDimension(R.dimen.spacing_normal)
		)
		btnView.setOnClickListener(this)
		btnReset = findViewById(R.id.btnReset)
		btnReset.setOnClickListener(this)
		txtSizePerMin = findViewById(R.id.txt_size_per_min)
		txtInformation = findViewById(R.id.txt_information)
		txtLocation = findViewById(R.id.txt_records_location)
		txtStorageInfo = findViewById(R.id.txt_storage_info)
		migratePublicStoragePanel = findViewById(R.id.migrate_public_storage_panel)
		migratePublicStoragePanel.setOnClickListener(this)
		txtLocation.setOnClickListener(this)
		findViewById<View>(R.id.btnBack).setOnClickListener(this)
		val txtAbout = findViewById<TextView>(R.id.txtAbout)
		txtAbout.text = aboutContent
		findViewById<View>(R.id.btnTrash).setOnClickListener(this)
		findViewById<View>(R.id.btnCredits).setOnClickListener(this)
		findViewById<View>(R.id.btnQuickMic).setOnClickListener(this)
		panelPublicDir = findViewById(R.id.panelPublicDir)
		txtFileBrowser = findViewById(R.id.btn_file_browser)
		txtFileBrowser.setOnClickListener(this)
		swPublicDir = findViewById(R.id.swPublicDir)
		swKeepScreenOn = findViewById(R.id.swKeepScreenOn)
		swAskToRename = findViewById(R.id.swAskToRename)

		txtRecordsCount = findViewById(R.id.txt_records_count)
		txtTotalDuration = findViewById(R.id.txt_total_duration)
		txtAvailableSpace = findViewById(R.id.txt_available_space)

		swPublicDir.setOnCheckedChangeListener(publicDirListener)

		swKeepScreenOn.setOnCheckedChangeListener { _, isChecked -> presenter.keepScreenOn(isChecked) }
		swAskToRename.setOnCheckedChangeListener { _, isChecked -> presenter.askToRenameAfterRecordingStop(isChecked) }

		formatSetting = findViewById(R.id.setting_recording_format)
		val formats = arrayOf("Wav")
		val formatsKeys = arrayOf(AppConstants.FORMAT_WAV)
		formatSetting.setData(formats, formatsKeys)
		formatSetting.setOnChipCheckListener { key, _, _ -> presenter.setSettingRecordingFormat(key) }
		formatSetting.setTitle(R.string.recording_format)
		formatSetting.setOnInfoClickListener {
			AndroidUtils.showInfoDialog(this@SettingsActivity, R.string.info_format)
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
			AndroidUtils.showInfoDialog(this@SettingsActivity, R.string.info_frequency)
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
			AndroidUtils.showInfoDialog(this@SettingsActivity, R.string.info_bitrate)
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
			AndroidUtils.showInfoDialog(this@SettingsActivity, R.string.info_channels)
		}

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
			AndroidUtils.showInfoDialog(this@SettingsActivity, R.string.info_output_format)
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
			AndroidUtils.showInfoDialog(this@SettingsActivity, R.string.info_bit_depth)
		}

		gainBoostSetting = findViewById(R.id.setting_gain_boost)
		val gainBoostOptions = arrayOf(
				getString(R.string.gain_boost_off),
				getString(R.string.gain_boost_6db),
				getString(R.string.gain_boost_12db)
		)
		val gainBoostKeys = arrayOf(
				SettingsMapper.GAIN_BOOST_OFF_KEY,
				SettingsMapper.GAIN_BOOST_6DB_KEY,
				SettingsMapper.GAIN_BOOST_12DB_KEY
		)
		gainBoostSetting.setData(gainBoostOptions, gainBoostKeys)
		gainBoostSetting.setOnChipCheckListener { key, _, _ ->
			presenter.setGainBoostLevel(SettingsMapper.keyToGainBoostLevel(key))
		}
		gainBoostSetting.setTitle(R.string.gain_boost)

		swNoiseReduction = findViewById(R.id.swNoiseReduction)
		swNoiseReduction.setOnCheckedChangeListener { _, isChecked -> presenter.setNoiseReductionEnabled(isChecked) }

		btnNoiseReductionConfigure = findViewById(R.id.btnNoiseReductionConfigure)
		btnNoiseReductionConfigure.setOnClickListener { showNoiseReductionSettingsDialog() }

		audioSourceSelector = findViewById(R.id.audio_source_selector)
		audioSourceSelector.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
			override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
				if (ignoreAudioSourceSelection) {
					return
				}
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

		presenter = ARApplication.injector.provideSettingsPresenter(applicationContext)

		val pnlInfo = findViewById<LinearLayout>(R.id.info_panel)
		pnlInfo.background = RippleUtils.createShape(
				ContextCompat.getColor(applicationContext, R.color.white_transparent_88),
				resources.getDimension(R.dimen.spacing_normal)
		)

		btnReset.background = RippleUtils.createShape(
				ContextCompat.getColor(applicationContext, colorMap.primaryColorRes),
				resources.getDimension(R.dimen.spacing_normal)
		)
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			btnReset.foreground = RippleUtils.createRippleMaskShape(
					ContextCompat.getColor(applicationContext, R.color.white_transparent_80),
					resources.getDimension(R.dimen.spacing_normal)
			)
		}

		initThemeColorSelector()
		initNameFormatSelector()
	}

	private fun initThemeColorSelector() {
		val themeColor = findViewById<Spinner>(R.id.themeColor)
		val items: MutableList<ThemeItem> = ArrayList()
		val values = resources.getStringArray(R.array.theme_colors2)
		val colorRes = colorMap.getColorResources()
		for (i in values.indices) {
			items.add(ThemeItem(values[i], applicationContext.resources.getColor(colorRes[i])))
		}
		val adapter = AppSpinnerAdapter(this@SettingsActivity,
				R.layout.list_item_spinner, R.id.txtItem, items, R.drawable.ic_color_lens)
		themeColor.adapter = adapter

		onThemeColorChangeListener = object : ColorMap.OnThemeColorChangeListener {
			override fun onThemeColorChange(colorKey: String) {
				setTheme(colorMap.appThemeResource)
				recreate()
			}
		}
		colorMap.addOnThemeColorChangeListener(onThemeColorChangeListener)

		val selected = SettingsMapper.colorKeyToPosition(colorMap.getSelected())
		if (selected != themeColor.selectedItemPosition) {
			themeColor.setSelection(selected)
		}
		themeColor.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
			override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
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
		val adapter = AppSpinnerAdapter(this@SettingsActivity,
				R.layout.list_item_spinner, R.id.txtItem, items, R.drawable.ic_title)
		nameFormatSelector.adapter = adapter

		nameFormatSelector.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
			override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
				presenter.setSettingNamingFormat(SettingsMapper.positionToNamingFormat(position))
			}

			override fun onNothingSelected(parent: AdapterView<*>?) {}
		}
	}

	override fun onStart() {
		super.onStart()
		presenter.bindView(this)
		presenter.loadSettings()
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			//适配分区存储
			swPublicDir.isChecked = false
			swPublicDir.isEnabled = false
		}
	}

	override fun onStop() {
		super.onStop()
		presenter.unbindView()
	}

	override fun onDestroy() {
		super.onDestroy()
		colorMap.removeOnThemeColorChangeListener(onThemeColorChangeListener)
	}

	@SuppressLint("UnsafeOptInUsageWarning")
	override fun onClick(v: View) {
		val id = v.id
		if (id == R.id.btnBack) {
			ARApplication.injector.releaseSettingsPresenter()
			finish()
		} else if (id == R.id.migrate_public_storage_panel || id == R.id.btnView) {
			startActivity(MoveRecordsActivity.getStartIntent(applicationContext, true))
		} else if (id == R.id.btnTrash) {
			startActivity(TrashActivity.getStartIntent(applicationContext))
		} else if (id == R.id.txt_records_location) {
			presenter.onRecordsLocationClick()
		} else if (id == R.id.btn_file_browser) {
			startActivity(FileBrowserActivity.getStartIntent(applicationContext))
		} else if (id == R.id.btnReset) {
			presenter.resetSettings()
			presenter.loadSettings()
		} else if (id == R.id.btnCredits) {
			showCreditsDialog()
		} else if (id == R.id.btnQuickMic) {
			startActivity(QuickMicActivity.getStartIntent(applicationContext))
		}
	}

	@Deprecated("Deprecated in Java")
	override fun onBackPressed() {
		super.onBackPressed()
		ARApplication.injector.releaseSettingsPresenter()
	}

	private fun showCreditsDialog() {
		AndroidUtils.showInfoDialog(this, R.string.credits_message)
	}

	val aboutContent: SpannableStringBuilder
		get() {
			//构建关于内容并附上开源许可链接
			val aboutBody = SpannableStringBuilder()
			@Suppress("DEPRECATION")
			aboutBody.append(Html.fromHtml(getString(R.string.about_body, AndroidUtils.getAppVersion(applicationContext))))
			return aboutBody
		}

	override fun showStoreInPublicDir(b: Boolean) {
		swPublicDir.setOnCheckedChangeListener(null)
		swPublicDir.isChecked = b
		swPublicDir.setOnCheckedChangeListener(publicDirListener)
	}

	override fun showDirectorySetting(b: Boolean) {
		panelPublicDir.visibility = if (b) View.VISIBLE else View.GONE
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			txtStorageInfo.visibility = if (b) View.VISIBLE else View.GONE
		}
	}

	override fun showMigratePublicStorage(b: Boolean) {
		migratePublicStoragePanel.visibility = if (b) View.VISIBLE else View.GONE
	}

	override fun showKeepScreenOn(b: Boolean) {
		swKeepScreenOn.isChecked = b
	}

	override fun showChannelCount(count: Int) {
		channelsSetting.setSelected(SettingsMapper.channelCountToKey(count))
	}

	override fun showAudioSourceSetting(selectedDeviceId: Int, devices: List<AudioDeviceInfo>) {
		availableAudioDevices = devices

		//构建下拉项：「默认麦克风」+ 外部设备
		val deviceNames: MutableList<String> = ArrayList()
		deviceNames.add(getString(R.string.audio_source_default_mic))

		var selectedPosition = 0
		for (i in devices.indices) {
			val device = devices[i]
			deviceNames.add(AudioDeviceManager.getDeviceDisplayName(this, device) +
					" (" + AudioDeviceManager.getDeviceTypeString(this, device) + ")")
			if (device.id == selectedDeviceId) {
				selectedPosition = i + 1
			}
		}

		val adapter = ArrayAdapter(this,
				android.R.layout.simple_spinner_item, deviceNames)
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

		ignoreAudioSourceSelection = true
		audioSourceSelector.adapter = adapter
		audioSourceSelector.setSelection(selectedPosition)
		ignoreAudioSourceSelection = false
	}

	override fun showGainBoostLevel(level: Int) {
		gainBoostSetting.setSelected(SettingsMapper.gainBoostLevelToKey(level))
	}

	override fun showNoiseReductionEnabled(enabled: Boolean) {
		swNoiseReduction.isChecked = enabled
	}

	override fun showAskToRenameAfterRecordingStop(b: Boolean) {
		swAskToRename.isChecked = b
	}

	override fun showRecordingBitrate(bitrate: Int) {
		bitrateSetting.setSelected(SettingsMapper.bitrateToKey(bitrate))
	}

	override fun showRecordingSampleRate(rate: Int) {
		sampleRateSetting.setSelected(SettingsMapper.sampleRateToKey(rate))
	}

	override fun showRecordingFormat(formatKey: String) {
		formatSetting.setSelected(formatKey)
	}

	override fun showOutputFormat(outputFormatKey: String) {
		outputFormatSetting.setSelected(outputFormatKey)
	}

	override fun showBitDepth(bitDepth: Int) {
		bitDepthSetting.setSelected(SettingsMapper.bitDepthToKey(bitDepth))
	}

	override fun showNamingFormat(namingKey: String) {
		nameFormatSelector.setSelection(SettingsMapper.namingFormatToPosition(namingKey))
	}

	override fun showAllRecordsDeleted() {
		Toast.makeText(applicationContext, R.string.all_records_deleted, Toast.LENGTH_LONG).show()
	}

	override fun showFailDeleteAllRecords() {
		Toast.makeText(applicationContext, R.string.failed_to_delete_all_records, Toast.LENGTH_LONG).show()
	}

	override fun showTotalRecordsDuration(duration: String) {
		txtTotalDuration.text = resources.getString(R.string.total_duration, duration)
	}

	override fun showRecordsCount(count: Int) {
		txtRecordsCount.text = resources.getString(R.string.total_record_count, count)
	}

	override fun showAvailableSpace(space: String) {
		txtAvailableSpace.text = resources.getString(R.string.available_space, space)
	}

	override fun showBitrateSelector() {
		bitrateSetting.visibility = View.VISIBLE
	}

	override fun hideBitrateSelector() {
		bitrateSetting.visibility = View.GONE
	}

	override fun showDialogPublicDirInfo() {
		AndroidUtils.showInfoDialog(this, R.string.public_dir_warning)
	}

	override fun showDialogPrivateDirInfo() {
		AndroidUtils.showInfoDialog(this, R.string.private_dir_warning)
	}

	override fun updateRecordingInfo(format: String) {
		//录音格式现在只有 WAV；所有采样率与声道均可用
	}

	override fun showSizePerMin(size: String) {
		txtSizePerMin.text = getString(R.string.size_per_min, size)
	}

	override fun showInformation(info: String) {
		txtInformation.text = info
	}

	override fun showRecordsLocation(location: String) {
		txtLocation.visibility = View.VISIBLE
		txtLocation.text = getString(R.string.records_location, location)
	}

	override fun hideRecordsLocation() {
		txtLocation.text = ""
		txtLocation.visibility = View.GONE
	}

	override fun openRecordsLocation(file: File) {
		val intent = Intent(Intent.ACTION_VIEW)
		val fileUri = FileProvider.getUriForFile(
				applicationContext,
				applicationContext.packageName + ".app_file_provider",
				file
		)
		intent.setDataAndType(fileUri, DocumentsContract.Document.MIME_TYPE_DIR)
		intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
		intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
		try {
			startActivity(intent)
		} catch (e: ActivityNotFoundException) {
			Timber.e(e)
		}
	}

	override fun enableAudioSettings() {
		btnReset.isEnabled = true
		formatSetting.isEnabled = true
		sampleRateSetting.isEnabled = true
		bitrateSetting.isEnabled = true
		channelsSetting.isEnabled = true
		outputFormatSetting.isEnabled = true
		bitDepthSetting.isEnabled = true
		audioSourceSelector.isEnabled = true
		gainBoostSetting.isEnabled = true
	}

	override fun disableAudioSettings() {
		btnReset.isEnabled = false
		formatSetting.isEnabled = false
		sampleRateSetting.isEnabled = false
		bitrateSetting.isEnabled = false
		channelsSetting.isEnabled = false
		outputFormatSetting.isEnabled = false
		bitDepthSetting.isEnabled = false
		audioSourceSelector.isEnabled = false
		gainBoostSetting.isEnabled = false
	}

	private fun showNoiseReductionSettingsDialog() {
		val prefs: Prefs = ARApplication.injector.providePrefs(applicationContext)

		val currentDb = prefs.getNoiseReductionDb()
		val currentSensitivity = prefs.getNoiseReductionSensitivity()
		val currentFreqSmoothing = prefs.getNoiseReductionFreqSmoothing()
		val currentProfileSeconds = prefs.getNoiseProfileSeconds()

		val layout = LinearLayout(this)
		layout.orientation = LinearLayout.VERTICAL
		val pad = resources.getDimension(R.dimen.spacing_normal).toInt()
		layout.setPadding(pad, pad, pad, 0)

		//降噪强度滑块（0-24，步进 0.5）
		val txtDb = TextView(this)
		txtDb.text = getString(R.string.noise_reduction_db, currentDb)
		layout.addView(txtDb)
		val seekDb = SeekBar(this)
		seekDb.max = 48 //0-24，0.5 步进
		seekDb.progress = (currentDb * 2).toInt()
		seekDb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
			override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
				txtDb.text = getString(R.string.noise_reduction_db, progress / 2.0f)
			}

			override fun onStartTrackingTouch(sb: SeekBar) {}

			override fun onStopTrackingTouch(sb: SeekBar) {}
		})
		layout.addView(seekDb)

		//灵敏度滑块（0-24，步进 0.5）
		val txtSensitivity = TextView(this)
		txtSensitivity.text = getString(R.string.noise_reduction_sensitivity, currentSensitivity)
		layout.addView(txtSensitivity)
		val seekSensitivity = SeekBar(this)
		seekSensitivity.max = 48 //0-24，0.5 步进
		seekSensitivity.progress = (currentSensitivity * 2).toInt()
		seekSensitivity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
			override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
				txtSensitivity.text = getString(R.string.noise_reduction_sensitivity, progress / 2.0f)
			}

			override fun onStartTrackingTouch(sb: SeekBar) {}

			override fun onStopTrackingTouch(sb: SeekBar) {}
		})
		layout.addView(seekSensitivity)

		//频率平滑滑块（0-6，步进 1）
		val txtFreqSmoothing = TextView(this)
		txtFreqSmoothing.text = getString(R.string.noise_reduction_freq_smoothing, currentFreqSmoothing)
		layout.addView(txtFreqSmoothing)
		val seekFreqSmoothing = SeekBar(this)
		seekFreqSmoothing.max = 6
		seekFreqSmoothing.progress = currentFreqSmoothing
		seekFreqSmoothing.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
			override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
				txtFreqSmoothing.text = getString(R.string.noise_reduction_freq_smoothing, progress)
			}

			override fun onStartTrackingTouch(sb: SeekBar) {}

			override fun onStopTrackingTouch(sb: SeekBar) {}
		})
		layout.addView(seekFreqSmoothing)

		//噪声样本时长滑块（0.5-5.0，步进 0.5）
		val txtProfile = TextView(this)
		txtProfile.text = getString(R.string.noise_profile_seconds, currentProfileSeconds)
		layout.addView(txtProfile)
		val seekProfile = SeekBar(this)
		seekProfile.max = 9 //0.5-5.0，0.5 步进，0..9 映射 0.5..5.0
		seekProfile.progress = ((currentProfileSeconds - 0.5f) * 2).toInt()
		seekProfile.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
			override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
				txtProfile.text = getString(R.string.noise_profile_seconds, (progress + 1) / 2.0f)
			}

			override fun onStartTrackingTouch(sb: SeekBar) {}

			override fun onStopTrackingTouch(sb: SeekBar) {}
		})
		layout.addView(seekProfile)

		AlertDialog.Builder(this)
				.setTitle(R.string.noise_reduction_settings)
				.setView(layout)
				.setPositiveButton(R.string.btn_save) { _, _ ->
					prefs.setNoiseReductionDb(seekDb.progress / 2.0f)
					prefs.setNoiseReductionSensitivity(seekSensitivity.progress / 2.0f)
					prefs.setNoiseReductionFreqSmoothing(seekFreqSmoothing.progress)
					prefs.setNoiseProfileSeconds((seekProfile.progress + 1) / 2.0f)
				}
				.setNegativeButton(R.string.btn_cancel, null)
				.show()
	}

	override fun showProgress() {
//		TODO: showProgress
	}

	override fun hideProgress() {
//		TODO: hideProgress
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
		@JvmStatic
		fun getStartIntent(context: Context): Intent {
			val intent = Intent(context, SettingsActivity::class.java)
			intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
			return intent
		}
	}
}
