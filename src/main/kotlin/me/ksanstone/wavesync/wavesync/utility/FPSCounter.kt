package me.ksanstone.wavesync.wavesync.utility

import javafx.animation.KeyFrame
import javafx.animation.Timeline
import javafx.beans.property.SimpleDoubleProperty
import javafx.util.Duration
import java.util.concurrent.atomic.AtomicInteger

class FPSCounter {

    val current = SimpleDoubleProperty(0.0)
    private val counter = AtomicInteger(0)
    private var lastCount = System.nanoTime()

    fun tick() {
        counter.incrementAndGet()
    }

    private var timer: Timeline = Timeline(
        KeyFrame(Duration.seconds(1.0), {
            val now = System.nanoTime()
            val delta = (now - lastCount).toDouble() / 1_000_000_000.0
            lastCount = now
            current.set(counter.getAndSet(0).toDouble() * delta)
        })
    )

    init {
        timer.cycleCount = Timeline.INDEFINITE
        timer.play()
    }

    fun close() {
        timer.stop()
    }
}