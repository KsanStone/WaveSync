package me.ksanstone.wavesync.wavesync.service.statistic.trackers

import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.util.Duration
import me.ksanstone.wavesync.wavesync.service.statistic.Statistic
import me.ksanstone.wavesync.wavesync.service.statistic.StatisticBean
import me.ksanstone.wavesync.wavesync.service.toDirectKey
import org.springframework.stereotype.Component
import java.lang.management.ManagementFactory
import java.util.*

@Component
class GCStatistics : StatisticBean {
    private val statList: ObservableList<Statistic> = FXCollections.observableArrayList()
    private var timer: Timer? = null
    private val gcBeans = ManagementFactory.getGarbageCollectorMXBeans()

    override fun getLocalizedNameKey() = "statistic.gc"

    override fun observableList() = statList
    override fun getStatistics(): List<Statistic> {
        val totalTime = gcBeans.stream().filter { it.collectionTime != -1L }.mapToLong { it.collectionTime }.sum()
        val totalCollections =
            gcBeans.stream().filter { it.collectionCount != -1L }.mapToLong { it.collectionCount }.sum()
        return gcBeans.map {
            Statistic.ofGcCollections(
                it.name.toDirectKey(),
                it.collectionCount,
                Duration.millis(it.collectionTime.toDouble())
            )
        }.toMutableList().apply {
            this.add(
                Statistic.ofGcCollections(
                    "statistic.gc.total",
                    totalCollections,
                    Duration.millis(totalTime.toDouble())
                )
            )
        }
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
            }, 0, 500)
        }
    }

    override fun pause() {
        timer?.cancel()
        this.timer = null
    }
}