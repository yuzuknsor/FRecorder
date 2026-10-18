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

package com.vdo.frecorder.util

import android.content.Context
import android.text.format.DateFormat
import com.vdo.frecorder.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 时间与日期格式化。所有方法保持原有输出格式不变，仅语言层面转写。
 */
object TimeUtils {

    /** 22.11.2018 11:30 */
    private val dateTimeFormat = SimpleDateFormat("dd.MM.yyyy HH.mm.ss", Locale.getDefault())

    /** 11-22-2018 11:30AM */
    private val dateTimeFormatUS = SimpleDateFormat("MM-dd-yyyy hh.mm.ssaa", Locale.getDefault())

    /** 2021-08-25 11:50 */
    private val dateTimeFormatISO8601 = SimpleDateFormat("yyyy-MM-dd HH.mm.ss", Locale.getDefault())

    @JvmStatic
    fun formatTimeIntervalMinSec(length: Long): String {
        val timeUnit = TimeUnit.MILLISECONDS
        val numMinutes = timeUnit.toMinutes(length)
        val numSeconds = timeUnit.toSeconds(length)
        return String.format(Locale.getDefault(), "%02d:%02d", numMinutes, numSeconds % 60)
    }

    @JvmStatic
    fun formatTimeIntervalHourMin(length: Long): String {
        return if (length >= 60 * 60 * 1000) { // 1 hour in millis
            val timeUnit = TimeUnit.MILLISECONDS
            val numHour = timeUnit.toHours(length).toString()
            val numMinutes = timeUnit.toMinutes(length)
            String.format(Locale.getDefault(), "%sh:%02d", numHour, numMinutes % 60)
        } else {
            formatTimeIntervalMinSec(length)
        }
    }

    @JvmStatic
    fun formatTimeIntervalHourMinSec2(length: Long): String {
        val timeUnit = TimeUnit.MILLISECONDS
        val numHour = timeUnit.toHours(length)
        val numMinutes = timeUnit.toMinutes(length)
        val numSeconds = timeUnit.toSeconds(length)
        return if (numHour == 0L) {
            String.format(Locale.getDefault(), "%02d:%02d", numMinutes, numSeconds % 60)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d:%02d", numHour, numMinutes % 60, numSeconds % 60)
        }
    }

    @JvmStatic
    fun formatTimeIntervalMinSecMills(mills: Long): String {
        val min = mills / (60 * 1000)
        val sec = (mills / 1000) % 60
        val m = (mills / 10) % 100
        return String.format(Locale.getDefault(), "%02d:%02d:%02d", min, sec, m)
    }

    @JvmStatic
    fun formatTimeIntervalHourMinSec(mills: Long): String {
        val hour = mills / (60 * 60 * 1000)
        val min = mills / (60 * 1000) % 60
        val sec = (mills / 1000) % 60
        return when {
            hour != 0L -> String.format(Locale.getDefault(), "%02dh:%02dm:%02ds", hour, min, sec)
            min != 0L -> String.format(Locale.getDefault(), "%02dm:%02ds", min, sec)
            else -> String.format(Locale.getDefault(), "%02ds", sec)
        }
    }

    @JvmStatic
    fun formatDateSmartLocale(time: Long, ctx: Context): String {
        if (time <= 0) {
            return "Wrong date!"
        }
        val today = Calendar.getInstance()
        val date = Calendar.getInstance()
        date.timeInMillis = time
        if (isSameYear(today, date)) {
            if (isSameDay(today, date)) {
                return ctx.resources.getString(R.string.today)
            }
            today.add(Calendar.DAY_OF_YEAR, -1) // make yesterday
            return if (isSameDay(today, date)) {
                ctx.resources.getString(R.string.yesterday)
            } else {
                formatDayMonthLocale(time)
            }
        }
        return formatDateLocale(time)
    }

    /**
     * 判断两个日历是否为同一天（忽略时间）。
     */
    @JvmStatic
    fun isSameDay(cal1: Calendar?, cal2: Calendar?): Boolean {
        requireNotNull(cal1) { "The dates must not be null" }
        requireNotNull(cal2) { "The dates must not be null" }
        return cal1.get(Calendar.ERA) == cal2.get(Calendar.ERA) &&
                cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    /**
     * 判断两个日历是否为同一年（忽略时间）。
     */
    @JvmStatic
    fun isSameYear(cal1: Calendar?, cal2: Calendar?): Boolean {
        requireNotNull(cal1) { "The dates must not be null" }
        requireNotNull(cal2) { "The dates must not be null" }
        return cal1.get(Calendar.ERA) == cal2.get(Calendar.ERA) &&
                cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR)
    }

    @JvmStatic
    fun formatDateForNameVariant(time: Long): String = dateTimeFormat.format(Date(time))

    @JvmStatic
    fun formatDateForNameUS(time: Long): String = dateTimeFormatUS.format(Date(time))

    @JvmStatic
    fun formatDateForNameISO8601(time: Long): String = dateTimeFormatISO8601.format(Date(time))

    @JvmStatic
    fun formatDateTimeLocale(time: Long): String =
        java.text.DateFormat.getDateTimeInstance(
            java.text.DateFormat.SHORT,
            java.text.DateFormat.SHORT,
            Locale.getDefault()
        ).format(Date(time))

    @JvmStatic
    fun formatDateLocale(time: Long): String {
        val pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "ddMMMyyyy")
        return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(time))
    }

    @JvmStatic
    fun formatDayMonthLocale(time: Long): String {
        val pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "ddMMM")
        return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(time))
    }
}
