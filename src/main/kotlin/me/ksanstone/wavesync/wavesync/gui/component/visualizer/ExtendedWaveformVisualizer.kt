package me.ksanstone.wavesync.wavesync.gui.component.visualizer

import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.css.CssMetaData
import javafx.css.Styleable
import javafx.css.StyleableProperty
import javafx.css.StyleablePropertyFactory
import javafx.fxml.FXMLLoader
import javafx.scene.canvas.GraphicsContext
import javafx.scene.chart.NumberAxis
import javafx.scene.layout.HBox
import javafx.scene.layout.Pane
import javafx.scene.paint.Color
import javafx.util.Duration
import me.ksanstone.wavesync.wavesync.WaveSyncBootApplication
import me.ksanstone.wavesync.wavesync.gui.controller.visualizer.extendedWaveform.ExtendedWaveformSettingsController
import me.ksanstone.wavesync.wavesync.gui.utility.AutoCanvas
import me.ksanstone.wavesync.wavesync.service.FourierMath
import me.ksanstone.wavesync.wavesync.service.LocalizationService
import me.ksanstone.wavesync.wavesync.service.PreferenceService
import me.ksanstone.wavesync.wavesync.service.SupportedCaptureSource
import me.ksanstone.wavesync.wavesync.service.fftScaling.LinearFFTScalar
import me.ksanstone.wavesync.wavesync.service.fftScaling.LinearFFTScalarParams
import me.ksanstone.wavesync.wavesync.utility.RollingBuffer
import kotlin.math.max
import kotlin.math.min

class ExtendedWaveformVisualizer : AutoCanvas() {

    val bufferDuration: ObjectProperty<Duration> = SimpleObjectProperty(Duration.seconds(1.0))

    private var effectiveBufferSampleRate = 48000
    private var sourceRate = SimpleIntegerProperty(48000)
    private var buffer: RollingBuffer<Float> = RollingBuffer(100) { 0.0F }
    private var computedBuffer: RollingBuffer<Float> = RollingBuffer(1) { 0.0F }
    private var lastWritten = 0L
    private var bufferPos = 0
    private var accumulator = 0.0

    private val waveColor: StyleableProperty<Color> =
        FACTORY.createStyleableColorProperty(this, "waveColor", "-fx-color") { vis -> vis.waveColor }
    private val rmsColor: StyleableProperty<Color> =
        FACTORY.createStyleableColorProperty(this, "waveColor", "-fx-rms-color") { vis -> vis.rmsColor }

    private val rmsScalar = LinearFFTScalar()

    init {
        canvasContainer.xAxisShown.value = true
        canvasContainer.yAxisShown.value = true

        xAxis.minorTickCount = 10
        (xAxis as NumberAxis).tickUnit = 1.0
        yAxis.lowerBound = -1.0
        yAxis.upperBound = 1.0
        (yAxis as NumberAxis).tickUnit = 0.2

        sourceRate.addListener { _, _, v ->
            effectiveBufferSampleRate = 48000.coerceAtMost(v.toInt())
            resizeBuffer(bufferDuration.get(), effectiveBufferSampleRate)
            resetBuffer()
        }

        bufferDuration.addListener { _, _, _ ->
            resizeBuffer(bufferDuration.get(), effectiveBufferSampleRate)
            resetBuffer()
            sizeXAxis()
        }

        resizeBuffer(bufferDuration.get(), effectiveBufferSampleRate)
        sizeXAxis()

        rmsScalar.update(LinearFFTScalarParams(6.5F))

        styleClass.add("extended-waveform-visualizer")
        stylesheets.add("/styles/waveform-visualizer.css")
    }

    override fun registerPreferences(id: String, preferenceService: PreferenceService) {
        preferenceService.registerDurationProperty(bufferDuration, "bufferDuration", this.javaClass, id)
        preferenceService.registerProperty(canvasContainer.xAxisShown, "xAxisShown", this.javaClass, id)
        preferenceService.registerProperty(canvasContainer.yAxisShown, "yAxisShown", this.javaClass, id)
        preferenceService.registerProperty(canvasContainer.horizontalLinesVisible, "horizontalLinesVisible", this.javaClass, id)
        preferenceService.registerProperty(canvasContainer.verticalLinesVisible, "verticalLinesVisible", this.javaClass, id)
    }

    override fun initializeSettingMenu() {
        val loader = FXMLLoader()
        loader.location = javaClass.classLoader.getResource("layout/waveform-ex")
        loader.resources =
            WaveSyncBootApplication.applicationContext.getBean(LocalizationService::class.java).getDefault()
        val controls: HBox =
            loader.load(javaClass.classLoader.getResourceAsStream("layout/waveform-ex/waveformExSettings.fxml"))
        val controller: ExtendedWaveformSettingsController = loader.getController()
        controller.extendedWaveformChartSettingsController.initialize(this)
        controlPane.children.add(controls)
    }

    private fun sizeXAxis() {
        xAxis.lowerBound = -bufferDuration.get().toSeconds()
        xAxis.upperBound = 0.0
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
        this.buffer = RollingBuffer(newSize.toInt()) { 0.0f }
    }

    private fun calculateDiffPoints() {
        val step = (buffer.size.toDouble() / width)
        val samplesWritten = buffer.written - lastWritten
        lastWritten = buffer.written
        bufferPos -= samplesWritten.toInt()
        bufferPos = bufferPos.coerceAtLeast(0)

        var cMin = 1.0F
        var cMax = -1.0F
        var tempPos = bufferPos

        while (true) {
            if (accumulator < step) {
                val i = tempPos
                if (i >= buffer.size) break
                val current = buffer[i]
                cMin = min(cMin, current)
                cMax = max(cMax, current)
                tempPos++
                accumulator++
                continue
            }

            val rms = FourierMath.calcRMS(buffer, bufferPos, tempPos - bufferPos)
            bufferPos = tempPos

            computedBuffer.insert(cMin)
            computedBuffer.insert(cMax)
            computedBuffer.insert(rms.toFloat())

            accumulator -= step
            cMin = 1.0F
            cMax = -1.0F
        }
    }

    private fun resetBuffer() {
        lastWritten = buffer.tail
        accumulator = 0.0
        bufferPos = buffer.size
    }

    private fun handleWidth(width: Double) {
        val px = width.toInt()
        if (px <= 0) return
        if (computedBuffer.size != px * 3) {
            computedBuffer = RollingBuffer(px * 3) { 0F }
            resetBuffer()
            calculateDiffPoints()
        } else {
            calculateDiffPoints()
        }
    }

    override fun draw(gc: GraphicsContext, deltaT: Double, now: Long, width: Double, height: Double) {
        gc.clearRect(0.0, 0.0, width, height)
        handleWidth(width)

        val rangeBreadth = 2.0

        for (i in 0 until computedBuffer.size / 3) {
            val cMin = computedBuffer[i * 3]
            val cMax = computedBuffer[i * 3 + 1]
            val rms = rmsScalar.scale(computedBuffer[i * 3 + 2])
            val yEnd = (cMax + 1.0F).toDouble() / rangeBreadth * height
            val yStart = (cMin + 1.0F).toDouble() / rangeBreadth * height
            val rmsH = rms * height / 2
            val rmsStart = max(yStart, (height - rmsH) / 2)
            val rmsEnd = min(yEnd, rmsH + (height - rmsH) / 2)

            gc.fill = waveColor.value
            gc.fillRect(i.toDouble(), yStart, 1.0, yEnd - yStart)
            gc.fill = rmsColor.value
            gc.fillRect(i.toDouble(), rmsStart, 1.0, rmsEnd - rmsStart)
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