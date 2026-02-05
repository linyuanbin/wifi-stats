package com.lin.wifistats

import android.content.Context
import android.net.TrafficStats
import org.json.JSONObject

/**
 * 按天存储 LIN-INC 的连接时长与上下行流量。
 * 键：日期字符串 "yyyy-MM-dd"
 * 值：{ durationSeconds, rxBytes, txBytes }
 */
class WifiStatsStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val keyDaily = "daily_"
    private val keyLastRx = "last_rx"
    private val keyLastTx = "last_tx"
    private val keyConnectedAt = "connected_at"
    private val keyLastSampleAt = "last_sample_at"
    private val keyWasLin = "was_lin"

    private var connectedAtMs: Long = 0
    private var lastSampleAtMs: Long = 0
    private var lastRx: Long = 0
    private var lastTx: Long = 0
    private var isLinConnected: Boolean = false

    init {
        connectedAtMs = prefs.getLong(keyConnectedAt, 0)
        lastSampleAtMs = prefs.getLong(keyLastSampleAt, 0)
        lastRx = prefs.getLong(keyLastRx, 0)
        lastTx = prefs.getLong(keyLastTx, 0)
        isLinConnected = prefs.getBoolean(keyWasLin, false)
    }

    /** 每次调用前从 prefs 同步会话状态，避免多入口（Activity/Fragment/Receiver）重复累计 */
    private fun reloadSessionFromPrefs() {
        connectedAtMs = prefs.getLong(keyConnectedAt, 0)
        lastSampleAtMs = prefs.getLong(keyLastSampleAt, 0)
        lastRx = prefs.getLong(keyLastRx, 0)
        lastTx = prefs.getLong(keyLastTx, 0)
        isLinConnected = prefs.getBoolean(keyWasLin, false)
    }

    /** 上次检查时是否连着 LIN-INC（用于解锁/唤醒时 SSID 暂未知则沿用此状态，避免误判断开导致少算） */
    fun getWasLinConnected(): Boolean = prefs.getBoolean(keyWasLin, false)

    /**
     * 在每次检查 WiFi 时调用：若当前连的是 LIN-INC 则累计；否则先结算上一条 Lin 连接再清空状态。
     * @return true 表示刚刚从 LIN-INC 断开，调用方可据此发送通知
     */
    fun onWifiStateChanged(context: Context, connectedToLinNow: Boolean): Boolean {
        reloadSessionFromPrefs()
        val today = todayKey()
        val justDisconnected = isLinConnected && !connectedToLinNow
        if (justDisconnected) {
            Prefs(context).lastLinDisconnectedAtMs = System.currentTimeMillis()
            flushToDay(today)
            clearSession()
        }
        if (connectedToLinNow) {
            if (!isLinConnected) {
                val now = System.currentTimeMillis()
                Prefs(context).lastLinConnectedAtMs = now
                val p = Prefs(context)
                if (p.firstLinConnectedTodayMs == 0L || dayKeyOf(p.firstLinConnectedTodayMs) != today) {
                    p.firstLinConnectedTodayMs = now
                }
                startSession()
            }
            accumulateToDay(today)
        }
        isLinConnected = connectedToLinNow
        prefs.edit().putBoolean(keyWasLin, connectedToLinNow).apply()
        return justDisconnected
    }

    private fun startSession() {
        val now = System.currentTimeMillis()
        connectedAtMs = now
        lastSampleAtMs = now
        lastRx = TrafficStats.getTotalRxBytes()
        lastTx = TrafficStats.getTotalTxBytes()
        prefs.edit()
            .putLong(keyConnectedAt, connectedAtMs)
            .putLong(keyLastSampleAt, lastSampleAtMs)
            .putLong(keyLastRx, lastRx)
            .putLong(keyLastTx, lastTx)
            .apply()
    }

    private fun clearSession() {
        connectedAtMs = 0
        lastSampleAtMs = 0
        lastRx = 0
        lastTx = 0
        prefs.edit()
            .remove(keyConnectedAt)
            .remove(keyLastSampleAt)
            .remove(keyLastRx)
            .remove(keyLastTx)
            .apply()
    }

    /** 只把「自上次采样至今」的时长与流量写入当天，避免与 accumulateToDay 已写入的重复累计 */
    private fun flushToDay(dayKey: String) {
        if (connectedAtMs == 0L) return
        val now = System.currentTimeMillis()
        val durationSec = (now - lastSampleAtMs) / 1000
        val rx = TrafficStats.getTotalRxBytes()
        val tx = TrafficStats.getTotalTxBytes()
        if (durationSec > 0 || rx != lastRx || tx != lastTx) {
            addToDay(
                dayKey,
                durationSec.coerceAtLeast(0),
                (rx - lastRx).coerceAtLeast(0),
                (tx - lastTx).coerceAtLeast(0)
            )
        }
        clearSession()
    }

    private fun accumulateToDay(dayKey: String) {
        if (connectedAtMs == 0L) return
        val now = System.currentTimeMillis()
        val durationSec = (now - lastSampleAtMs) / 1000
        lastSampleAtMs = now
        val rx = TrafficStats.getTotalRxBytes()
        val tx = TrafficStats.getTotalTxBytes()
        val deltaRx = (rx - lastRx).coerceAtLeast(0)
        val deltaTx = (tx - lastTx).coerceAtLeast(0)
        if (durationSec > 0 || deltaRx > 0 || deltaTx > 0) {
            addToDay(dayKey, durationSec.coerceAtLeast(0), deltaRx, deltaTx)
        }
        lastRx = rx
        lastTx = tx
        prefs.edit()
            .putLong(keyLastSampleAt, lastSampleAtMs)
            .putLong(keyLastRx, lastRx)
            .putLong(keyLastTx, lastTx)
            .apply()
    }

    private fun addToDay(dayKey: String, durationSec: Long, rxBytes: Long, txBytes: Long) {
        if (durationSec < 0) return
        val existing = getDayRaw(dayKey)
        val duration = existing.durationSeconds + durationSec
        val rx = existing.rxBytes + rxBytes
        val tx = existing.txBytes + txBytes
        prefs.edit().putString(keyDaily + dayKey, toJson(duration, rx, tx)).apply()
    }

    private fun getDayRaw(dayKey: String): DayRecord {
        val json = prefs.getString(keyDaily + dayKey, null) ?: return DayRecord(0, 0, 0)
        return parseJson(json)
    }

    fun getDay(dayKey: String): DayRecord = getDayRaw(dayKey)

    /** 今日 LIN-INC 在线时长（秒），含当前未结算的会话（仅加「自上次采样至今」，避免与已存今日重复） */
    fun getTodayDurationLiveSeconds(): Long {
        reloadSessionFromPrefs()
        val today = getDayRaw(todayKey()).durationSeconds
        val now = System.currentTimeMillis()
        val session = if (isLinConnected && lastSampleAtMs > 0) {
            (now - lastSampleAtMs) / 1000
        } else 0L
        return today + session
    }

    fun getDailyKeysSorted(): List<String> {
        val keys = prefs.all.keys
            .filter { it.startsWith(keyDaily) }
            .map { it.removePrefix(keyDaily) }
            .sorted()
        return keys
    }

    fun getDailyData(limit: Int = 30): List<Pair<String, DayRecord>> {
        val keys = getDailyKeysSorted().takeLast(limit)
        return keys.map { it to getDayRaw(it) }
    }

    fun getWeeklyData(weeks: Int = 12): List<Pair<String, DayRecord>> {
        val daily = getDailyData(weeks * 7)
        val byWeek = daily.groupBy { weekKey(it.first) }
        return byWeek.map { (week, list) ->
            week to DayRecord(
                list.sumOf { it.second.durationSeconds },
                list.sumOf { it.second.rxBytes },
                list.sumOf { it.second.txBytes }
            )
        }.sortedBy { it.first }
    }

    fun getMonthlyData(months: Int = 12): List<Pair<String, DayRecord>> {
        val daily = getDailyData(months * 31)
        val byMonth = daily.groupBy { it.first.substring(0, 7) }
        return byMonth.map { (month, list) ->
            month to DayRecord(
                list.sumOf { it.second.durationSeconds },
                list.sumOf { it.second.rxBytes },
                list.sumOf { it.second.txBytes }
            )
        }.sortedBy { it.first }
    }

    private fun weekKey(dateStr: String): String {
        val parts = dateStr.split("-")
        if (parts.size != 3) return dateStr
        val y = parts[0].toIntOrNull() ?: 0
        val m = parts[1].toIntOrNull() ?: 0
        val d = parts[2].toIntOrNull() ?: 0
        val cal = java.util.Calendar.getInstance()
        cal.set(y, m - 1, d)
        val week = cal.get(java.util.Calendar.WEEK_OF_YEAR)
        return "${y}-W%02d".format(week)
    }

    private fun todayKey(): String {
        val cal = java.util.Calendar.getInstance()
        return "%04d-%02d-%02d".format(
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH) + 1,
            cal.get(java.util.Calendar.DAY_OF_MONTH)
        )
    }

    private fun dayKeyOf(ms: Long): String {
        if (ms <= 0) return ""
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = ms
        return "%04d-%02d-%02d".format(
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH) + 1,
            cal.get(java.util.Calendar.DAY_OF_MONTH)
        )
    }

    private fun toJson(durationSeconds: Long, rxBytes: Long, txBytes: Long): String {
        return JSONObject().apply {
            put("d", durationSeconds)
            put("rx", rxBytes)
            put("tx", txBytes)
        }.toString()
    }

    private fun parseJson(s: String): DayRecord {
        return try {
            val o = JSONObject(s)
            DayRecord(
                o.optLong("d", 0),
                o.optLong("rx", 0),
                o.optLong("tx", 0)
            )
        } catch (_: Exception) {
            DayRecord(0, 0, 0)
        }
    }

    companion object {
        private const val PREFS_NAME = "lin_wifi_stats_store"
    }
}

data class DayRecord(
    val durationSeconds: Long,
    val rxBytes: Long,
    val txBytes: Long
) {
    val totalBytes: Long get() = rxBytes + txBytes
}
