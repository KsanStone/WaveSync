package me.ksanstone.wavesync.wavesync.gui.component.visualizer

import javafx.animation.Animation
import javafx.animation.KeyFrame
import javafx.animation.Timeline
import javafx.beans.binding.DoubleBinding
import javafx.beans.property.*
import javafx.beans.value.ObservableValue
import javafx.collections.ListChangeListener
import javafx.scene.Node
import javafx.scene.canvas.Canvas
import javafx.scene.chart.NumberAxis
import javafx.scene.control.Tooltip
import javafx.scene.layout.AnchorPane
import javafx.scene.paint.Color
import javafx.scene.text.Text
import javafx.util.Duration
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.BAR_CUTOFF
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.BAR_LOW_PASS
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.BAR_SCALING
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.BAR_SMOOTHING
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.REFRESH_RATE
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.TARGET_BAR_WIDTH
import me.ksanstone.wavesync.wavesync.service.FourierMath
import me.ksanstone.wavesync.wavesync.service.PreferenceService
import me.ksanstone.wavesync.wavesync.service.SupportedCaptureSource
import me.ksanstone.wavesync.wavesync.service.smoothing.MagnitudeSmoother
import me.ksanstone.wavesync.wavesync.service.smoothing.MultiplicativeSmoother
import java.text.DecimalFormat
import kotlin.math.*


class BarVisualizer : AnchorPane() {

    private var canvas: Canvas
    private var frequencyAxis: NumberAxis
    private var smoother: MagnitudeSmoother
    private var canvasHeightProperty: DoubleBinding
    private val tooltip: Tooltip = Tooltip("---")

    val smoothing: FloatProperty = SimpleFloatProperty(BAR_SMOOTHING)
    val startColor: ObjectProperty<Color> = SimpleObjectProperty(Color.rgb(255,120, 246))
    val endColor: ObjectProperty<Color> = SimpleObjectProperty(Color.AQUA)
    val scaling: FloatProperty = SimpleFloatProperty(BAR_SCALING)
    val cutoff: IntegerProperty = SimpleIntegerProperty(BAR_CUTOFF)
    val lowPass: IntegerProperty = SimpleIntegerProperty(BAR_LOW_PASS)
    val targetBarWidth: IntegerProperty = SimpleIntegerProperty(TARGET_BAR_WIDTH)
    val framerate: IntegerProperty = SimpleIntegerProperty(REFRESH_RATE)
    val gap: IntegerProperty = SimpleIntegerProperty(0)

    init {
        stylesheets.add("/styles/bar-visualizer.css")

        heightProperty().addListener { _: ObservableValue<out Number?>?, _: Number?, _: Number? -> draw() }
        widthProperty().addListener { _: ObservableValue<out Number?>?, _: Number?, _: Number? -> draw() }

        val drawLoop = Timeline(
            KeyFrame(Duration.seconds(1.0 / framerate.get()), { draw() })
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
            drawLoop.keyFrames[0] = KeyFrame(Duration.seconds(1.0 / framerate.get()), { draw() })
            drawLoop.playFromStart()
        }

        frequencyAxis = NumberAxis(0.0, 20000.0, 1000.0)
        frequencyAxis.childrenUnmodifiable
            .addListener(ListChangeListener<Node?> { c: ListChangeListener.Change<out Node?> ->
                while (c.next()) {
                    if (c.wasAdded()) {
                        for (mark in c.addedSubList) {
                            if (mark is Text) {
                                val parsed = DecimalFormat("###,###.###").parse(mark.text).toDouble()
                                if (parsed == frequencyAxis.lowerBound) {
                                    mark.text = if(mark.text.contains(" ")) mark.text else " ".repeat(mark.text.length * 2) + mark.text
                                } else if (parsed == frequencyAxis.upperBound) {
                                    mark.text = if(mark.text.contains(" ")) mark.text else mark.text + " ".repeat(mark.text.length * 2)
                                }
                            }
                        }
                    }
                }
            } as ListChangeListener<Node?>?)

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
        cutoff.addListener { _ -> sizeFrequencyAxis() }
        lowPass.addListener { _ -> sizeFrequencyAxis() }

        setOnMouseMoved {
            if (source == null) {
                tooltip.text = "---"
            } else {
                val x = it.x
                val bufferLength = smoother.dataSize
                val step = calculateStep(targetBarWidth.get(), bufferLength, width)
                val totalBars = floor(bufferLength.toDouble() / step)
                val barWidth = (width - (totalBars - 1) * gap.get()) / totalBars
                val bar = floor(x / barWidth)
                val binStart = floor(bar * step).toInt()
                val binEnd = floor((bar+1) * step).toInt()
                val minFreq = FourierMath.frequencyOfBin(binStart, source!!.format.mix.rate, fftSize)
                val maxFreq = FourierMath.frequencyOfBin(binEnd, source!!.format.mix.rate, fftSize)
                tooltip.text = "Bar: $bar \nFFT: $binStart - $binEnd\nFreq: ${minFreq}Hz - ${maxFreq}Hz"
            }
        }

        Tooltip.install(this, tooltip)
    }

