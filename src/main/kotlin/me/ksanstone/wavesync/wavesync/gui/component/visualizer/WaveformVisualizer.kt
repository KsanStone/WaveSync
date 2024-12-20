package me.ksanstone.wavesync.wavesync.gui.component.visualizer

import javafx.application.Platform
import javafx.beans.property.*
import javafx.css.CssMetaData
import javafx.css.Styleable
import javafx.css.StyleableProperty
import javafx.css.StyleablePropertyFactory
import javafx.fxml.FXMLLoader
import javafx.scene.canvas.GraphicsContext
import javafx.scene.chart.NumberAxis
import javafx.scene.control.Label
import javafx.scene.layout.HBox
import javafx.scene.layout.Pane
import javafx.scene.paint.Color
import javafx.util.Duration
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.DEFAULT_WAVEFORM_RANGE_LINK
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.DEFAULT_WAVEFORM_RANGE_MAX
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.DEFAULT_WAVEFORM_RANGE_MIN
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.DEFAULT_WAVEFORM_RENDER_MODE
import me.ksanstone.wavesync.wavesync.WaveSyncBootApplication
import me.ksanstone.wavesync.wavesync.gui.controller.visualizer.waveform.WaveformSettingsController
import me.ksanstone.wavesync.wavesync.gui.utility.AutoCanvas
import me.ksanstone.wavesync.wavesync.gui.utility.roundTo
import me.ksanstone.wavesync.wavesync.service.AudioCaptureService
import me.ksanstone.wavesync.wavesync.service.FourierMath
import me.ksanstone.wavesync.wavesync.service.FourierMath.frequencySamplesAtRate
import me.ksanstone.wavesync.wavesync.service.LocalizationService
import me.ksanstone.wavesync.wavesync.service.PreferenceService
import me.ksanstone.wavesync.wavesync.utility.RollingBuffer
import kotlin.math.roundToInt


class WaveformVisualizer(channel: Int) : AutoCanvas() {

    val enableAlign: BooleanProperty = SimpleBooleanProperty(false)
    val autoAlign: BooleanProperty = SimpleBooleanProperty(false)
    val rangeMax: FloatProperty = SimpleFloatProperty(DEFAULT_WAVEFORM_RANGE_MAX)
    val rangeMin: FloatProperty = SimpleFloatProperty(DEFAULT_WAVEFORM_RANGE_MIN)
    val rangeLink: BooleanProperty = SimpleBooleanProperty(DEFAULT_WAVEFORM_RANGE_LINK)
    val targetAlignFrequency: DoubleProperty = SimpleDoubleProperty(100.0)
    val renderMode: ObjectProperty<RenderMode> = SimpleObjectProperty(DEFAULT_WAVEFORM_RENDER_MODE)
    val bufferDuration: ObjectProperty<Duration> = SimpleObjectProperty(Duration.millis(60.0))

    private val channelProperty: IntegerProperty = SimpleIntegerProperty(channel)
    private lateinit var buffer: RollingBuffer<Float>
    private val waveColor: StyleableProperty<Color> =
        FACTORY.createStyleableColorProperty(this, "waveColor", "-fx-color") { vis -> vis.waveColor }
    private val align: BooleanProperty = SimpleBooleanProperty(false)
    private val alignLowPass: DoubleProperty = SimpleDoubleProperty(20.0)
    private var sampleRate: IntegerProperty = SimpleIntegerProperty(48000)
    private val downSampledSize: IntegerProperty = SimpleIntegerProperty(100)
    private val alignFrequency: DoubleProperty = SimpleDoubleProperty(100.0)
    private val acs = WaveSyncBootApplication.applicationContext.getBean(AudioCaptureService::class.java)

