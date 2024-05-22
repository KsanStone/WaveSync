package me.ksanstone.wavesync.wavesync.gui.component.visualizer

import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.css.CssMetaData
import javafx.css.Styleable
import javafx.css.StyleableProperty
import javafx.css.StyleablePropertyFactory
import javafx.scene.canvas.GraphicsContext
import javafx.scene.layout.Pane
import javafx.scene.paint.Color
import javafx.util.Duration
import me.ksanstone.wavesync.wavesync.gui.utility.AutoCanvas
import me.ksanstone.wavesync.wavesync.service.SupportedCaptureSource
import me.ksanstone.wavesync.wavesync.utility.RollingBuffer
import kotlin.math.max
import kotlin.math.min

class ExtendedWaveformVisualizer : AutoCanvas() {

    val bufferDuration: ObjectProperty<Duration> = SimpleObjectProperty(Duration.seconds(5.0))

    private var effectiveBufferSampleRate = 48000
    private var sourceRate = SimpleIntegerProperty(48000)
    private var buffer: RollingBuffer<Float> = RollingBuffer(100, 0.0F)

    private val waveColor: StyleableProperty<Color> =
        FACTORY.createStyleableColorProperty(this, "waveColor", "-fx-color") { vis -> vis.waveColor }

    init {
        canvasContainer.xAxisShown.value = false
        canvasContainer.yAxisShown.value = false

        sourceRate.addListener { _, _, v ->
            effectiveBufferSampleRate = 48000.coerceAtMost(v.toInt())
            resizeBuffer(bufferDuration.get(), effectiveBufferSampleRate)
        }

        resizeBuffer(bufferDuration.get(), effectiveBufferSampleRate)

        styleClass.add("extended-waveform-visualizer")
        stylesheets.add("/styles/waveform-visualizer.css")
    }

    fun handleSamples(samples: FloatArray, source: SupportedCaptureSource) {
        if (isPaused) return
        if (source.rate != sourceRate.get()) sourceRate.set(source.rate)
        if (source.rate <= effectiveBufferSampleRate) {
            buffer.insert(samples.toTypedArray())
        } else {
            val scaleDownFactor = source.rate / effectiveBufferSampleRate
            for (i in samples.indices step scaleDownFactor)
                buffer.insert(samples[i])
        }
    }

    private fun resizeBuffer(time: Duration, rate: Int) {
        val newSize = rate * time.toSeconds()
        this.buffer = RollingBuffer(newSize.toInt(), 0.0f)
    }

    override fun draw(gc: GraphicsContext, deltaT: Double, now: Long, width: Double, height: Double) {
        gc.clearRect(0.0, 0.0, width, height)

        val step = buffer.size.toDouble() / width
        var lineX = 0
        var buffPos = 0
        var accumulator = 0.0

        val drop: Int = (step - buffer.written % step).toInt().coerceIn(0, buffer.size - 50)

        var cMin = 1.0F
        var cMax = -1.0F
        val rangeBreadth = 2

        gc.fill = waveColor.value

        while (lineX < width) {
            if (accumulator < step) {
                val i = buffPos + drop
                if (i >= buffer.size) break
                val current = buffer[i]
                cMin = min(cMin, current)
                cMax = max(cMax, current)
                buffPos++
                accumulator++
                continue
            }

            val yEnd = (cMax + 1.0F).toDouble() / rangeBreadth * height
            val yStart = (cMin + 1.0F).toDouble() / rangeBreadth * height

            gc.fillRect(lineX.toDouble(), yStart, 1.0, yEnd - yStart)

            accumulator -= step
            lineX++
            cMin = 1.0F
            cMax = -1.0F
        }
    }

    override fun getCssMetaData(): List<CssMetaData<out Styleable?, *>> {
        return FACTORY.cssMetaData
    }

    companion object {
        private val FACTORY: StyleablePropertyFactory<ExtendedWaveformVisualizer> =
            StyleablePropertyFactory<ExtendedWaveformVisualizer>(
                Pane.getClassCssMetaData()
            )

        @Suppress("unused")
        fun getClassCssMetaData(): List<CssMetaData<out Styleable?, *>> {
            return FACTORY.cssMetaData
        }
    }

}