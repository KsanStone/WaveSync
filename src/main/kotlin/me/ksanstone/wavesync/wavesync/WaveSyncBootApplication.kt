package me.ksanstone.wavesync.wavesync

import jakarta.annotation.PostConstruct
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.DEFAULT_REFRESH_RATE
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.PropertySource
import java.awt.DisplayMode
import java.awt.GraphicsEnvironment
import java.awt.HeadlessException
import java.util.concurrent.CompletableFuture


@Configuration
@PropertySource("/application.properties")
open class WaveSyncBootApplication(
    applicationContext: ConfigurableApplicationContext
) {

    val logger: Logger = LoggerFactory.getLogger("Main")
    private var targetRefreshRate: Int = DEFAULT_REFRESH_RATE

    init {
        WaveSyncBootApplication.applicationContext = applicationContext
    }

    @PostConstruct
    fun initialize() {
        CompletableFuture.runAsync {
            targetRefreshRate = findHighestRefreshRate()
            logger.info("Detected framerate: $targetRefreshRate")
        }
    }

    fun findHighestRefreshRate(): Int {
        return try {
            val ge = GraphicsEnvironment.getLocalGraphicsEnvironment()
            val gs = ge.screenDevices
            gs.map { it.displayMode.refreshRate }.filter { it != DisplayMode.REFRESH_RATE_UNKNOWN }.maxOrNull()
                ?: DEFAULT_REFRESH_RATE
        } catch (e: HeadlessException) {
            logger.warn("Headless exception")
            DEFAULT_REFRESH_RATE
        }
    }

    companion object {
        lateinit var applicationContext: ConfigurableApplicationContext
    }
}