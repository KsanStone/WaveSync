package me.ksanstone.wavesync.wavesync.gui.component.visualizer

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
import javafx.scene.paint.Paint
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.BAR_CUTOFF
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.BAR_LOW_PASS
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.BAR_SCALING
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.BAR_SMOOTHING
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.DB_MAX
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.DB_MIN
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.DEFAULT_SCALAR_TYPE
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.GAP
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.PEAK_LINE_VISIBLE
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.TARGET_BAR_WIDTH
import me.ksanstone.wavesync.wavesync.WaveSyncBootApplication
import me.ksanstone.wavesync.wavesync.gui.controller.visualizer.bar.BarSettingsController
import me.ksanstone.wavesync.wavesync.gui.utility.AutoCanvas
import me.ksanstone.wavesync.wavesync.service.FourierMath
import me.ksanstone.wavesync.wavesync.service.LocalizationService
import me.ksanstone.wavesync.wavesync.service.PreferenceService
import me.ksanstone.wavesync.wavesync.service.SupportedCaptureSource
import me.ksanstone.wavesync.wavesync.service.fftScaling.*
import me.ksanstone.wavesync.wavesync.service.smoothing.MagnitudeSmoother
import me.ksanstone.wavesync.wavesync.service.smoothing.MultiplicativeSmoother
import me.ksanstone.wavesync.wavesync.utility.MaxTracker
import kotlin.math.floor
import kotlin.math.max


class BarVisualizer : AutoCanvas() {

    private var frequencyAxis: NumberAxis = xAxis
    private var smoother: MagnitudeSmoother
    private var maxTracker: MaxTracker
    private var localizationService: LocalizationService =
        WaveSyncBootApplication.applicationContext.getBean(LocalizationService::class.java)
    private val tooltip = Label("---")
    private val startColor: ObjectProperty<Color> = SimpleObjectProperty(Color.rgb(255, 120, 246))
    private val endColor: ObjectProperty<Color> = SimpleObjectProperty(Color.AQUA)
    val smoothing: FloatProperty = SimpleFloatProperty(BAR_SMOOTHING)
    val cutoff: IntegerProperty = SimpleIntegerProperty(BAR_CUTOFF)
    val lowPass: IntegerProperty = SimpleIntegerProperty(BAR_LOW_PASS)
    val targetBarWidth: IntegerProperty = SimpleIntegerProperty(TARGET_BAR_WIDTH)
    val gap: IntegerProperty = SimpleIntegerProperty(GAP)
    val peakLineVisible: BooleanProperty = SimpleBooleanProperty(PEAK_LINE_VISIBLE)

    val scalarType: ObjectProperty<FFTScalarType> = SimpleObjectProperty(DEFAULT_SCALAR_TYPE)
    val linearScaling: FloatProperty = SimpleFloatProperty(BAR_SCALING)
    val dbMin: FloatProperty = SimpleFloatProperty(DB_MIN)
    val dbMax: FloatProperty = SimpleFloatProperty(DB_MAX)

    private lateinit var fftScalar: FFTScalar<*>

    private val peakLineColor: StyleableProperty<Paint> =
        FACTORY.createStyleablePaintProperty(this, "peakLineColor", "-fx-peak-line-color") { vis -> vis.peakLineColor }

