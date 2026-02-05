package com.lin.wifistats

data class StatItem(
    val period: String,
    val durationSeconds: Long,
    val rxBytes: Long,
    val txBytes: Long
) {
    fun durationFormatted(): String {
        val h = durationSeconds / 3600
        val m = (durationSeconds % 3600) / 60
        return if (h > 0) "${h}h ${m}m" else "${m} 分钟"
    }
    fun bytesFormatted(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        if (bytes < 1024 * 1024) return "%.2f KB".format(bytes / 1024.0)
        if (bytes < 1024 * 1024 * 1024) return "%.2f MB".format(bytes / (1024.0 * 1024))
        return "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
    }
}
