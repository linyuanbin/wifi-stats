package com.lin.wifistats

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * 前台服务保活：开启「未连接 WiFi 提醒」时运行。在服务内每 1 分钟执行一次检查，
 * 避免后台时 AlarmManager 被系统延迟/限制导致不推送。
 */
class WifiCheckService : Service() {

    private var checkThread: HandlerThread? = null
    private var checkHandler: Handler? = null
    private var checkRunnable: Runnable? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        val pending = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.foreground_service_title))
            .setContentText(getString(R.string.foreground_service_text))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pending)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        startPeriodicCheck()
        return START_STICKY
    }

    override fun onDestroy() {
        checkRunnable?.let { checkHandler?.removeCallbacks(it) }
        checkThread?.quitSafely()
        checkThread = null
        checkHandler = null
        checkRunnable = null
        super.onDestroy()
    }

    /** 在服务内每 1 分钟执行一次检查，后台时不再依赖 AlarmManager 广播 */
    private fun startPeriodicCheck() {
        if (checkHandler != null) return
        val thread = HandlerThread("WifiCheck").apply { start() }
        checkThread = thread
        val handler = Handler(thread.looper)
        checkHandler = handler
        val app = applicationContext
        val runnable = object : Runnable {
            override fun run() {
                if (!Prefs(app).isWifiCheckEnabled) return
                WifiCheckReceiver.runOneCheck(app)
                handler.postDelayed(this, INTERVAL_MS)
            }
        }
        checkRunnable = runnable
        // 首次立即执行一次，之后每 1 分钟执行
        handler.post(runnable)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.app_name),
                NotificationManager.IMPORTANCE_LOW
            )
            channel.setShowBadge(false)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "wifi_check_foreground"
        private const val NOTIFICATION_ID = 3000

        private const val INTERVAL_MS = 1 * 60 * 1000L
//        private const val INTERVAL_MS = 5 * 1000L

        fun startIfNeeded(context: Context) {
            if (!Prefs(context).isWifiCheckEnabled) return
            val intent = Intent(context, WifiCheckService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WifiCheckService::class.java))
        }
    }
}
