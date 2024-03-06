package me.ksanstone.wavesync.wavesync.service.statistic

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean

@Service
class StatisticMonitor(
    val statisticBeans: List<StatisticBean>
) {

    private val logger = LoggerFactory.getLogger(this.javaClass)
    private val listeners = mutableListOf<WeakReference<Any>>()
    private val running = AtomicBoolean(false)

    @PostConstruct
    fun init() {
        logger.info("Registered ${statisticBeans.size} statistic beans")
    }

    fun register(listener: Any): List<StatisticBean> {
        if (listeners.any { it.get() == listener }) throw IllegalArgumentException("Already registered")
        listeners.add(WeakReference(listener))
        start()
        return statisticBeans
    }

    fun unregister(listener: Any) {
        if (!listeners.removeIf { it.get() == listener }) throw IllegalArgumentException("Already unregistered")
        if (listeners.size == 0) pause()
    }

    private fun start() {
        if (!running.compareAndSet(false, true)) return
        statisticBeans.forEach { it.start() }
        logger.info("Started monitoring")
    }


    private fun pause() {
        if (!running.compareAndSet(true, false)) return
        statisticBeans.forEach { it.pause() }
        logger.info("Stopped monitoring")
    }

}