    init {
        resizeBuffer(bufferDuration.get(), sampleRate.get())
        detachedWindowNameProperty.set("Waveform")
        graphCanvas.xAxisShown.value = false
        graphCanvas.forceDrawVerticalAccentLines.value = true
        graphCanvas.verticalLinesVisible.value = true
        graphCanvas.highlightedHorizontalLines.addAll(1.0, -1.0)
        if (yAxis is NumberAxis)
            (yAxis as NumberAxis).tickUnit = 0.2
        if (xAxis is NumberAxis)
            (xAxis as NumberAxis).tickUnit = 100000000.0
        xAxis.minorTickCount = 0
        // .toString().toDouble() hack to get an exact conversion
        yAxis.lowerBoundProperty().bind(rangeMin.map { it.toString().toDouble() })
        yAxis.upperBoundProperty().bind(rangeMax.map { it.toString().toDouble() })

        acs.fftSize.addListener { _, _, v -> graphCanvas.highlightedVerticalLines.setAll(xAxis.upperBound - v.toDouble()) }

        autoAlign.addListener { _ -> bindAlign() }
        bindAlign()

        val ls = WaveSyncBootApplication.applicationContext.getBean(LocalizationService::class.java)
        alignLowPass.set(calcAlignLowPass())

        sampleRate.addListener { _, _, v ->
            resizeBuffer(bufferDuration.get(), v.toInt())
            alignLowPass.value = calcAlignLowPass()
        }
        bufferDuration.addListener { _, _, v ->
            resizeBuffer(v, sampleRate.get())
            alignLowPass.value = calcAlignLowPass()
        }
        align.bind(
            acs.peakValue[channelProperty.value].greaterThan(FourierMath.ALIGN_THRESHOLD).and(enableAlign)
                .and(acs.peakFrequency[channelProperty.value].greaterThan(alignLowPass))
                .and(acs.peakFrequency[channelProperty.value].lessThanOrEqualTo(20000))
        )

        val alignInfo = Label()
        alignFrequency.addListener { _ -> info(alignInfo) }
        align.addListener { _ -> info(alignInfo) }

        infoPane.add(Label(ls.get("visualizer.waveform.info.align")), 0, 3)
        infoPane.add(alignInfo, 1, 3)

        val downSampleInfoLabel = Label()
        downSampledSize.addListener { _ -> downSampleInfo(downSampleInfoLabel) }

        infoPane.add(Label(ls.get("visualizer.waveform.info.samples")), 0, 4)
        infoPane.add(downSampleInfoLabel, 1, 4)

        styleClass.add("waveform-visualizer")
        stylesheets.add("/styles/waveform-visualizer.css")
    }

    private fun bindAlign() {
        alignFrequency.unbind()
        if (autoAlign.get())
            alignFrequency.bind(acs.peakFrequency[channelProperty.value].map { it.toDouble().roundTo(0) })
        else
            alignFrequency.bind(targetAlignFrequency)
    }

    private fun calcAlignLowPass(): Double {
        return 1 / (buffer.size.toDouble() / sampleRate.get().toDouble()) * 1.2
    }

    override fun registerPreferences(id: String, preferenceService: PreferenceService) {
        preferenceService.registerProperty(renderMode, "renderMode", RenderMode::class.java, this.javaClass, id)
        preferenceService.registerProperty(targetAlignFrequency, "targetAlignFrequency", this.javaClass, id)
        preferenceService.registerProperty(enableAlign, "enableAlign", this.javaClass, id)
        preferenceService.registerProperty(autoAlign, "autoAlign", this.javaClass, id)
        preferenceService.registerProperty(rangeMax, "rangeMax", this.javaClass, id)
        preferenceService.registerProperty(rangeMin, "rangeMin", this.javaClass, id)
        preferenceService.registerProperty(rangeLink, "rangeLink", this.javaClass, id)
        preferenceService.registerProperty(graphCanvas.yAxisShown, "yAxisShown", this.javaClass, id)
        preferenceService.registerProperty(
            graphCanvas.horizontalLinesVisible,
            "horizontalLinesVisible",
            this.javaClass,
            id
        )
        preferenceService.registerProperty(
            graphCanvas.verticalLinesVisible,
            "verticalLinesVisible",
            this.javaClass,
            id
        )
        preferenceService.registerDurationProperty(bufferDuration, "bufferDuration", this.javaClass, id)
    }

    override fun initializeSettingMenu() {
        val loader = FXMLLoader()
        loader.location = javaClass.classLoader.getResource("layout/waveform")
        loader.resources =
            WaveSyncBootApplication.applicationContext.getBean(LocalizationService::class.java).getDefault()
        val controls: HBox =
            loader.load(javaClass.classLoader.getResourceAsStream("layout/waveform/waveformSettings.fxml"))
        val controller: WaveformSettingsController = loader.getController()
        controller.channelLabel.textProperty()
            .bind(acs.getChannelLabelProperty(channelProperty.value).map { it.shortcut })
        controller.waveformChartSettingsController.initialize(this)
        controlPane.children.add(controls)
    }

    private fun resizeBuffer(time: Duration, rate: Int) {
        val newSize = rate * time.toSeconds()
        this.buffer = RollingBuffer(newSize.toInt()) { 0.0f }
        xAxis.upperBound = newSize
        graphCanvas.highlightedVerticalLines.setAll(xAxis.upperBound - acs.fftSize.value.toDouble())
    }

