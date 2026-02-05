package com.lin.wifistats

import android.content.Context
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * 在 Wi-Fi 断开/周期检查通知时，若用户配置了钉钉 Webhook，则异步 POST 一条文本消息。
 * 30 秒钟内同一类型只发一次，避免堆积同时发送。
 */
object DingTalkNotifier {
    private const val MSG = "小钉提醒：Wi-Fi已断开"
    private const val THROTTLE_MS = 30 * 1000L //消息推送限流30s

    fun notifyWifiDisconnected(context: Context) {
        val prefs = Prefs(context)
        val url = prefs.dingTalkWebhookUrl ?: return
        if (url.isBlank()) return
        val now = System.currentTimeMillis()
        if (now - prefs.lastDingTalkNotifyAtMs < THROTTLE_MS) return
        prefs.lastDingTalkNotifyAtMs = now
        Thread {
            try {
                val body = JSONObject()
                    .put("msgtype", "text")
                    .put("text", JSONObject().put("content", MSG))
                    .toString()
                val bodyBytes = body.toByteArray(StandardCharsets.UTF_8)
                val u = URL(url)
                val conn = u.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                conn.doOutput = true
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                conn.setFixedLengthStreamingMode(bodyBytes.size)
                conn.outputStream.use { it.write(bodyBytes) }
                conn.responseCode
                conn.disconnect()
            } catch (_: Exception) { /* 静默失败 */
            }
        }.start()
    }
}