    init {
        frequencyAxis.tickUnit = 1000.0
        canvasContainer.highlightedVerticalLines.add(20000.0)
        detachedWindowNameProperty.set("Bar")
        canvasContainer.tooltipEnabled.set(true)
        canvasContainer.tooltipContainer.children.add(tooltip)

        smoother = MultiplicativeSmoother()
        smoother.dataSize = 512
        maxTracker = MaxTracker()
        maxTracker.dataSize = smoother.dataSize

        smoothing.addListener { _ ->
            smoother.factor = smoothing.get().toDouble()
        }
        cutoff.addListener { _ -> sizeFrequencyAxis() }
        lowPass.addListener { _ -> sizeFrequencyAxis() }

        changeScalar()
        scalarType.addListener { _ -> changeScalar(); maxTracker.zero() }
        linearScaling.addListener { _ -> refreshScalar() }
        dbMax.addListener { _ -> refreshScalar() }
        dbMin.addListener { _ -> refreshScalar() }
        peakLineVisible.addListener { _, _, v -> if(v) maxTracker.zero() }

        canvasContainer.tooltipPosition.addListener { _, _, v ->
            if (source == null) {
                tooltip.text = "---"
            } else {
                val x = v.x
                val bufferLength = smoother.dataSize
                val step = calculateStep(targetBarWidth.get(), bufferLength, width)
                val totalBars = floor(bufferLength.toDouble() / step)
                val barWidth = (width - (totalBars - 1) * gap.get()) / totalBars
                val bar = floor(x / barWidth)
                val binStart = floor(bar * step).toInt()
                val binEnd = floor((bar + 1) * step).toInt()
                val minFreq = FourierMath.frequencyOfBin(binStart, source!!.format.mix.rate, fftSize)
                val maxFreq = FourierMath.frequencyOfBin(binEnd, source!!.format.mix.rate, fftSize)
                tooltip.text =
                    "Bar: ${localizationService.formatNumber(bar)} \nFFT: ${localizationService.formatNumber(binStart)} - ${
                        localizationService.formatNumber(binEnd)
                    }\nFreq: ${localizationService.formatNumber(minFreq, "Hz")} - ${
                        localizationService.formatNumber(
                            maxFreq,
                            "Hz"
                        )
                    }"
            }
        }

        this.styleClass.setAll("bar-visualizer")
        this.stylesheets.add("/styles/bar-visualizer.css")

