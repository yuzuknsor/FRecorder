/*
 * Copyright 2018 Dmytro Ponomarenko
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.vdo.frecorder

import android.annotation.SuppressLint
import android.content.Context
import com.vdo.frecorder.app.AppRecorder
import com.vdo.frecorder.app.AppRecorderImpl
import com.vdo.frecorder.app.browser.FileBrowserContract
import com.vdo.frecorder.app.browser.FileBrowserPresenter
import com.vdo.frecorder.app.lostrecords.LostRecordsContract
import com.vdo.frecorder.app.lostrecords.LostRecordsPresenter
import com.vdo.frecorder.app.main.MainContract
import com.vdo.frecorder.app.main.MainPresenter
import com.vdo.frecorder.app.moverecords.MoveRecordsViewModel
import com.vdo.frecorder.app.records.RecordsContract
import com.vdo.frecorder.app.records.RecordsPresenter
import com.vdo.frecorder.app.settings.SettingsContract
import com.vdo.frecorder.app.settings.SettingsMapper
import com.vdo.frecorder.app.settings.SettingsPresenter
import com.vdo.frecorder.app.setup.SetupContract
import com.vdo.frecorder.app.setup.SetupPresenter
import com.vdo.frecorder.app.trash.TrashContract
import com.vdo.frecorder.app.trash.TrashPresenter
import com.vdo.frecorder.audio.AudioDeviceManager
import com.vdo.frecorder.audio.AudioWaveformVisualization
import com.vdo.frecorder.audio.player.AudioPlayerNew
import com.vdo.frecorder.audio.player.PlayerContractNew
import com.vdo.frecorder.audio.recorder.RecorderContract
import com.vdo.frecorder.audio.recorder.WavRecorder
import com.vdo.frecorder.data.FileRepository
import com.vdo.frecorder.data.FileRepositoryImpl
import com.vdo.frecorder.data.Prefs
import com.vdo.frecorder.data.PrefsImpl
import com.vdo.frecorder.data.RecordDataSource
import com.vdo.frecorder.data.database.LocalRepository
import com.vdo.frecorder.data.database.LocalRepositoryImpl
import com.vdo.frecorder.data.database.RecordsDataSource
import com.vdo.frecorder.data.database.TrashDataSource

/** 依赖装配器（手工 DI）。Kotlin 化（原 Injector.java）。 */
class Injector {

	private var loadingTasks: BackgroundQueue? = null
	private var recordingTasks: BackgroundQueue? = null
	private var importTasks: BackgroundQueue? = null
	private var processingTasks: BackgroundQueue? = null
	private var copyTasks: BackgroundQueue? = null

	private var mainPresenter: MainContract.UserActionsListener? = null
	private var recordDataSource: RecordDataSource? = null
	private var recordsPresenter: RecordsContract.UserActionsListener? = null
	private var settingsPresenter: SettingsContract.UserActionsListener? = null
	private var lostRecordsPresenter: LostRecordsContract.UserActionsListener? = null
	private var fileBrowserPresenter: FileBrowserContract.UserActionsListener? = null
	private var trashPresenter: TrashContract.UserActionsListener? = null
	private var setupPresenter: SetupContract.UserActionsListener? = null

	private var moveRecordsViewModel: MoveRecordsViewModel? = null

	private var audioPlayer: PlayerContractNew.Player? = null

	fun providePrefs(context: Context): Prefs = PrefsImpl.getInstance(context)

	fun provideRecordsDataSource(context: Context): RecordsDataSource = RecordsDataSource.getInstance(context)

	fun provideTrashDataSource(context: Context): TrashDataSource = TrashDataSource.getInstance(context)

	fun provideFileRepository(context: Context): FileRepository =
			FileRepositoryImpl.getInstance(context, providePrefs(context))

	fun provideLocalRepository(context: Context): LocalRepository =
			LocalRepositoryImpl.getInstance(
					provideRecordsDataSource(context),
					provideTrashDataSource(context),
					provideFileRepository(context),
					providePrefs(context)
			)

	fun provideAppRecorder(context: Context): AppRecorder =
			AppRecorderImpl.getInstance(
					provideAudioRecorder(context), provideLocalRepository(context),
					provideLoadingTasksQueue(), provideRecordDataSource(context), providePrefs(context)
			)

	fun provideAudioWaveformVisualization(): AudioWaveformVisualization =
			AudioWaveformVisualization(provideProcessingTasksQueue())

	fun provideLoadingTasksQueue(): BackgroundQueue =
			loadingTasks ?: BackgroundQueue("LoadingTasks").also { loadingTasks = it }

	fun provideRecordingTasksQueue(): BackgroundQueue =
			recordingTasks ?: BackgroundQueue("RecordingTasks").also { recordingTasks = it }

	fun provideImportTasksQueue(): BackgroundQueue =
			importTasks ?: BackgroundQueue("ImportTasks").also { importTasks = it }

	fun provideProcessingTasksQueue(): BackgroundQueue =
			processingTasks ?: BackgroundQueue("ProcessingTasks").also { processingTasks = it }

	fun provideCopyTasksQueue(): BackgroundQueue =
			copyTasks ?: BackgroundQueue("CopyTasks").also { copyTasks = it }

	fun provideColorMap(context: Context): ColorMap = ColorMap.getInstance(providePrefs(context))

