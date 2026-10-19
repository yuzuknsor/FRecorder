/*
 * Copyright 2018 Dmytro Ponomarenko
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

package com.vdo.frecorder.audio.recorder

import android.media.MediaRecorder

/** 3GP/AMR 录音器（按采样率选 AMR_WB/AMR_NB）。Kotlin 化（原 ThreeGpRecorder.java）。 */
class ThreeGpRecorder private constructor() : MediaRecorderBase() {

	override val outputFormat: Int
		get() = MediaRecorder.OutputFormat.THREE_GPP

	//configureRecorder 里按采样率动态设 AMR_WB/AMR_NB，此属性仅满足契约
	override val audioEncoder: Int
		get() = MediaRecorder.AudioEncoder.AMR_WB

	override fun configureRecorder(recorder: MediaRecorder, sampleRate: Int) {
		recorder.setOutputFormat(outputFormat)
		if (sampleRate > 8000) {
			recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_WB) //AMR_WB 16000Hz ~23kbps
		} else {
			recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)  //AMR_NB 8000Hz ~12kbps
		}
	}

	companion object {
		@JvmStatic
		fun getInstance(): ThreeGpRecorder = Holder.singleton
	}

	private object Holder {
		val singleton = ThreeGpRecorder()
	}
}
