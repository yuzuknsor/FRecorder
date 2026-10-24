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

package com.vdo.frecorder.app

import android.media.AudioDeviceInfo
import com.vdo.frecorder.IntArrayList
import com.vdo.frecorder.audio.recorder.RecorderContract
import java.io.File

/** 应用级录音器接口。Kotlin 化（原 AppRecorder.java）。 */
interface AppRecorder {

	fun addRecordingCallback(recorderCallback: AppRecorderCallback?)
	fun removeRecordingCallback(recorderCallback: AppRecorderCallback?)
	fun setRecorder(recorder: RecorderContract.Recorder)
	fun startRecording(filePath: String, channelCount: Int, sampleRate: Int, bitrate: Int)
	fun startRecording(filePath: String, channelCount: Int, sampleRate: Int, bitrate: Int, audioDevice: AudioDeviceInfo?)
	fun startRecording(filePath: String, channelCount: Int, sampleRate: Int, bitrate: Int, audioDevice: AudioDeviceInfo?, gainBoostLevel: Int)
	fun pauseRecording()
	fun resumeRecording()
	fun stopRecording()
	fun getRecordingData(): IntArrayList
	fun getRecordingDuration(): Long
	fun isRecording(): Boolean
	fun isPaused(): Boolean
	fun getRecordFile(): File?
	fun release()
}
