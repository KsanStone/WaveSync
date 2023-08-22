package me.ksanstone.wavesync.wavesync.gui.component.visualizer

import javafx.animation.Animation
import javafx.animation.KeyFrame
import javafx.animation.Timeline
import javafx.beans.property.*
import javafx.beans.value.ObservableValue
import javafx.scene.canvas.Canvas
import javafx.scene.layout.AnchorPane
import javafx.scene.paint.Color
import javafx.util.Duration
import me.ksanstone.wavesync.wavesync.service.SupportedCaptureSource
import me.ksanstone.wavesync.wavesync.service.smoothing.IMagnitudeSmoother
import me.ksanstone.wavesync.wavesync.service.smoothing.MultiplicativeSmoother
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max


class BarVisualizer : AnchorPane() {

    private var canvas: Canvas
    private var smoother: IMagnitudeSmoother

    val smoothing: FloatProperty = SimpleFloatProperty(1F)
    val startColor: ObjectProperty<Color> = SimpleObjectProperty(Color.LIGHTPINK)
    val endColor: ObjectProperty<Color> = SimpleObjectProperty(Color.AQUA)
    val scaling: FloatProperty = SimpleFloatProperty(10.0F)
    val cutoff: IntegerProperty = SimpleIntegerProperty(20000)
    val targetBarWidth: IntegerProperty = SimpleIntegerProperty(7)

    init {
        heightProperty().addListener { _: ObservableValue<out Number?>?, _: Number?, _: Number? -> draw() }
        widthProperty().addListener { _: ObservableValue<out Number?>?, _: Number?, _: Number? -> draw() }
        val drawLoop = Timeline(
            KeyFrame(Duration.millis(16.0), { draw() })
        )
        drawLoop.cycleCount = Timeline.INDEFINITE
        drawLoop.play()

        parentProperty().addListener { _, _, newValue ->
            if (newValue != null && drawLoop.status != Animation.Status.RUNNING)
                drawLoop.play()
            else if (newValue == null && drawLoop.status == Animation.Status.RUNNING)
                drawLoop.pause()
        }

        canvas = Canvas()
        setBottomAnchor(canvas, 0.0)
        setLeftAnchor(canvas, 0.0)
        setRightAnchor(canvas, 0.0)
        setTopAnchor(canvas, 0.0)
        canvas.widthProperty().bind(widthProperty())
        canvas.heightProperty().bind(heightProperty())
        children.add(canvas)

        smoother = MultiplicativeSmoother()
        smoother.dataSize = 512

        smoothing.addListener { _ ->
            smoother.factor = smoothing.get().toDouble()
        }
    }

    fun handleFFT(array: FloatArray, source: SupportedCaptureSource) {
        val size = source.trimResultTo(array.size * 2, cutoff.get())
        if (smoother.dataSize != size) {
            smoother.dataSize = size
        }

        smoother.data = array.slice(0 until size).map { fl ->
            (fl * (scaling.get() * (1.0f - ln(fl + 0.2f) - 0.813f) + 1) ).coerceAtMost(1.0f)
        }.toFloatArray()
    }

    private var lastDraw = System.nanoTime()

    private fun calculateStep(targetWidth: Int, bufferLength: Int, width: Double): Int {
        val estimatedWidth = width / bufferLength
        return Math.round(targetWidth.toDouble() / estimatedWidth).toInt().coerceAtLeast(1)
    }

    private fun draw() {
        val now = System.nanoTime()
        val deltaT = (now - lastDraw).toDouble() / 1_000_000_000.0
        lastDraw = now

        smoother.applySmoothing(deltaT)

        val gc = canvas.graphicsContext2D
        gc.fill = Color.rgb(33, 33, 33)
        gc.fillRect(0.0, 0.0, width, height)

        val gap = 0
        val bufferLength = smoother.dataSize
        val step = calculateStep(targetBarWidth.get(), bufferLength, width)
        val totalBars = floor(bufferLength.toDouble() / step)
        val barWidth = (width - (totalBars - 1) * gap) / totalBars
        val barHeightScalar = height
        val buffer = smoother.data

        gc.fill = Color.LIGHTPINK
        var x = 0.0
        var y = 0.0

        for (i in 0 until bufferLength) {
            y = max(buffer[i].toDouble(), y)
            if (i % step != 0) continue
            val barHeight = y * barHeightScalar

            val color = startColor.get().interpolate(endColor.get(), y)
            gc.fill = color
            gc.fillRect(x, height - barHeight, barWidth + 1, barHeight)
            x += barWidth + gap
            y = 0.0
        }
    }
}