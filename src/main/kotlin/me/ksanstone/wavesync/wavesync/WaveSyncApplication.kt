package me.ksanstone.wavesync.wavesync

import javafx.application.Application
import javafx.application.Platform
import javafx.stage.Stage
import me.ksanstone.wavesync.wavesync.event.StageReadyEvent
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.ConfigurableApplicationContext
import kotlin.system.exitProcess

class WaveSyncApplication : Application() {

    private lateinit var applicationContext: ConfigurableApplicationContext
    private val logger: Logger = LoggerFactory.getLogger("WaveSync")

    override fun init() {
        applicationContext = SpringApplicationBuilder(WaveSyncBootApplication::class.java).run()
    }

    override fun start(stage: Stage) {
        primaryStage = stage
        applicationContext.publishEvent(StageReadyEvent(stage))
    }

    override fun stop() {
        logger.info("Shutting down")
        applicationContext.close()
        Platform.exit()
        exitProcess(0)
    }

    companion object {
        lateinit var primaryStage: Stage
    }
}

fun main(args: Array<String>) {
    Application.launch(WaveSyncApplication::class.java, *args)
}