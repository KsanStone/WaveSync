package me.ksanstone.wavesync.wavesync.service.statistic.trackers

import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.util.Duration
import me.ksanstone.wavesync.wavesync.service.TotalRuntimeTracker
import me.ksanstone.wavesync.wavesync.service.statistic.Statistic
import me.ksanstone.wavesync.wavesync.service.statistic.StatisticBean
import org.springframework.stereotype.Component
import java.lang.management.ManagementFactory
import java.util.*

@Component
class RuntimeStatistics(
    private val totalRuntimeTracker: TotalRuntimeTracker
) : StatisticBean {
    private val statList: ObservableList<Statistic> = FXCollections.observableArrayList()
    private var timer: Timer? = null
    private val runtimeBean = ManagementFactory.getRuntimeMXBean()
    private val threadBean = ManagementFactory.getThreadMXBean()

    override fun getLocalizedNameKey() = "statistic.runtime"

    override fun observableList() = statList
    override fun getStatistics(): List<Statistic> {

        return listOf(
            Statistic.ofDuration("statistic.runtime.uptime", Duration.millis(runtimeBean.uptime.toDouble())),
            Statistic.ofDuration(
                "statistic.runtime.totalUptime",
                Duration.millis(totalRuntimeTracker.getTotal().inWholeMilliseconds.toDouble())
            ),
            Statistic("statistic.runtime.vm", "${runtimeBean.vmVersion} ${runtimeBean.vmVendor}"),
            Statistic(
                "statistic.runtime.threads",
                "${threadBean.threadCount} (${threadBean.daemonThreadCount} daemon)"
            ),
        )
    }

    private fun update() {
        observableList().setAll(getStatistics())
    }


    override fun start() {
        timer?.cancel()
        this.timer = Timer().also {
            it.scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    update()
                }
            }, 0, 1000)
        }
    }

    override fun pause() {
        timer?.cancel()
        this.timer = null
    }
}