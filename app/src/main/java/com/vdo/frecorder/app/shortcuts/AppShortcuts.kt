package com.vdo.frecorder.app.shortcuts

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import com.vdo.frecorder.R
import com.vdo.frecorder.app.main.MainActivity
import com.vdo.frecorder.app.quickmic.QuickMicActivity

/**
 * 动态发布桌面长按快捷方式。
 *
 * 为什么不用 res/xml/shortcuts.xml：静态快捷方式里的 targetPackage 是写死的，
 * 而 debugConfig 变体的 applicationId 是 com.vdo.frecorder.debug，导致所有快捷方式
 * 在调试包里都解析不到目标，点了没有任何反应。改成代码生成，包名随 BuildConfig 走。
 *
 * 所有快捷方式都指向已导出的 MainActivity，由它再转发内部页面，
 * 避免把 QuickMicActivity 这类内部界面暴露给第三方应用。
 */
object AppShortcuts {

    private const val ID_RECORD = "recording_dynamic"
    private const val ID_QUICK_MIC = "quick_mic_dynamic"

    @JvmStatic
    fun refresh(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) {
            return
        }
        val manager = context.getSystemService(Context.SHORTCUT_SERVICE) as? ShortcutManager ?: return
        if (manager.isRateLimitingActive) {
            return
        }

        val record = ShortcutInfo.Builder(context, ID_RECORD)
            .setShortLabel(context.getString(R.string.start_recording))
            .setLongLabel(context.getString(R.string.start_recording))
            .setIcon(Icon.createWithResource(context, R.drawable.ic_record_red))
            .setIntent(
                Intent(context, MainActivity::class.java)
                    .setAction(ACTION_RUN)
            )
            .setRank(1)
            .build()

        val quickMic = ShortcutInfo.Builder(context, ID_QUICK_MIC)
            .setShortLabel(context.getString(R.string.shortcut_quick_mic_short))
            .setLongLabel(context.getString(R.string.shortcut_quick_mic_long))
            .setIcon(Icon.createWithResource(context, R.drawable.ic_mic_boost))
            .setIntent(
                Intent(context, MainActivity::class.java)
                    .setAction(QuickMicActivity.ACTION_QUICK_RECORD)
            )
            .setRank(0)
            .build()

        manager.dynamicShortcuts = listOf(quickMic, record)
    }

    const val ACTION_RUN = "android.intent.action.ACTION_RUN"
}
