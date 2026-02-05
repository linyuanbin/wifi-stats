package com.lin.wifistats

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.switchmaterial.SwitchMaterial
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NetworkStatusFragment : Fragment() {

    private lateinit var prefs: Prefs
    private lateinit var store: WifiStatsStore
    private lateinit var textWifiStatus: TextView
    private lateinit var textTodayDuration: TextView
    private lateinit var textFirstLinConnectedToday: TextView
    private lateinit var textLastLinDisconnected: TextView
    private lateinit var switchWifiCheck: SwitchMaterial
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    private val handler = Handler(Looper.getMainLooper())
    private var tickRunnable: Runnable? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_network_status, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = Prefs(requireContext())
        store = WifiStatsStore(requireContext())

        textWifiStatus = view.findViewById(R.id.text_wifi_status)
        textTodayDuration = view.findViewById(R.id.text_today_duration)
        textFirstLinConnectedToday = view.findViewById(R.id.text_first_lin_connected_today)
        textLastLinDisconnected = view.findViewById(R.id.text_last_lin_disconnected)
        switchWifiCheck = view.findViewById(R.id.switch_wifi_check)

        view.findViewById<View>(R.id.btn_refresh_status).setOnClickListener { manualRefresh() }

        switchWifiCheck.isChecked = prefs.isWifiCheckEnabled
        switchWifiCheck.setOnCheckedChangeListener { _, isChecked ->
            prefs.isWifiCheckEnabled = isChecked
            if (isChecked) {
                WifiCheckScheduler.schedule(requireContext())
                WifiCheckService.startIfNeeded(requireContext())
            } else {
                WifiCheckScheduler.cancel(requireContext())
                WifiCheckService.stop(requireContext())
            }
        }

        updateStatus()
        updateTodayDuration()
        updateLastLinTimes()
    }

    /** 手动刷新当前连接状态和今日统计时长 */
    private fun manualRefresh() {
        val ctx = requireContext()
        val connectedToLin = WifiUtils.isConnectedToLinInc(ctx)
        store.onWifiStateChanged(ctx, connectedToLin)
        updateStatus()
        updateTodayDuration()
        updateLastLinTimes()
    }

    override fun onResume() {
        super.onResume()
        val ctx = requireContext()
        val connectedToLin = WifiUtils.isConnectedToLinInc(ctx)
        val justDisconnected = store.onWifiStateChanged(ctx, connectedToLin)
        if (justDisconnected && WifiCheckReceiver.shouldShowDisconnectNotification(ctx)) {
            WifiCheckReceiver.showLinDisconnectedNotification(ctx)
        }
        if (prefs.isWifiCheckEnabled) {
            WifiCheckScheduler.schedule(ctx)
            WifiCheckService.startIfNeeded(ctx)
        } else {
            WifiCheckService.stop(ctx)
        }
        updateStatus()
        updateTodayDuration()
        updateLastLinTimes()
        startTicking()
    }

    override fun onPause() {
        super.onPause()
        stopTicking()
    }

    private fun startTicking() {
        stopTicking()
        tickRunnable = object : Runnable {
            override fun run() {
                if (!isAdded) return
                updateTodayDuration()
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(tickRunnable!!)
    }

    private fun stopTicking() {
        tickRunnable?.let { handler.removeCallbacks(it) }
        tickRunnable = null
    }

    private fun updateStatus() {
        val ctx = requireContext()
        textWifiStatus.text = when {
            WifiUtils.isWifiConnected(ctx) -> {
                val ssid = WifiUtils.getCurrentSsid(ctx)
                if (ssid.isNullOrEmpty()) {
                    getString(R.string.wifi_status_connected, getString(R.string.wifi_status_unknown_ssid)) +
                        "\n" + getString(R.string.wifi_status_ssid_hint)
                } else {
                    getString(R.string.wifi_status_connected, ssid)
                }
            }
            else -> getString(R.string.wifi_status_none)
        }
    }

    private fun updateLastLinTimes() {
        val firstToday = prefs.firstLinConnectedTodayMs
        val disconnectedAt = prefs.lastLinDisconnectedAtMs
        textFirstLinConnectedToday.text = if (firstToday > 0) timeFormat.format(Date(firstToday)) else getString(R.string.never)
        textLastLinDisconnected.text = if (disconnectedAt > 0) timeFormat.format(Date(disconnectedAt)) else getString(R.string.never)
    }

    private fun updateTodayDuration() {
        val sec = store.getTodayDurationLiveSeconds()
        val h = sec / 3600
        val m = (sec % 3600) / 60
        textTodayDuration.text = if (h > 0) "${h} 小时 ${m} 分钟" else "${m} 分钟"
    }
}
