package me.ksanstone.wavesync.wavesync.service.statistic

import javafx.util.Duration
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

        fun ofGcCollections(localizedKey: String, collections: Long, time: Duration): Statistic {
            return Statistic(localizedKey, "$collections ${formatDuration(time)}")
        }

        fun ofDuration(localizedKey: String, duration: Duration): Statistic {
            return Statistic(localizedKey, formatDuration(duration))
        }

        private fun formatDuration(duration: Duration): String {
            val millis = duration.toMillis()
            return String.format(
                "%02d:%02d:%02d.%03d",
                (millis / 3600000).toInt(),
                (millis % 3600000 / 60000).toInt(),
                (millis % 60000 / 1000).toInt(),
                (millis % 1000).toInt()
            )
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