package me.ksanstone.wavesync.wavesync

import javafx.application.Application
import javafx.application.Platform
import javafx.stage.Stage
import javafx.util.Duration
import me.ksanstone.wavesync.wavesync.event.StageReadyEvent
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.ConfigurableApplicationContext
import java.lang.management.ManagementFactory
import kotlin.properties.Delegates
import kotlin.system.exitProcess


class WaveSyncApplication : Application() {

    override fun init() {
        startTime = System.currentTimeMillis()
        logTimePoint("JFX init")
        applicationContext = SpringApplicationBuilder(WaveSyncBootApplication::class.java).run()
    }

    override fun start(stage: Stage) {
        try {
            logTimePoint("JFX start")
            primaryStage = stage
            applicationContext.publishEvent(StageReadyEvent(stage))
        } catch (t: Throwable) {
            t.printStackTrace()
            exitProcess(1)
        }
    }

    override fun stop() {
        logger.info("Shutting down")
        applicationContext.close()
        Platform.exit()
        exitProcess(0)
    }

    companion object {
        lateinit var applicationContext: ConfigurableApplicationContext
        private val logger: Logger = LoggerFactory.getLogger("WaveSync")
        lateinit var primaryStage: Stage
        var startTime by Delegates.notNull<Long>()

        fun logTimePoint(message: String) {
            val time = System.currentTimeMillis() - startTime
            val uptime = ManagementFactory.getRuntimeMXBean().uptime.toDouble()
            logger.info("$message in ${Duration.millis(time.toDouble())} (process running for ${Duration.millis(uptime)})")
        }
    }
}

fun main(args: Array<String>) {
    System.setProperty("java.awt.headless", "false")
    Application.launch(WaveSyncApplication::class.java, *args)
}