    fun registerPreferences(id: String, preferenceService: PreferenceService) {
        preferenceService.registerProperty(smoothing, "$id-smoothing")
        preferenceService.registerProperty(scaling, "$id-scaling")
        preferenceService.registerProperty(cutoff, "$id-cutoff")
        preferenceService.registerProperty(lowPass, "$id-lowPass")
        preferenceService.registerProperty(targetBarWidth, "$id-targetBarWidth")
    }

    private fun sizeFrequencyAxis() {
        if (source == null) return
        val upper = source!!.trimResultTo(fftSize, cutoff.get())
        val lower = source!!.bufferBeginningSkipFor(lowPass.get(), fftSize)
        frequencyAxis.lowerBound = FourierMath.frequencyOfBin(lower, source!!.format.mix.rate, fftSize).toDouble()
        frequencyAxis.upperBound = FourierMath.frequencyOfBin(upper, source!!.format.mix.rate, fftSize).toDouble()
    }

    private var source: SupportedCaptureSource? = null
    private var fftSize: Int = 1024
    private var frequencyBinSkip: Int = 0

    fun handleFFT(array: FloatArray, source: SupportedCaptureSource) {
        if (source != this.source) {
            this.source = source
            sizeFrequencyAxis()
        }
        this.fftSize = array.size * 2
        var size = source.trimResultTo(array.size * 2, cutoff.get())
        frequencyBinSkip = source.bufferBeginningSkipFor(lowPass.get(), array.size * 2)
        size = (size - frequencyBinSkip).coerceAtLeast(10)
        if (smoother.dataSize != size) {
            smoother.dataSize = size
        }

        smoother.data = array.slice(frequencyBinSkip until size).map { fl ->
            (fl * (scaling.get() * (1.0f - ln(fl + 0.2f) - 0.813f) + 1)).coerceAtMost(1.0f)
        }.toFloatArray()
    }

    private var lastDraw = System.nanoTime()

    private fun calculateStep(targetWidth: Int, bufferLength: Int, width: Double): Double {
        val estimatedWidth = width / bufferLength
        return (targetWidth.toDouble() / estimatedWidth).coerceAtLeast(1.0)
    }

    private fun draw() {
        val now = System.nanoTime()
        val deltaT = (now - lastDraw).toDouble() / 1_000_000_000.0
        lastDraw = now

        smoother.applySmoothing(deltaT)

        val canvasHeight = canvasHeightProperty.doubleValue()
        val gc = canvas.graphicsContext2D
        gc.clearRect(0.0, 0.0, width, canvasHeight)

        val localGap = gap.get()
        val bufferLength = smoother.dataSize
        val step = calculateStep(targetBarWidth.get(), bufferLength, width)
        val totalBars = floor(bufferLength.toDouble() / step)
        val barWidth = (width - (totalBars - 1) * localGap) / totalBars
        val buffer = smoother.data
        val padding = (barWidth * 0.3).coerceAtMost(1.0)


        gc.fill = Color.HOTPINK
        var x = 0.0
        var y = 0.0
        var stepAccumulator = 0.0

        for (i in 0 until bufferLength) {
            y = max(buffer[i].toDouble(), y)
            if (++stepAccumulator < step) continue
            stepAccumulator -= step

            val barHeight = y * canvasHeight
            val color = startColor.get().interpolate(endColor.get(), y)

            gc.fill = color
            gc.fillRect(x, canvasHeight - barHeight, barWidth + padding, barHeight)
            x += barWidth + localGap
            y = 0.0
        }
    }
}