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

package com.vdo.frecorder.exception

import com.vdo.frecorder.R

/**
 * 把内部异常类型翻译成可直接展示给用户的字符串资源。
 */
object ErrorParser {

    @JvmStatic
    fun parseException(e: AppException): Int = when (e.type) {
        AppException.CANT_CREATE_FILE -> R.string.error_cant_create_file
        AppException.INVALID_OUTPUT_FILE -> R.string.error_invalid_output_file
        AppException.RECORDER_INIT_EXCEPTION -> R.string.error_failed_to_init_recorder
        AppException.PLAYER_DATA_SOURCE_EXCEPTION -> R.string.error_player_data_source
        AppException.PLAYER_INIT_EXCEPTION -> R.string.error_failed_to_init_player
        AppException.CANT_PROCESS_RECORD -> R.string.error_process_waveform
        AppException.NO_SPACE_AVAILABLE -> R.string.error_no_available_space
        AppException.RECORDING_ERROR -> R.string.error_on_recording
        AppException.FAILED_TO_RESTORE -> R.string.error_failed_to_restore
        AppException.READ_PERMISSION_DENIED -> R.string.error_permission_denied
        else -> R.string.error_unknown
    }
}