        controlPane.toFront()
    }

    fun initializeSettingMenu() {
        val loader = FXMLLoader()
        loader.location = javaClass.classLoader.getResource("layout/bar")
        loader.resources =
            WaveSyncBootApplication.applicationContext.getBean(LocalizationService::class.java).getDefault()
        val controls: HBox = loader.load(javaClass.classLoader.getResourceAsStream("layout/bar/barSettings.fxml"))
        val controller: BarSettingsController = loader.getController()
        controller.chartSettingsController.initialize(this)

        controlPane.children.add(controls)
    }


    fun registerPreferences(id: String, preferenceService: PreferenceService) {
        preferenceService.registerProperty(smoothing, "smoothing", this.javaClass, id)
        preferenceService.registerProperty(linearScaling, "scaling", this.javaClass, id)
        preferenceService.registerProperty(cutoff, "cutoff", this.javaClass, id)
        preferenceService.registerProperty(lowPass, "lowPass", this.javaClass, id)
        preferenceService.registerProperty(targetBarWidth, "targetBarWidth", this.javaClass, id)
        preferenceService.registerProperty(gap, "gap", this.javaClass, id)
        preferenceService.registerProperty(scalarType, "fftScalar", FFTScalarType::class.java, this.javaClass, id)
        preferenceService.registerProperty(dbMin, "dbMin", this.javaClass, id)
        preferenceService.registerProperty(dbMax, "dbMax", this.javaClass, id)
        preferenceService.registerProperty(peakLineVisible, "peakLineVisible", this.javaClass, id)
        preferenceService.registerProperty(canvasContainer.xAxisShown, "xAxisShown", this.javaClass, id)
        preferenceService.registerProperty(canvasContainer.yAxisShown, "yAxisShown", this.javaClass, id)
        preferenceService.registerProperty(canvasContainer.horizontalLinesVisible, "horizontalLinesVisible", this.javaClass, id)
        preferenceService.registerProperty(canvasContainer.verticalLinesVisible, "verticalLinesVisible", this.javaClass, id)
    }

    private fun changeScalar() {
        this.fftScalar = when (scalarType.get()) {
            FFTScalarType.LINEAR -> LinearFFTScalar()
            FFTScalarType.DECIBEL -> DeciBelFFTScalar()
            FFTScalarType.EXAGGERATED -> ExaggeratedFFTScalar()
            else -> throw IllegalArgumentException("Invalid fft scalar type ${scalarType.get()}")
        }
        refreshScalar()
    }

    private fun refreshScalar() {
        when (fftScalar) {
            is LinearFFTScalar -> {
                (fftScalar as LinearFFTScalar).update(LinearFFTScalarParams())
            }

            is ExaggeratedFFTScalar -> {
                (fftScalar as ExaggeratedFFTScalar).update(ExaggeratedFFTScalarParams(scaling = linearScaling.get()))
            }

            is DeciBelFFTScalar -> {
                (fftScalar as DeciBelFFTScalar).update(
                    DeciBelFFTScalarParameters(
                        rangeMin = dbMin.get(),
                        rangeMax = dbMax.get()
                    )
                )
            }
        }
        sizeValueAxis()
    }

    private fun sizeValueAxis() {
        val s = fftScalar.getAxisScale()
        yAxis.lowerBound = s.min
        yAxis.upperBound = s.max
        yAxis.tickUnit = s.step
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
            maxTracker.dataSize = size
        }

        for (i in frequencyBinSkip until size) {
            smoother.dataTarget[i - frequencyBinSkip] = fftScalar.scale(array[i])
        }
        if (peakLineVisible.get())
            maxTracker.data = smoother.data
    }

    private fun calculateStep(targetWidth: Int, bufferLength: Int, width: Double): Double {
        val estimatedWidth = width / bufferLength
        return (targetWidth.toDouble() / estimatedWidth).coerceAtLeast(1.0)
    }

    override fun draw(gc: GraphicsContext, deltaT: Double, now: Long, width: Double, height: Double) {
        smoother.applySmoothing(deltaT)

        gc.clearRect(0.0, 0.0, width, height)

        val localGap = gap.get()
        val bufferLength = smoother.dataSize
        var step = calculateStep(targetBarWidth.get(), bufferLength, width)
        val totalBars = floor(bufferLength.toDouble() / step)
        var barWidth = (width - (totalBars - 1) * localGap) / totalBars
        val buffer = smoother.data
        val padding = (barWidth * 0.3).coerceAtMost(1.0)

        var x = 0.0
        var y = 0.0
        var stepAccumulator = 0.0

        for (i in 0 until bufferLength) {
            y = max(buffer[i].toDouble(), y)
            if (++stepAccumulator < step) continue
            stepAccumulator -= step

            val barHeight = y * height
            val color = startColor.get().interpolate(endColor.get(), y)

            gc.fill = color
            gc.fillRect(x, height - barHeight, barWidth + padding, barHeight)
            x += barWidth + localGap
            y = 0.0
        }

        if (!peakLineVisible.get()) return

        gc.stroke = peakLineColor.value

        x = 0.0
        y = 0.0
        stepAccumulator = 0.0
        step = calculateStep(1, bufferLength, width)
        gc.beginPath()
        gc.moveTo(0.0, height - maxTracker.data[0].toDouble() * height)
        barWidth = width / floor(bufferLength.toDouble() / step)

        for (i in 0 until bufferLength) {
            y = max(maxTracker.data[i].toDouble(), y)
            if (++stepAccumulator < step) continue
            stepAccumulator -= step

            gc.lineTo(x, height - y * height)
            x += barWidth
            y = 0.0
        }

        gc.stroke()
    }

    override fun getCssMetaData(): List<CssMetaData<out Styleable?, *>> {
        return FACTORY.cssMetaData
    }

    companion object {
        private val FACTORY: StyleablePropertyFactory<BarVisualizer> =
            StyleablePropertyFactory<BarVisualizer>(
                Pane.getClassCssMetaData()
            )

        @Suppress("unused")
        fun getClassCssMetaData(): List<CssMetaData<out Styleable?, *>> {
            return FACTORY.cssMetaData
        }
    }
}