package me.ksanstone.wavesync.wavesync.service

import jakarta.annotation.PostConstruct
import javafx.beans.property.SimpleLongProperty
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import kotlin.properties.Delegates
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

/**
 * Tracks the total amount of time the program has been running for.
 */
@Service
class TotalRuntimeTracker(
    private val preferenceService: PreferenceService
) {

    private val totalNanos = SimpleLongProperty(0)
    private var timeStarted by Delegates.notNull<Long>()
    private var clockStart by Delegates.notNull<Long>()
    private val logger = LoggerFactory.getLogger(javaClass)

    @PostConstruct
    fun postConstruct() {
        preferenceService.registerProperty(totalNanos, "totalNanos", this.javaClass)
        timeStarted = System.nanoTime()
        clockStart = System.nanoTime()

        Runtime.getRuntime().addShutdownHook(Thread {
            destroy()
        })
    }

    fun getTotal(): Duration {
        return (totalNanos.value + (System.nanoTime() - clockStart)).nanoseconds
    }

    private fun getCurrent(): Duration {
        return (System.nanoTime() - timeStarted).nanoseconds
    }

    @Scheduled(fixedRate = 60 * 1000)
    private fun save() {
        val now = System.nanoTime()
        val timeRan = now - clockStart
        clockStart = now
        totalNanos.set(totalNanos.value + timeRan)
    }

    private fun destroy() {
        save()
        logger.info("Ran for: ${getCurrent()}, Total: ${totalNanos.value.nanoseconds}")
    }

}