    private fun info(label: Label) {
        Platform.runLater {
            label.text = "${alignFrequency.value}Hz ${align.value} Lo: ${alignLowPass.value}"
        }
    }

    private fun downSampleInfo(label: Label) {
        Platform.runLater {
            label.text = "${downSampledSize.get()} \u2022 ${buffer.size}"
        }
    }

    override fun draw(gc: GraphicsContext, deltaT: Double, now: Long, width: Double, height: Double) {
        gc.clearRect(0.0, 0.0, width, height)

        var drop = 0
        var take = buffer.size
        val min = rangeMin.get()
        val max = rangeMax.get()
        val rangeBreadth = max - min
        var takeAdjust = 0

        val minDisplaySamples = 10

        if (align.get()) {
            val maxWaves = (width / PIXELS_PER_WAVE).toInt().coerceIn(1, 50)
            val waveSize = frequencySamplesAtRate(alignFrequency.value, sampleRate.get())
            drop = (waveSize - buffer.written % waveSize).toInt().coerceIn(0, buffer.size - minDisplaySamples)
            take = (buffer.size - waveSize).coerceIn(1.0, waveSize * maxWaves).roundToInt()
                .coerceAtMost(buffer.size - drop)
        } else if (bufferDuration.get().greaterThan(Duration.millis(100.0)).and(renderMode.get() == RenderMode.LINE)) {
            // make the line graph less jumpy
            val waveSize = take.toDouble() / width.roundToInt()
            drop = (waveSize - buffer.written % waveSize).toInt().coerceIn(0, buffer.size - minDisplaySamples)
            takeAdjust = -drop
        }

        when (renderMode.get()!!) {
            RenderMode.LINE -> drawLine(gc, drop, take, min, rangeBreadth, width, height, takeAdjust)
            RenderMode.POINT_CLOUD -> drawPoints(gc, drop, take, min, rangeBreadth, width, height)
        }
    }

    private fun drawLine(
        gc: GraphicsContext,
        drop: Int,
        take: Int,
        min: Float,
        rangeBreadth: Float,
        width: Double,
        height: Double,
        takeAdjust: Int
    ) {
        var stepAccumulator = 0.0
        val step = take.toDouble() / width.roundToInt()

        gc.stroke = waveColor.value
        gc.beginPath()
        var acc = 0
        for (i in drop until drop + take + takeAdjust) {
            val ai = i - drop
            if (++stepAccumulator < step) continue
            stepAccumulator -= step
            gc.lineTo(ai.toDouble() / take * width, (buffer[i] - min).toDouble() / rangeBreadth * height)
            acc++
        }
        downSampledSize.set(acc)
        gc.stroke()
    }

    private fun drawPoints(
        gc: GraphicsContext,
        drop: Int,
        take: Int,
        min: Float,
        rangeBreadth: Float,
        width: Double,
        height: Double
    ) {
        var stepAccumulator = 0.0
        val step = take.toDouble() / (width.roundToInt() * 30)

        val color = waveColor.value
        for (i in drop until drop + take) {
            val ai = i - drop
            if (++stepAccumulator < step) continue
            stepAccumulator -= step
            gc.pixelWriter.setColor(
                (ai.toDouble() / take * width).roundToInt(),
                ((buffer[i] - min).toDouble() / rangeBreadth * height).roundToInt(),
                color
            )
        }
    }

    override fun registerListeners(acs: AudioCaptureService) {
        acs.registerSampleObserver(channelProperty.value, this::handleSamples)
    }

    fun handleSamples(event: AudioCaptureService.SampleEvent) {
        if (isPaused) return
        sampleRate.value = event.source.rate
        buffer.insert(event.data.toTypedArray())
    }

    override fun getCssMetaData(): List<CssMetaData<out Styleable?, *>> {
        return FACTORY.cssMetaData
    }

    companion object {
        private val FACTORY: StyleablePropertyFactory<WaveformVisualizer> =
            StyleablePropertyFactory<WaveformVisualizer>(
                Pane.getClassCssMetaData()
            )

        private const val PIXELS_PER_WAVE = 300

        @Suppress("unused")
        fun getClassCssMetaData(): List<CssMetaData<out Styleable?, *>> {
            return FACTORY.cssMetaData
        }
    }

    enum class RenderMode {
        LINE,
        POINT_CLOUD
    }
}
