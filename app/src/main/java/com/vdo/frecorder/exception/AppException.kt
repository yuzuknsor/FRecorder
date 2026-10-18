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

/**
 * 应用内所有可归类异常的基类：每个子类只负责给自己的错误类型编号。
 */
abstract class AppException : Exception() {

    /** 与 Java 侧 `getType()` 等价，旧代码无需改动。 */
    abstract val type: Int

    companion object {
        const val CANT_CREATE_FILE = 1
        const val INVALID_OUTPUT_FILE = 2
        const val RECORDER_INIT_EXCEPTION = 3
        const val PLAYER_INIT_EXCEPTION = 4
        const val PLAYER_DATA_SOURCE_EXCEPTION = 5
        const val CANT_PROCESS_RECORD = 6
        const val READ_PERMISSION_DENIED = 7
        const val NO_SPACE_AVAILABLE = 8
        const val RECORDING_ERROR = 9
        const val FAILED_TO_RESTORE = 10
    }
}
