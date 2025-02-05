package me.ksanstone.wavesync.wavesync

import javafx.animation.AnimationTimer
import javafx.application.Application
import javafx.application.Platform
import javafx.application.Preloader
import javafx.stage.Stage
import javafx.util.Duration
import me.ksanstone.wavesync.wavesync.event.StageReadyEvent
import me.ksanstone.wavesync.wavesync.utility.BannerUtil
import me.ksanstone.wavesync.wavesync.utility.FPSCounter
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import java.lang.management.ManagementFactory
import kotlin.properties.Delegates
import kotlin.system.exitProcess


class WaveSyncApplication : Application() {

    override fun init() {
        startTime = System.currentTimeMillis()
        BannerUtil.banner()
        logTimePoint("JFX init")
        counter = FPSCounter()
        applicationContext = AnnotationConfigApplicationContext("me.ksanstone.wavesync")
        logTimePoint("Context Instantiated")
    }

    override fun start(stage: Stage) {
        try {
            logTimePoint("JFX start")
            primaryStage = stage
            applicationContext.publishEvent(StageReadyEvent(stage))
            notifyPreloader(Preloader.ProgressNotification(1.0))
            startPulseCounter()
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

    private fun startPulseCounter() {
        object : AnimationTimer() {
            private var lastT = System.nanoTime()
            override fun handle(now: Long) { // Fired every pulse, measure their count
                val elapsedTime = now - lastT
                counter.tick(elapsedTime.toDouble().div(1_000_000_000))
                lastT = now
            }
        }.start()
    }

    companion object {
        lateinit var applicationContext: ConfigurableApplicationContext
        private val logger: Logger = LoggerFactory.getLogger("WaveSync")
        lateinit var primaryStage: Stage
        var counter: FPSCounter by Delegates.notNull()
        var startTime by Delegates.notNull<Long>()
        var jfxPulseFrequency = 60.0

        fun logTimePoint(message: String) {
            val time = System.currentTimeMillis() - startTime
            val uptime = ManagementFactory.getRuntimeMXBean().uptime.toDouble()
            logger.info("$message in ${Duration.millis(time.toDouble())} (process running for ${Duration.millis(uptime)})")
        }
    }
}

fun main(args: Array<String>) {
    System.setProperty("java.awt.headless", "false")
    System.setProperty("javafx.preloader", "me.ksanstone.wavesync.wavesync.WavePreloader")
    System.setProperty("prism.vsync", "false")

//    System.setProperty("javafx.animation.fullspeed", "true")
//    WaveSyncApplication.jfxPulseFrequency = 1000.0

    Application.launch(WaveSyncApplication::class.java, *args)
}