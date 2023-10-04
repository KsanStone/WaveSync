package me.ksanstone.wavesync.wavesync.gui.utility

import javafx.animation.Animation
import javafx.animation.KeyFrame
import javafx.animation.Timeline
import javafx.beans.property.IntegerProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.value.ObservableValue
import javafx.scene.canvas.Canvas
import javafx.scene.canvas.GraphicsContext
import javafx.scene.layout.AnchorPane
import javafx.util.Duration
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults

abstract class AutoCanvas() : AnchorPane() {

    protected var canvas: Canvas = Canvas()

    val framerate: IntegerProperty = SimpleIntegerProperty(ApplicationSettingDefaults.REFRESH_RATE)

    init {
        heightProperty().addListener { _: ObservableValue<out Number?>?, _: Number?, _: Number? -> drawCall() }
        widthProperty().addListener { _: ObservableValue<out Number?>?, _: Number?, _: Number? -> drawCall() }

        canvas.widthProperty().bind(widthProperty())
        canvas.heightProperty().bind(heightProperty())

        setTopAnchor(canvas, 0.0)
        setLeftAnchor(canvas, 0.0)

        children.add(canvas)

        initializeDrawLoop()
    }

    private fun initializeDrawLoop() {
        val drawLoop = Timeline(
            KeyFrame(Duration.seconds(1.0 / framerate.get()), { drawCall() })
        )

        drawLoop.cycleCount = Timeline.INDEFINITE
        drawLoop.play()

        parentProperty().addListener { _, _, newValue ->
            if (newValue != null && drawLoop.status != Animation.Status.RUNNING)
                drawLoop.play()
            else if (newValue == null && drawLoop.status == Animation.Status.RUNNING)
                drawLoop.pause()
        }

        framerate.addListener { _ ->
            drawLoop.pause()
            drawLoop.keyFrames[0] = KeyFrame(Duration.seconds(1.0 / framerate.get()), { drawCall() })
            drawLoop.playFromStart()
        }
    }

    private var lastDraw = System.nanoTime()

    private fun drawCall() {
        val now = System.nanoTime()
        val deltaT = (now - lastDraw).toDouble() / 1_000_000_000.0
        lastDraw = now

        this.draw(canvas.graphicsContext2D, deltaT, now, canvas.width, canvas.height)
    }

    protected abstract fun draw(gc: GraphicsContext, deltaT: Double, now: Long, width: Double, height: Double)

}