package com.lin.wifistats

import android.content.Context
import android.content.SharedPreferences

class Prefs(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var isWifiCheckEnabled: Boolean
        get() = prefs.getBoolean(KEY_WIFI_CHECK_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_WIFI_CHECK_ENABLED, value).apply()

    var lastLinConnectedAtMs: Long
        get() = prefs.getLong(KEY_LAST_lin_CONNECTED_AT, 0)
        set(value) = prefs.edit().putLong(KEY_LAST_lin_CONNECTED_AT, value).apply()

    /** 今日首次连接 LIN-INC 的时间（毫秒），仅当「今日」首次连上时更新 */
    var firstLinConnectedTodayMs: Long
        get() = prefs.getLong(KEY_FIRST_lin_CONNECTED_TODAY_MS, 0)
        set(value) = prefs.edit().putLong(KEY_FIRST_lin_CONNECTED_TODAY_MS, value).apply()

    var lastLinDisconnectedAtMs: Long
        get() = prefs.getLong(KEY_LAST_lin_DISCONNECTED_AT, 0)
        set(value) = prefs.edit().putLong(KEY_LAST_lin_DISCONNECTED_AT, value).apply()

    /** 钉钉机器人 Webhook URL，为空则不发送 */
    var dingTalkWebhookUrl: String?
        get() = prefs.getString(KEY_DINGTALK_WEBHOOK_URL, null)?.takeIf { it.isNotBlank() }
        set(value) = prefs.edit().putString(KEY_DINGTALK_WEBHOOK_URL, value ?: "").apply()

    /** 上次发送钉钉「Wi-Fi 断开」消息的时间（用于 30s 限流） */
    var lastDingTalkNotifyAtMs: Long
        get() = prefs.getLong(KEY_LAST_DINGTALK_NOTIFY_AT_MS, 0)
        set(value) = prefs.edit().putLong(KEY_LAST_DINGTALK_NOTIFY_AT_MS, value).apply()

    /** 上次弹出「未连接 WiFi」通知的时间（用于 30s 限流，避免堆积同时弹） */
    var lastWifiDisconnectedNotifyAtMs: Long
        get() = prefs.getLong(KEY_LAST_WIFI_DISCONNECTED_NOTIFY_AT_MS, 0)
        set(value) = prefs.edit().putLong(KEY_LAST_WIFI_DISCONNECTED_NOTIFY_AT_MS, value).apply()

    companion object {
        private const val PREFS_NAME = "lin_wifi_stats"
        private const val KEY_WIFI_CHECK_ENABLED = "wifi_check_enabled"
        private const val KEY_LAST_lin_CONNECTED_AT = "last_lin_connected_at"
        private const val KEY_FIRST_lin_CONNECTED_TODAY_MS = "first_lin_connected_today_ms"
        private const val KEY_LAST_lin_DISCONNECTED_AT = "last_lin_disconnected_at"
        private const val KEY_DINGTALK_WEBHOOK_URL = "dingtalk_webhook_url"
        private const val KEY_LAST_DINGTALK_NOTIFY_AT_MS = "last_dingtalk_notify_at_ms"
        private const val KEY_LAST_WIFI_DISCONNECTED_NOTIFY_AT_MS = "last_wifi_disconnected_notify_at_ms"
    }
}
