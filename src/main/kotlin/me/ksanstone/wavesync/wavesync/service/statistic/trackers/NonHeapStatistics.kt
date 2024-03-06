package me.ksanstone.wavesync.wavesync.service.statistic.trackers

import javafx.collections.FXCollections
import javafx.collections.ObservableList
import me.ksanstone.wavesync.wavesync.service.statistic.Statistic
import me.ksanstone.wavesync.wavesync.service.statistic.StatisticBean
import org.springframework.stereotype.Component
import java.lang.management.ManagementFactory
import java.util.*

@Component
class NonHeapStatistics : StatisticBean {

    private val statList: ObservableList<Statistic> = FXCollections.observableArrayList()
    private val memoryBean = ManagementFactory.getMemoryMXBean()
    private var timer: Timer? = null

    override fun getLocalizedNameKey() = "statistic.nonHeap"

    override fun observableList() = statList

    override fun getStatistics(): List<Statistic> {
        return listOf(
            Statistic.ofBytes("statistic.nonHeap.total", memoryBean.nonHeapMemoryUsage.committed),
            Statistic.ofBytes("statistic.nonHeap.used", memoryBean.nonHeapMemoryUsage.used)
        )
    }

    private fun update() {
        observableList().setAll(getStatistics())
    }

    override fun start() {
        timer?.cancel()
        this.timer = Timer().also {
            it.schedule(object : TimerTask() {
                override fun run() {
                    update()
                }
            }, 0, 100)
        }
    }

    override fun pause() {
        timer?.cancel()
        this.timer = null
    }
}