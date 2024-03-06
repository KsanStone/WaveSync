package me.ksanstone.wavesync.wavesync.service.statistic

import kotlin.math.ln
import kotlin.math.pow

data class Statistic(
    val localizedKey: String,
    val value: String
) {
    companion object {

        fun ofByteRate(localizedKey: String, bytes: Long): Statistic {
            return Statistic(localizedKey, formatBytes(bytes) + "/s")
        }

        fun ofBytes(localizedKey: String, bytes: Long): Statistic {
            return Statistic(localizedKey, formatBytes(bytes))
        }

        private fun formatBytes(bytes: Long): String {
            val units = arrayOf("B", "KiB", "MiB", "GiB", "TiB", "PiB", "EiB", "ZiB", "YiB")
            if (bytes == 0L) return "0 B"
            if (bytes < 0) return "N/A"
            val digitGroups = (ln(bytes.toDouble()) / ln(1024.0)).toInt()
            return String.format("%.2f %s", bytes / 1024.0.pow(digitGroups.toDouble()), units[digitGroups])
        }
    }
}