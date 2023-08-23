package me.ksanstone.wavesync.wavesync.gui.component.visualizer

import javafx.animation.Animation
import javafx.animation.KeyFrame
import javafx.animation.Timeline
import javafx.beans.binding.DoubleBinding
import javafx.beans.property.*
import javafx.beans.value.ObservableValue
import javafx.scene.canvas.Canvas
import javafx.scene.chart.NumberAxis
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
    private var frequencyAxis: NumberAxis
    private var smoother: IMagnitudeSmoother
    private var canvasHeightProperty: DoubleBinding

    val smoothing: FloatProperty = SimpleFloatProperty(0.91F)
    val startColor: ObjectProperty<Color> = SimpleObjectProperty(Color.LIGHTPINK)
    val endColor: ObjectProperty<Color> = SimpleObjectProperty(Color.AQUA)
    val scaling: FloatProperty = SimpleFloatProperty(10.0F)
    val cutoff: IntegerProperty = SimpleIntegerProperty(20000)
    val targetBarWidth: IntegerProperty = SimpleIntegerProperty(4)

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

        frequencyAxis = NumberAxis(0.0, 20000.0, 1000.0)
        frequencyAxis.prefHeight = 30.0
        setBottomAnchor(frequencyAxis, 0.0)
        setLeftAnchor(frequencyAxis, 0.0)
        setRightAnchor(frequencyAxis, 0.0)
        children.add(frequencyAxis)

        canvasHeightProperty = heightProperty().subtract(frequencyAxis.heightProperty())
        canvas = Canvas()
        canvas.widthProperty().bind(widthProperty())
        canvas.heightProperty().bind(canvasHeightProperty)
        setTopAnchor(canvas, 0.0)
        setLeftAnchor(canvas, 0.0)
        children.add(canvas)

        smoother = MultiplicativeSmoother()
        smoother.dataSize = 512

        smoothing.addListener { _ ->
            smoother.factor = smoothing.get().toDouble()
        }
    }

    private fun sizeFrequencyAxis() {
        frequencyAxis.lowerBound = 0.0
        frequencyAxis.upperBound = smoother.dataSize * (source.format.mix.rate.toDouble() / fftSize)
        println(frequencyAxis.upperBound)
    }

    private lateinit var source: SupportedCaptureSource
    private var fftSize: Int = 1024

    fun handleFFT(array: FloatArray, source: SupportedCaptureSource) {
        this.source = source
        this.fftSize = array.size * 2
        val size = source.trimResultTo(array.size * 2, cutoff.get())
        if (smoother.dataSize != size) {
            smoother.dataSize = size
            sizeFrequencyAxis()
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

        val canvasHeight = canvasHeightProperty.doubleValue()
        val gc = canvas.graphicsContext2D
        gc.fill = Color.rgb(33, 33, 33)
        gc.fillRect(0.0, 0.0, width, canvasHeight)

        val gap = 0
        val bufferLength = smoother.dataSize
        val step = calculateStep(targetBarWidth.get(), bufferLength, width)
        val totalBars = floor(bufferLength.toDouble() / step)
        val barWidth = (width - (totalBars - 1) * gap) / totalBars
        val buffer = smoother.data

        gc.fill = Color.LIGHTPINK
        var x = 0.0
        var y = 0.0

        for (i in 0 until bufferLength) {
            y = max(buffer[i].toDouble(), y)
            if (i % step != 0) continue
            val barHeight = y * canvasHeight

            val color = startColor.get().interpolate(endColor.get(), y)
            gc.fill = color
            gc.fillRect(x, canvasHeight - barHeight, barWidth, barHeight)
            x += barWidth + gap
            y = 0.0
        }
    }
}