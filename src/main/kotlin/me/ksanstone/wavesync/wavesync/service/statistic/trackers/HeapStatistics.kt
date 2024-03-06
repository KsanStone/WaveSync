package me.ksanstone.wavesync.wavesync.service.statistic.trackers

import javafx.collections.FXCollections
import javafx.collections.ObservableList
import me.ksanstone.wavesync.wavesync.service.statistic.HeapMeterThread
import me.ksanstone.wavesync.wavesync.service.statistic.Statistic
import me.ksanstone.wavesync.wavesync.service.statistic.StatisticBean
import org.springframework.stereotype.Component
import java.util.*

@Component
class HeapStatistics : StatisticBean {

    private val statList: ObservableList<Statistic> = FXCollections.observableArrayList()
    private var meterThread: HeapMeterThread? = null
    private var timer: Timer? = null
    private var memRate: Long = -1

    override fun getLocalizedNameKey() = "statistic.heap"

    override fun observableList() = statList


    override fun getStatistics(): List<Statistic> {
        val rt = Runtime.getRuntime()
        return listOf(
            Statistic.ofBytes("statistic.heap.max", rt.maxMemory()),
            Statistic.ofBytes("statistic.heap.total", rt.totalMemory()),
            Statistic.ofBytes("statistic.heap.used", rt.totalMemory() - rt.freeMemory()),
            Statistic.ofByteRate("statistic.heap.rate", memRate)
        )
    }

    private fun update() {
        observableList().setAll(getStatistics())
    }

    override fun start() {
        meterThread?.terminate()
        Thread {
            meterThread = HeapMeterThread({ rate ->
                memRate = rate
            }, 1000)
            meterThread!!.start()
        }.start()
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
        meterThread?.terminate()
        memRate = -1
        timer?.cancel()
        this.timer = null
    }

}