	fun provideSettingsMapper(context: Context): SettingsMapper = SettingsMapper.getInstance(context)

	fun provideAudioPlayer(): PlayerContractNew.Player {
		audioPlayer?.let { return it }
		synchronized(playerLock) {
			audioPlayer?.let { return it }
			val created = AudioPlayerNew()
			audioPlayer = created
			return created
		}
	}

	fun provideAudioRecorder(context: Context): RecorderContract.Recorder = WavRecorder.getInstance()

	fun provideAudioDeviceManager(context: Context): AudioDeviceManager = AudioDeviceManager.getInstance(context)

	fun provideRecordDataSource(context: Context): RecordDataSource =
			recordDataSource ?: RecordDataSource(
					provideLocalRepository(context),
					providePrefs(context)
			).also { recordDataSource = it }

	fun provideMainPresenter(context: Context): MainContract.UserActionsListener =
			mainPresenter ?: MainPresenter(
					providePrefs(context), provideFileRepository(context),
					provideLocalRepository(context), provideAudioPlayer(), provideAppRecorder(context),
					provideRecordingTasksQueue(), provideLoadingTasksQueue(), provideProcessingTasksQueue(),
					provideImportTasksQueue(), provideSettingsMapper(context), provideRecordDataSource(context)
			).also { mainPresenter = it }

	fun provideRecordsPresenter(context: Context): RecordsContract.UserActionsListener =
			recordsPresenter ?: RecordsPresenter(
					provideLocalRepository(context), provideFileRepository(context),
					provideLoadingTasksQueue(), provideRecordingTasksQueue(),
					provideAudioPlayer(), provideAppRecorder(context), providePrefs(context)
			).also { recordsPresenter = it }

	fun provideSettingsPresenter(context: Context): SettingsContract.UserActionsListener =
			settingsPresenter ?: SettingsPresenter(
					provideLocalRepository(context), provideFileRepository(context),
					provideRecordingTasksQueue(), provideLoadingTasksQueue(), providePrefs(context),
					provideSettingsMapper(context), provideAppRecorder(context), provideAudioDeviceManager(context)
			).also { settingsPresenter = it }

	fun provideTrashPresenter(context: Context): TrashContract.UserActionsListener =
			trashPresenter ?: TrashPresenter(
					provideLoadingTasksQueue(), provideRecordingTasksQueue(),
					provideFileRepository(context), provideLocalRepository(context)
			).also { trashPresenter = it }

	fun provideSetupPresenter(context: Context): SetupContract.UserActionsListener =
			setupPresenter ?: SetupPresenter(providePrefs(context)).also { setupPresenter = it }

	@SuppressLint("UnsafeOptInUsageWarning")
	fun provideMoveRecordsViewModel(context: Context): MoveRecordsViewModel =
			moveRecordsViewModel ?: MoveRecordsViewModel(
					provideLoadingTasksQueue(),
					provideLocalRepository(context),
					provideFileRepository(context),
					provideSettingsMapper(context),
					provideAudioPlayer(),
					provideAppRecorder(context),
					providePrefs(context)
			).also { moveRecordsViewModel = it }

	fun provideLostRecordsPresenter(context: Context): LostRecordsContract.UserActionsListener =
			lostRecordsPresenter ?: LostRecordsPresenter(
					provideLoadingTasksQueue(), provideRecordingTasksQueue(),
					provideLocalRepository(context), providePrefs(context)
			).also { lostRecordsPresenter = it }

	fun provideFileBrowserPresenter(context: Context): FileBrowserContract.UserActionsListener =
			fileBrowserPresenter ?: FileBrowserPresenter(
					providePrefs(context), provideAppRecorder(context), provideImportTasksQueue(),
					provideLoadingTasksQueue(), provideRecordingTasksQueue(),
					provideLocalRepository(context), provideFileRepository(context)
			).also { fileBrowserPresenter = it }

	fun releaseTrashPresenter() {
		trashPresenter?.clear()
		trashPresenter = null
	}

	fun releaseLostRecordsPresenter() {
		lostRecordsPresenter?.clear()
		lostRecordsPresenter = null
	}

	fun releaseFileBrowserPresenter() {
		fileBrowserPresenter?.clear()
		fileBrowserPresenter = null
	}

	fun releaseRecordsPresenter() {
		recordsPresenter?.clear()
		recordsPresenter = null
	}

	fun releaseMainPresenter() {
		mainPresenter?.clear()
		mainPresenter = null
	}

	fun releaseSettingsPresenter() {
		settingsPresenter?.clear()
		settingsPresenter = null
	}

	fun releaseSetupPresenter() {
		setupPresenter?.clear()
		setupPresenter = null
	}

	@SuppressLint("UnsafeOptInUsageWarning")
	fun releaseMoveRecordsViewModel() {
		moveRecordsViewModel?.clear()
		moveRecordsViewModel = null
	}

	fun closeTasks() {
		loadingTasks?.let {
			it.cleanupQueue()
			it.close()
		}
		importTasks?.let {
			it.cleanupQueue()
			it.close()
		}
		processingTasks?.let {
			it.cleanupQueue()
			it.close()
		}
		recordingTasks?.let {
			it.cleanupQueue()
			it.close()
		}
	}

	companion object {
		private val playerLock = Any()
	}
}
