package com.lin.wifistats

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.android.material.tabs.TabLayout

class ReportFragment : Fragment() {

    private lateinit var store: WifiStatsStore
    private lateinit var tabsReport: TabLayout
    private lateinit var containerStats: FrameLayout
    private var currentTab = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_report, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        store = WifiStatsStore(requireContext())
        tabsReport = view.findViewById(R.id.tabs_report)
        containerStats = view.findViewById(R.id.container_stats)

        tabsReport.addTab(tabsReport.newTab().setText(getString(R.string.tab_daily)))
        tabsReport.addTab(tabsReport.newTab().setText(getString(R.string.tab_weekly)))
        tabsReport.addTab(tabsReport.newTab().setText(getString(R.string.tab_monthly)))
        tabsReport.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentTab = tab?.position ?: 0
                refreshStats()
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        view.findViewById<View>(R.id.btn_refresh_report).setOnClickListener { refreshStats() }
        view.findViewById<View>(R.id.btn_fix_report).setOnClickListener { showFixConfirmDialog() }
        refreshStats()
    }

    override fun onResume() {
        super.onResume()
        refreshStats()
    }

    private fun refreshStats() {
        containerStats.removeAllViews()
        val view = layoutInflater.inflate(R.layout.fragment_stats, containerStats, false)
        containerStats.addView(view)

        val chart = view.findViewById<LineChart>(R.id.chart)
        val recycler = view.findViewById<RecyclerView>(R.id.recycler)
        recycler.layoutManager = LinearLayoutManager(requireContext())
        val adapter = StatsAdapter()
        recycler.adapter = adapter

        when (currentTab) {
            0 -> {
                val data = store.getDailyData(30)
                val items = data.map {
                    StatItem(
                        it.first,
                        it.second.durationSeconds,
                        it.second.rxBytes,
                        it.second.txBytes
                    )
                }
                setupChart(chart, items, "日期")
                adapter.setData(items.reversed())
            }

            1 -> {
                val data = store.getWeeklyData(12)
                val items = data.map {
                    StatItem(
                        it.first,
                        it.second.durationSeconds,
                        it.second.rxBytes,
                        it.second.txBytes
                    )
                }
                setupChart(chart, items, "周")
                adapter.setData(items.reversed())
            }

            2 -> {
                val data = store.getMonthlyData(12)
                val items = data.map {
                    StatItem(
                        it.first,
                        it.second.durationSeconds,
                        it.second.rxBytes,
                        it.second.txBytes
                    )
                }
                setupChart(chart, items, "月份")
                adapter.setData(items.reversed())
            }
        }
    }

    private fun setupChart(chart: LineChart, items: List<StatItem>, xLabel: String) {
        if (items.isEmpty()) {
            chart.clear()
            chart.setNoDataText("暂无数据")
            chart.invalidate()
            return
        }
        val entriesDuration =
            items.mapIndexed { i, it -> Entry(i.toFloat(), it.durationSeconds.toFloat() / 3600f) }
        val entriesRx =
            items.mapIndexed { i, it -> Entry(i.toFloat(), it.rxBytes.toFloat() / (1024 * 1024)) }
        val entriesTx =
            items.mapIndexed { i, it -> Entry(i.toFloat(), it.txBytes.toFloat() / (1024 * 1024)) }

        val ctx = requireContext()
        val setDuration = LineDataSet(entriesDuration, "连接时长(h)").apply {
            color = ContextCompat.getColor(ctx, android.R.color.holo_blue_dark)
            setCircleColor(ContextCompat.getColor(ctx, android.R.color.holo_blue_dark))
            lineWidth = 2f
            setDrawValues(true)
        }
//        val setRx = LineDataSet(entriesRx, "下载(MB)").apply {
//            color = ContextCompat.getColor(ctx, android.R.color.holo_green_dark)
//            setCircleColor(ContextCompat.getColor(ctx, android.R.color.holo_green_dark))
//            lineWidth = 2f
//            setDrawValues(true)
//        }
//        val setTx = LineDataSet(entriesTx, "上传(MB)").apply {
//            color = ContextCompat.getColor(ctx, android.R.color.holo_orange_dark)
//            setCircleColor(ContextCompat.getColor(ctx, android.R.color.holo_orange_dark))
//            lineWidth = 2f
//            setDrawValues(true)
//        }

//        chart.data = LineData(setDuration, setRx, setTx)
        chart.data = LineData(setDuration)
        chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        chart.xAxis.valueFormatter = IndexAxisValueFormatter(items.map { it.period })
        chart.xAxis.setAvoidFirstLastClipping(true)
        chart.axisRight.isEnabled = false
        chart.description.isEnabled = false
        chart.legend.isWordWrapEnabled = true
        chart.invalidate()
    }

    private fun showFixConfirmDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.fix_dialog_title)
            .setMessage(R.string.fix_dialog_message)
            .setPositiveButton(R.string.fix_dialog_confirm) { _, _ ->
                store.clearTodayDurations(requireContext())
                refreshStats()
            }
            .setNegativeButton(R.string.fix_dialog_cancel, null)
            .show()
    }

}
