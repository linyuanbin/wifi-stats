package com.lin.wifistats

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build

object WifiUtils {

    /** 当前配置的统计目标 SSID，默认 LIN-INC */
    fun getTargetSsid(context: Context): String = Prefs(context).targetSsid

    fun isWifiConnected(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    /** 规范化 SSID：去引号、去首尾空格、转成可比较字符串 */
    private fun normalizeSsid(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val s = raw.replace("\"", "").trim()
        if (s.isEmpty() || s.equals("<unknown ssid>", ignoreCase = true)) return null
        return s
    }

    /** 当前连接的 WiFi SSID（优先用 deprecated API，兼容性更好） */
    fun getCurrentSsid(context: Context): String? {
        if (!isWifiConnected(context)) return null
        val appContext = context.applicationContext
        val wifi = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        val info = wifi.connectionInfo ?: return null
        var ssid = normalizeSsid(info.ssid)
        if (!ssid.isNullOrEmpty()) return ssid
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return null
            val caps = cm.getNetworkCapabilities(network) ?: return null
            val transportInfo = caps.transportInfo
            if (transportInfo is WifiInfo) {
                val raw = transportInfo.ssid?.toString()
                ssid = normalizeSsid(raw)
                if (!ssid.isNullOrEmpty()) return ssid
            }
        }
        return null
    }

    /** 是否连接配置的目标 SSID：规范化后做不区分大小写比较，并兼容含多余空格/字符 */
    fun isConnectedToLinInc(context: Context): Boolean {
        val ssid = getCurrentSsid(context) ?: return false
        val normalized = ssid.trim().lowercase()
        val target = getTargetSsid(context).trim().lowercase()
        if (target.isEmpty()) return false
        return normalized == target || normalized.contains(target)
    }
}
