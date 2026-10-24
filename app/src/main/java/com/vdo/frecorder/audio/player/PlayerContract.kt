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

package com.vdo.frecorder.audio.player

import com.vdo.frecorder.exception.AppException

/**
 * @deprecated use [PlayerContractNew]
 */
@Deprecated("use PlayerContractNew")
interface PlayerContract {

	interface PlayerCallback {
		fun onPreparePlay()
		fun onStartPlay()
		fun onPlayProgress(mills: Long)
		fun onStopPlay()
		fun onPausePlay()
		fun onSeek(mills: Long)
		fun onError(throwable: AppException)
	}

	interface Player {
		fun addPlayerCallback(callback: PlayerContract.PlayerCallback?)
		fun removePlayerCallback(callback: PlayerContract.PlayerCallback?): Boolean
		fun setData(data: String?)
		fun playOrPause()
		fun seek(mills: Long)
		fun pause()
		fun stop()
		fun isPlaying(): Boolean
		fun isPause(): Boolean
		fun getPauseTime(): Long
		fun release()
	}
}
