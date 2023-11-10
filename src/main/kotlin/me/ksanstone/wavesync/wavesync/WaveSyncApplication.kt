package me.ksanstone.wavesync.wavesync

import javafx.application.Application
import javafx.application.Platform
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
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
        try {
            primaryStage = stage
            primaryStage.addEventHandler(KeyEvent.KEY_PRESSED) { event ->
                if (KeyCode.F11 == event.code) {
                    primaryStage.isFullScreen = !primaryStage.isFullScreen
                }
            }
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
        lateinit var primaryStage: Stage
    }
}

fun main(args: Array<String>) {
    System.setProperty("java.awt.headless", "false")
    Application.launch(WaveSyncApplication::class.java, *args)
}