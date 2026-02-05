package com.lin.wifistats

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * 提高后台推送可靠性：请求忽略电池优化、打开应用设置页供用户手动放行。
 */
object BatteryHelper {

    /** 当前应用是否已被用户设为「忽略电池优化」 */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * 请求用户将本应用加入「不优化电量」白名单，减少后台被系统限制。
     * 需在 Activity 中调用，系统会弹出授权对话框。
     */
    fun requestIgnoreBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        if (isIgnoringBatteryOptimizations(context)) return true
        return try {
            val intent = Intent().apply {
                action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                data = Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    /** 打开本应用的系统设置页，便于用户手动设置「自启动 / 后台运行 / 不限制电量」等。 */
    fun openAppDetailSettings(context: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
