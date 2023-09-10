package me.ksanstone.wavesync.wavesync

import jakarta.annotation.PostConstruct
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.REFRESH_RATE
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.ConfigurableApplicationContext
import java.awt.DisplayMode
import java.awt.GraphicsEnvironment
import java.awt.HeadlessException


@SpringBootApplication
class WaveSyncBootApplication(
  applicationContext: ConfigurableApplicationContext
) {
    
    val logger: Logger = LoggerFactory.getLogger("Main")
    var targetRefreshRate: Int = REFRESH_RATE

    init {
        WaveSyncBootApplication.applicationContext = applicationContext
    }

    @PostConstruct
    fun initialize() {
        targetRefreshRate = findHighestRefreshRate()
        logger.info("Detected framerate: $targetRefreshRate")
    }

    fun findHighestRefreshRate(): Int {
        return try {
            val ge = GraphicsEnvironment.getLocalGraphicsEnvironment()
            val gs = ge.screenDevices
            gs.map { it.displayMode.refreshRate }.filter { it != DisplayMode.REFRESH_RATE_UNKNOWN }.maxOrNull() ?: REFRESH_RATE
        } catch (e: HeadlessException) {
            logger.warn("Headless exception")
            REFRESH_RATE
        }
    }

    companion object {
        lateinit var applicationContext: ConfigurableApplicationContext
    }
}