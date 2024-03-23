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
import javafx.scene.paint.*
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.BAR_CUTOFF
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.BAR_LOW_PASS
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.BAR_SCALING
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.BAR_SMOOTHING
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.DB_MAX
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.DB_MIN
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.DEFAULT_BAR_RENDER_MODE
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.DEFAULT_FILL_UNDER_CURVE
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.DEFAULT_LOGARITHMIC_MODE
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.DEFAULT_SCALAR_TYPE
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.GAP
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.LINEAR_BAR_SCALING
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.PEAK_LINE_VISIBLE
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.TARGET_BAR_WIDTH
import me.ksanstone.wavesync.wavesync.WaveSyncBootApplication
import me.ksanstone.wavesync.wavesync.gui.component.util.LogarithmicAxis
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

    private var smoother: MagnitudeSmoother
    private var rawMaxTracker: MaxTracker
    private var localizationService: LocalizationService =
        WaveSyncBootApplication.applicationContext.getBean(LocalizationService::class.java)
    private val tooltip = Label()
    private val startColor: ObjectProperty<Color> = SimpleObjectProperty(Color.rgb(255, 120, 246))
    private val endColor: ObjectProperty<Color> = SimpleObjectProperty(Color.AQUA)
    val smoothing: FloatProperty = SimpleFloatProperty(BAR_SMOOTHING)
    val cutoff: IntegerProperty = SimpleIntegerProperty(BAR_CUTOFF)
    val lowPass: IntegerProperty = SimpleIntegerProperty(BAR_LOW_PASS)
    val targetBarWidth: IntegerProperty = SimpleIntegerProperty(TARGET_BAR_WIDTH)
    val gap: IntegerProperty = SimpleIntegerProperty(GAP)
    val peakLineVisible: BooleanProperty = SimpleBooleanProperty(PEAK_LINE_VISIBLE)

    val scalarType: ObjectProperty<FFTScalarType> = SimpleObjectProperty(DEFAULT_SCALAR_TYPE)
    val exaggeratedScalar: FloatProperty = SimpleFloatProperty(BAR_SCALING)
    val linearScalar: FloatProperty = SimpleFloatProperty(LINEAR_BAR_SCALING)
    val dbMin: FloatProperty = SimpleFloatProperty(DB_MIN)
    val dbMax: FloatProperty = SimpleFloatProperty(DB_MAX)
    val logarithmic: BooleanProperty = SimpleBooleanProperty(DEFAULT_LOGARITHMIC_MODE)
    val renderMode: ObjectProperty<RenderMode> = SimpleObjectProperty(DEFAULT_BAR_RENDER_MODE)
    val fillCurve: BooleanProperty = SimpleBooleanProperty(DEFAULT_FILL_UNDER_CURVE)

    private var source: SupportedCaptureSource? = null
    private var rate: Int = 44100
    private var fftSize: Int = 1024
    private var frequencyBinSkip: Int = 0
    private lateinit var fftDataArray: FloatArray
    private val bufferResizeLock = Object()


    private lateinit var fftScalar: FFTScalar<*>

    private val peakLineColor: StyleableProperty<Paint> =
        FACTORY.createStyleablePaintProperty(this, "peakLineColor", "-fx-peak-line-color") { vis -> vis.peakLineColor }
    private val peakLineUnderColor: StyleableProperty<Paint> =
        FACTORY.createStyleablePaintProperty(this, "peakLineUnderColor", "peakLineUnderColor") { vis -> vis.peakLineColor }

    init {
        if (xAxis is NumberAxis)
            (xAxis as NumberAxis).tickUnit = 1000.0
        canvasContainer.highlightedVerticalLines.add(20000.0)
        detachedWindowNameProperty.set("Bar")
        canvasContainer.tooltipEnabled.set(true)
        canvasContainer.tooltipContainer.children.add(tooltip)

        smoother = MultiplicativeSmoother()
        smoother.dataSize = 512
        rawMaxTracker = MaxTracker()
        rawMaxTracker.dataSize = smoother.dataSize

        smoothing.addListener { _ ->
            smoother.factor = smoothing.get().toDouble()
        }
        cutoff.addListener { _ -> sizeFrequencyAxis() }
        lowPass.addListener { _ -> sizeFrequencyAxis() }

        changeScalar()
        scalarType.addListener { _ -> changeScalar(); rawMaxTracker.zero() }
        listOf(exaggeratedScalar, dbMax, dbMin, linearScalar)
            .forEach { it.addListener { _ -> refreshScalar() } }
        dbMin.addListener { _ -> refreshScalar() }
        peakLineVisible.addListener { _, _, v -> if(v) rawMaxTracker.zero() }

        canvasContainer.tooltipPosition.addListener { _ -> refreshTooltipLabel() }

        this.styleClass.setAll("bar-visualizer")
        this.stylesheets.add("/styles/bar-visualizer.css")

        controlPane.toFront()

        logarithmic.addListener { _, _, v ->
            if (v) {
                updateXAxis(LogarithmicAxis(0.0, 20000.0))
            } else {
                updateXAxis(NumberAxis(0.0, 20000.0, 1000.0))
            }
            sizeFrequencyAxis()
        }
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
        preferenceService.registerProperty(renderMode, "renderMode", RenderMode::class.java, this.javaClass, id)
        preferenceService.registerProperty(smoothing, "smoothing", this.javaClass, id)
        preferenceService.registerProperty(exaggeratedScalar, "exaggeratedScaling", this.javaClass, id)
        preferenceService.registerProperty(linearScalar, "linearScaling", this.javaClass, id)
        preferenceService.registerProperty(cutoff, "cutoff", this.javaClass, id)
        preferenceService.registerProperty(lowPass, "lowPass", this.javaClass, id)
        preferenceService.registerProperty(targetBarWidth, "targetBarWidth", this.javaClass, id)
        preferenceService.registerProperty(gap, "gap", this.javaClass, id)
        preferenceService.registerProperty(scalarType, "fftScalar", FFTScalarType::class.java, this.javaClass, id)
        preferenceService.registerProperty(dbMin, "dbMin", this.javaClass, id)
        preferenceService.registerProperty(dbMax, "dbMax", this.javaClass, id)
        preferenceService.registerProperty(peakLineVisible, "peakLineVisible", this.javaClass, id)
        preferenceService.registerProperty(fillCurve, "fillCurve", this.javaClass, id)
        preferenceService.registerProperty(logarithmic, "logarithmic", this.javaClass, id)
        preferenceService.registerProperty(canvasContainer.xAxisShown, "xAxisShown", this.javaClass, id)
        preferenceService.registerProperty(canvasContainer.yAxisShown, "yAxisShown", this.javaClass, id)
        preferenceService.registerProperty(canvasContainer.horizontalLinesVisible, "horizontalLinesVisible", this.javaClass, id)
        preferenceService.registerProperty(canvasContainer.verticalLinesVisible, "verticalLinesVisible", this.javaClass, id)
    }

    private fun refreshTooltipLabel() {
        try {
            if (source == null) {
                tooltip.text = "---"
                return
            }

            val x = canvasContainer.tooltipPosition.get().x
            val bufferLength = smoother.dataSize
            val step = calculateStep(targetBarWidth.get(), bufferLength, canvas.width)
            val totalBars = floor(bufferLength.toDouble() / step)
            val barWidth = (canvas.width - (totalBars - 1) * gap.get()) / totalBars
            val bar = floor(x / barWidth)
            val binStart = floor(bar * step).toInt()
            val binEnd = floor((bar + 1) * step).toInt()
            val minFreq = FourierMath.frequencyOfBin(binStart, source!!.rate, fftSize)
            val maxFreq = FourierMath.frequencyOfBin(binEnd, source!!.rate, fftSize)
            val maxValue = rawMaxTracker.data.slice(binStart..binEnd).max()
            val rawValue = fftDataArray.slice(binStart + frequencyBinSkip..binEnd + frequencyBinSkip).max()
            tooltip.text =
                "Bar: ${localizationService.formatNumber(bar)} \n" +
                        "FFT: ${localizationService.formatNumber(binStart)} - ${
                            localizationService.formatNumber(
                                binEnd
                            )
                        }\n" +
                        "Freq: ${
                            localizationService.formatNumber(
                                minFreq,
                                "Hz"
                            )
                        } - ${localizationService.formatNumber(maxFreq, "Hz")}\n" +
                        "Scaled: ${localizationService.formatNumber(fftScalar.scaleRaw(rawValue))}"
            if (peakLineVisible.get()) tooltip.text += "\nMax: ${
                localizationService.formatNumber(
                    fftScalar.scaleRaw(
                        maxValue
                    )
                )
            }"
        } catch (_: IndexOutOfBoundsException) { }
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
                (fftScalar as LinearFFTScalar).update(LinearFFTScalarParams(scaling = linearScalar.get()))
            }

            is ExaggeratedFFTScalar -> {
                (fftScalar as ExaggeratedFFTScalar).update(ExaggeratedFFTScalarParams(scaling = exaggeratedScalar.get()))
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
        if (yAxis is NumberAxis)
            (yAxis as NumberAxis).tickUnit = s.step
    }

    private fun sizeFrequencyAxis() {
        if (source == null) return
        val upper = source!!.trimResultTo(fftSize, cutoff.get())
        val lower = source!!.bufferBeginningSkipFor(lowPass.get(), fftSize)
        xAxis.lowerBound = FourierMath.frequencyOfBin(lower, source!!.rate, fftSize).toDouble()
        xAxis.upperBound = FourierMath.frequencyOfBin(upper, source!!.rate, fftSize).toDouble()
    }

    fun handleFFT(array: FloatArray, source: SupportedCaptureSource) {
        if(isPaused) return

        fftDataArray = array
        if (source != this.source) {
            this.source = source
            sizeFrequencyAxis()
        }
        this.fftSize = array.size * 2
        this.rate = source.rate
        var size = source.trimResultTo(array.size * 2, cutoff.get())
        frequencyBinSkip = source.bufferBeginningSkipFor(lowPass.get(), array.size * 2)
        size = (size - frequencyBinSkip).coerceAtLeast(10)
        if (smoother.dataSize != size) {
            synchronized(bufferResizeLock) {
                smoother.dataSize = size
                rawMaxTracker.dataSize = size
            }
        }

        for (i in frequencyBinSkip until size) {
            smoother.dataTarget[i - frequencyBinSkip] = fftScalar.scale(array[i])
        }
        if (peakLineVisible.get()) {
            rawMaxTracker.applyData(array, frequencyBinSkip, size)
        }
    }

    private fun calculateStep(targetWidth: Int, bufferLength: Int, width: Double): Double {
        val estimatedWidth = width / bufferLength
        return (targetWidth.toDouble() / estimatedWidth).coerceAtLeast(1.0)
    }

    override fun draw(gc: GraphicsContext, deltaT: Double, now: Long, width: Double, height: Double) {
        synchronized(bufferResizeLock) {
            smoother.applySmoothing(deltaT)

            gc.clearRect(0.0, 0.0, width, height)

            val localGap = if (renderMode.get()!! == RenderMode.BAR && !logarithmic.get()) gap.get() else 0
            val bufferLength = smoother.dataSize
            var step = calculateStep(targetBarWidth.get(), bufferLength, width)
            val totalBars = floor(bufferLength.toDouble() / step)
            var barWidth = (width - (totalBars - 1) * localGap) / totalBars
            val buffer = smoother.data
            val padding = (barWidth * 0.33).coerceAtMost(1.0)
            gc.stroke = startColor.get()

            when (renderMode.get()!!) {
                RenderMode.LINE -> drawLine(buffer, bufferLength, step, gc, barWidth, height, width, fillCurve.get())
                RenderMode.BAR -> drawBars(buffer, bufferLength, step, gc, barWidth, localGap, padding, height, width)
            }

            if (canvasContainer.tooltipContainer.isVisible) refreshTooltipLabel()

            if (!peakLineVisible.get()) return
            gc.stroke = peakLineColor.value
            step = calculateStep(1, bufferLength, width)
            barWidth = width / floor(bufferLength.toDouble() / step)
            drawLine(rawMaxTracker.data.map { fftScalar.scale(it) }.toFloatArray(), bufferLength, step, gc, barWidth, height, width, false)
        }
    }

    private fun drawLine(buffer: FloatArray, bufferLength: Int, step: Double, gc: GraphicsContext, barWidth: Double, height: Double, width: Double, fill: Boolean) {
        if (logarithmic.get()) {
            drawLineLogarithmic(buffer, bufferLength, gc, barWidth, height, width, fill)
        } else {
            drawLineLinear(buffer, bufferLength, step, gc, barWidth, height, width, fill)
        }
    }

    private fun drawLineLogarithmic(
        buffer: FloatArray,
        bufferLength: Int,
        gc: GraphicsContext,
        barWidth: Double,
        height: Double,
        width: Double,
        fill: Boolean
    ) {
        var y = 0.0
        var x: Double
        var lastX = 0.0

        gc.beginPath()
        gc.moveTo(0.0, height - buffer[0].toDouble() * height)

        for (i in 0 until bufferLength) {
            y = max(buffer[i].toDouble(), y)
            x = xAxis.getDisplayPosition(FourierMath.frequencyOfBin(i, rate, fftSize))
            if (x - lastX < barWidth && i != 0) continue

            gc.lineTo(x, height - y * height)
            lastX = x
            y = 0.0
        }
        gc.stroke()
        fillUnderLine(fill, gc, width, height)
    }

    private fun fillUnderLine(
        fill: Boolean,
        gc: GraphicsContext,
        width: Double,
        height: Double
    ) {
        if (fill) {
            gc.lineTo(width, height + 5.0)
            gc.lineTo(0.0, height + 5.0)
            gc.closePath()
            if (gc.stroke is Color) {
                val c1 = Color((gc.stroke as Color).red, (gc.stroke as Color).green, (gc.stroke as Color).blue, 0.25)
                val c2 = Color((gc.stroke as Color).red, (gc.stroke as Color).green, (gc.stroke as Color).blue, 0.05)
                gc.fill = LinearGradient(0.0, 0.0, 0.0, height, false, CycleMethod.NO_CYCLE, Stop(0.0, c1), Stop(1.0, c2))
            } else {
                gc.fill = peakLineUnderColor.value
            }
            gc.fill()
        }
    }

    private fun drawLineLinear(buffer: FloatArray, bufferLength: Int, step: Double, gc: GraphicsContext, barWidth: Double, height: Double, width: Double, fill: Boolean) {
        var x = 0.0
        var y = 0.0
        var stepAccumulator = 0.0
        gc.beginPath()
        gc.moveTo(0.0, height - buffer[0].toDouble() * height)

        for (i in 0 until bufferLength) {
            y = max(buffer[i].toDouble(), y)
            if (++stepAccumulator < step) continue
            stepAccumulator -= step

            gc.lineTo(x, height - y * height)
            x += barWidth
            y = 0.0
        }

        gc.stroke()
        fillUnderLine(fill, gc, width, height)
    }

    private fun drawBars(buffer: FloatArray, bufferLength: Int, step: Double, gc: GraphicsContext, barWidth: Double, localGap: Int, padding: Double, height: Double, width: Double) {
        if (logarithmic.get()) {
            drawBarsLogarithmic(buffer, bufferLength, gc, barWidth, padding, height, width)
        } else {
            drawBarsLinear(buffer, bufferLength, step, gc, barWidth, localGap, padding, height)
        }
    }

    private fun drawBarsLogarithmic(
        buffer: FloatArray,
        bufferLength: Int,
        gc: GraphicsContext,
        barWidth: Double,
        padding: Double,
        height: Double,
        width: Double
    ) {
        var y = 0.0
        var lastX = 0.0
        var lastEnd = 0.0

        val xArray = DoubleArray(bufferLength) { xAxis.getDisplayPosition(FourierMath.frequencyOfBin(it, rate, fftSize)) }

        for (i in 0 until bufferLength) {
            y = max(buffer[i].toDouble(), y)
            val tmp = xArray.getOrElse(i+1) { width }
            if (tmp - lastX < barWidth && i != 0) continue

            val barHeight = y * height
            val color = startColor.get().interpolate(endColor.get(), y)

            gc.fill = color
            gc.fillRect(lastEnd, height - barHeight, tmp - lastEnd + padding, barHeight)
            lastEnd = tmp
            lastX = tmp
            y = 0.0
        }
    }

    private fun drawBarsLinear(buffer: FloatArray, bufferLength: Int, step: Double, gc: GraphicsContext, barWidth: Double, localGap: Int, padding: Double, height: Double) {
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

    enum class RenderMode {
        LINE,
        BAR
    }
}