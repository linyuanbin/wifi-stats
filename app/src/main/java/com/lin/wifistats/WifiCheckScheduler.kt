package com.lin.wifistats

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock

object WifiCheckScheduler {
    private const val INTERVAL_MS = 1 * 60 * 1000L // 1 分钟，与结算周期一致
    private const val REQUEST_CODE = 2000
    private const val REQUEST_CODE_SHOW = 2001

    /**
     * 优先使用 setAlarmClock（Doze 下仍会触发）；失败时回退到 setExactAndAllowWhileIdle，避免闪退。
     */
    fun schedule(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        val operation = PendingIntent.getBroadcast(
            context, REQUEST_CODE,
            Intent(context, WifiCheckReceiver::class.java), flags
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                val triggerAt = System.currentTimeMillis() + INTERVAL_MS
                val showIntent = PendingIntent.getActivity(
                    context, REQUEST_CODE_SHOW,
                    Intent(context, MainActivity::class.java), flags
                )
                am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, showIntent), operation)
                return
            } catch (_: Exception) { /* 部分机型可能抛异常，回退到旧方式 */
            }
        }
        val triggerAt = SystemClock.elapsedRealtime() + INTERVAL_MS
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAt,
                    operation
                )
            } else {
                am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, operation)
            }
        } catch (_: Exception) {
            am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, operation)
        }
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, WifiCheckReceiver::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        val pi = PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
        am.cancel(pi)
    }
}
