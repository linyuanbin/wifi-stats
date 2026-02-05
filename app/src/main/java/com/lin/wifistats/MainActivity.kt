package com.lin.wifistats

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class MainActivity : AppCompatActivity() {

    private lateinit var store: WifiStatsStore
    private lateinit var tabsMain: TabLayout
    private lateinit var pager: ViewPager2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_main)
            val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
            if (toolbar != null) setSupportActionBar(toolbar)
            store = WifiStatsStore(this)
            tabsMain = findViewById(R.id.tabs_main)
            pager = findViewById(R.id.pager)
            pager.adapter = MainPagerAdapter(this)
            TabLayoutMediator(tabsMain, pager) { tab, position ->
                tab.text = when (position) {
                    0 -> getString(R.string.tab_network_status)
                    1 -> getString(R.string.tab_report)
                    else -> ""
                }
            }.attach()
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "onCreate error", e)
            throw e
        }
    }

    override fun onResume() {
        super.onResume()
        if (::store.isInitialized) {
            try {
                val connectedToLin = WifiUtils.isConnectedToLinInc(this)
                val wifiConnected = WifiUtils.isWifiConnected(this)
                val ssidUnknown = wifiConnected && WifiUtils.getCurrentSsid(this).isNullOrEmpty()
                val effectiveConnectedToLin = if (ssidUnknown) store.getWasLinConnected() else connectedToLin
                store.onWifiStateChanged(this, effectiveConnectedToLin)
                // 不在前台弹通知；1 分钟周期结算由服务/闹钟负责，onResume 时已结算一次
                if (Prefs(this).isWifiCheckEnabled) {
                    WifiCheckScheduler.schedule(this)
                    WifiCheckService.startIfNeeded(this)
                } else {
                    WifiCheckService.stop(this)
                }
            } catch (_: Exception) {}
        }
        requestLocationPermissionIfNeeded()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_settings) {
            startActivity(Intent(this, SettingsActivity::class.java))
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    /** Android 9+ 获取当前 Wi-Fi SSID 需要定位权限，在 onResume 中请求避免启动时闪退 */
    private fun requestLocationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) return
        try {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), REQUEST_LOCATION)
        } catch (_: Exception) {}
    }

    companion object {
        private const val REQUEST_LOCATION = 3000
    }

    private class MainPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
        override fun getItemCount(): Int = 2

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> NetworkStatusFragment()
                1 -> ReportFragment()
                else -> NetworkStatusFragment()
            }
        }
    }
}
