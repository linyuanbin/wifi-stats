package com.lin.wifistats

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class WifiCheckReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        runOneCheck(context)
        WifiCheckScheduler.schedule(context)
    }

    companion object {
        private const val CHANNEL_ID = "wifi_check"
        private const val CHANNEL_ID_DISCONNECT = "lin_disconnect"
        private const val NOTIFICATION_ID = 1001
        private const val NOTIFICATION_ID_DISCONNECT = 1002

        /** 执行一次 Wi-Fi 检查与提醒（供 Receiver 与前台 Service 共用）。先做今日在线时长结算，再根据开关决定是否发通知。 */
        fun runOneCheck(context: Context) {
            val store = WifiStatsStore(context)
            val connectedToLin = WifiUtils.isConnectedToLinInc(context)
            val wifiConnected = WifiUtils.isWifiConnected(context)
            val ssidUnknown = wifiConnected && WifiUtils.getCurrentSsid(context).isNullOrEmpty()
            val effectiveConnectedToLin = if (ssidUnknown) store.getWasLinConnected() else connectedToLin
            val justDisconnected = store.onWifiStateChanged(context, effectiveConnectedToLin)

            val prefs = Prefs(context)
            if (!prefs.isWifiCheckEnabled) {
                WifiCheckScheduler.cancel(context)
                return
            }
            if (!wifiConnected) {
                // 优先发送钉钉，系统通知被拦截时用户仍能通过钉钉收到提醒
                DingTalkNotifier.notifyWifiDisconnected(context)
                if (justDisconnected) {
                    showLinDisconnectedNotification(context)
                }
                val prefs = Prefs(context)
                val now = System.currentTimeMillis()
                val throttleMs = 30 * 1000L // 消息限流 30s
                if (now - prefs.lastWifiDisconnectedNotifyAtMs >= throttleMs) {
                    prefs.lastWifiDisconnectedNotifyAtMs = now
                    createChannel(
                        context,
                        CHANNEL_ID,
                        context.getString(R.string.wifi_not_connected_title),
                        highImportance = true
                    )
                    val periodicNotificationId =
                        NOTIFICATION_ID + (now / throttleMs).toInt() % 100000
                    val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                        .setSmallIcon(android.R.drawable.ic_dialog_alert)
                        .setContentTitle(context.getString(R.string.wifi_not_connected_title))
                        .setContentText(context.getString(R.string.wifi_not_connected_message))
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setAutoCancel(true)
                    try {
                        NotificationManagerCompat.from(context)
                            .notify(periodicNotificationId, builder.build())
                    } catch (_: SecurityException) {
                    }
                }
            }
        }

        private fun createChannel(
            context: Context,
            channelId: String,
            name: String,
            highImportance: Boolean = false
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val importance =
                    if (highImportance) NotificationManager.IMPORTANCE_HIGH else NotificationManager.IMPORTANCE_DEFAULT
                val channel = NotificationChannel(channelId, name, importance)
                (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                    .createNotificationChannel(channel)
            }
        }

        /** 仅当确认为「真正断开 LIN-INC」时显示通知（解锁后 SSID 暂时未知不弹） */
        fun shouldShowDisconnectNotification(context: Context): Boolean {
            if (WifiUtils.isWifiConnected(context)) {
                val ssid = WifiUtils.getCurrentSsid(context)
                if (ssid.isNullOrEmpty()) return false
                if (ssid.equals("LIN-INC", ignoreCase = true)) return false
            }
            return true
        }

        /** 每次断开 LIN-INC 时由调用方触发一次通知（钉钉已在上层优先发送） */
        fun showLinDisconnectedNotification(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID_DISCONNECT,
                    context.getString(R.string.lin_disconnected_title),
                    NotificationManager.IMPORTANCE_HIGH
                )
                (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                    .createNotificationChannel(channel)
            }
            val builder = NotificationCompat.Builder(context, CHANNEL_ID_DISCONNECT)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(context.getString(R.string.lin_disconnected_title))
                .setContentText(context.getString(R.string.lin_disconnected_message))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
            try {
                NotificationManagerCompat.from(context)
                    .notify(NOTIFICATION_ID_DISCONNECT, builder.build())
            } catch (_: SecurityException) {
            }
        }
    }
}
