package me.ksanstone.wavesync.wavesync.gui.component.visualizer

import javafx.beans.property.*
import javafx.beans.value.ObservableValue
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
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.DEFAULT_BAR_CUTOFF
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.DEFAULT_BAR_LOW_PASS
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.DEFAULT_BAR_RENDER_MODE
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.DEFAULT_BAR_SCALING
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.DEFAULT_BAR_SMOOTHER_TYPE
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.DEFAULT_BAR_SMOOTHING
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.DEFAULT_DB_MAX
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.DEFAULT_DB_MIN
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.DEFAULT_FILL_UNDER_CURVE
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.DEFAULT_GAP
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.DEFAULT_LINEAR_BAR_SCALING
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.DEFAULT_LOGARITHMIC_MODE
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.DEFAULT_PEAK_LINE_VISIBLE
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.DEFAULT_SCALAR_TYPE
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.DEFAULT_SHOW_PEAK
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.DEFAULT_SMOOTH_CURVE
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.DEFAULT_TARGET_BAR_WIDTH
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.DEFAULT_USE_CSS_COLOR
import me.ksanstone.wavesync.wavesync.WaveSyncBootApplication
import me.ksanstone.wavesync.wavesync.gui.component.util.LogarithmicAxis
import me.ksanstone.wavesync.wavesync.gui.controller.visualizer.bar.BarSettingsController
import me.ksanstone.wavesync.wavesync.gui.utility.AutoCanvas
import me.ksanstone.wavesync.wavesync.service.*
import me.ksanstone.wavesync.wavesync.service.fftScaling.*
import me.ksanstone.wavesync.wavesync.service.smoothing.ExponentialFalloffSmoother
import me.ksanstone.wavesync.wavesync.service.smoothing.MagnitudeSmoother
import me.ksanstone.wavesync.wavesync.service.smoothing.MultiplicativeSmoother
import me.ksanstone.wavesync.wavesync.utility.MaxTracker
import kotlin.math.*


class BarVisualizer : AutoCanvas() {

    private var smoother: MagnitudeSmoother
    private var rawMaxTracker: MaxTracker
    private var localizationService: LocalizationService =
        WaveSyncBootApplication.applicationContext.getBean(LocalizationService::class.java)
    private val tooltip = Label()
    private val setStartColor: ObjectProperty<Color> = SimpleObjectProperty(Color.rgb(255, 120, 246))
    private val setEndColor: ObjectProperty<Color> = SimpleObjectProperty(Color.AQUA)
    val smoothing: FloatProperty = SimpleFloatProperty(DEFAULT_BAR_SMOOTHING)
    val highPass: IntegerProperty = SimpleIntegerProperty(DEFAULT_BAR_CUTOFF)
    val lowPass: IntegerProperty = SimpleIntegerProperty(DEFAULT_BAR_LOW_PASS)
    val targetBarWidth: IntegerProperty = SimpleIntegerProperty(DEFAULT_TARGET_BAR_WIDTH)
    val gap: IntegerProperty = SimpleIntegerProperty(DEFAULT_GAP)
    val peakLineVisible: BooleanProperty = SimpleBooleanProperty(DEFAULT_PEAK_LINE_VISIBLE)

    val scalarType: ObjectProperty<FFTScalarType> = SimpleObjectProperty(DEFAULT_SCALAR_TYPE)
    val exaggeratedScalar: FloatProperty = SimpleFloatProperty(DEFAULT_BAR_SCALING)
    val linearScalar: FloatProperty = SimpleFloatProperty(DEFAULT_LINEAR_BAR_SCALING)
    val dbMin: FloatProperty = SimpleFloatProperty(DEFAULT_DB_MIN)
    val dbMax: FloatProperty = SimpleFloatProperty(DEFAULT_DB_MAX)
    val logarithmic: BooleanProperty = SimpleBooleanProperty(DEFAULT_LOGARITHMIC_MODE)
    val renderMode: ObjectProperty<RenderMode> = SimpleObjectProperty(DEFAULT_BAR_RENDER_MODE)
    val smootherType: ObjectProperty<SmootherType> = SimpleObjectProperty(DEFAULT_BAR_SMOOTHER_TYPE)
    val fillCurve: BooleanProperty = SimpleBooleanProperty(DEFAULT_FILL_UNDER_CURVE)
    val smoothCurve: BooleanProperty = SimpleBooleanProperty(DEFAULT_SMOOTH_CURVE)
    val showPeak: BooleanProperty = SimpleBooleanProperty(DEFAULT_SHOW_PEAK)

    private val useCssColor: BooleanProperty = SimpleBooleanProperty(DEFAULT_USE_CSS_COLOR)
    private var source: SupportedCaptureSource? = null
    private var rate: Int = 44100
    private var fftSize: Int = 1024
    private var frequencyBinSkip: Int = 0
    private lateinit var fftDataArray: FloatArray
    private var fftLocBuffer = FftLocBuffer(0, Array(0) { _ -> FftLoc(0.0, 0.0, 0.0) })
    private var lineAngleArray = DoubleArray(fftLocBuffer.size + 1)
    private val bufferResizeLock = Object()

    private lateinit var fftScalar: FFTScalar<*>

    private val peakLineColor: StyleableProperty<Paint> =
        FACTORY.createStyleablePaintProperty(this, "peakLineColor", "-fx-peak-line-color") { vis -> vis.peakLineColor }
    private val peakLineUnderColor: StyleableProperty<Paint> =
        FACTORY.createStyleablePaintProperty(this, "peakLineUnderColor", "peakLineUnderColor") { vis -> vis.peakLineColor }
    private val peakPointUnderColor: StyleableProperty<Color> =
        FACTORY.createStyleableColorProperty(this, "peakPointUnderColor", "-fx-peak-point-color") { vis -> vis.peakPointUnderColor }
    private val startCssColor: StyleableProperty<Color> =
        FACTORY.createStyleableColorProperty(this, "startCssColor", "-fx-start-color") { vis -> vis.startCssColor }
    private val endCssColor: StyleableProperty<Color> =
        FACTORY.createStyleableColorProperty(this, "endCssColor", "-fx-end-color") { vis -> vis.endCssColor }

    private val effectiveStartColor: ObjectProperty<Color> = SimpleObjectProperty()
    private val effectiveEndColor: ObjectProperty<Color> = SimpleObjectProperty()

    private val audioCaptureService: AudioCaptureService =
        WaveSyncBootApplication.applicationContext.getBean(AudioCaptureService::class.java)

    init {
        if (xAxis is NumberAxis)
            (xAxis as NumberAxis).tickUnit = 1000.0
        changeSmoother()
        this.smootherType.addListener { _ ->  changeSmoother() }
        canvasContainer.dependOnXAxis.set(true)
        canvasContainer.highlightedVerticalLines.add(20000.0)
        detachedWindowNameProperty.set("Bar")
        canvasContainer.tooltipEnabled.set(true)
        canvasContainer.tooltipContainer.children.add(tooltip)

        useCssColor.addListener { _ -> colorSwitch() }
        colorSwitch()

        val globalColorService = WaveSyncBootApplication.applicationContext.getBean(GlobalColorService::class.java)
        this.setStartColor.bind(globalColorService.startColor)
        this.setEndColor.bind(globalColorService.endColor)
        this.useCssColor.bind(globalColorService.barUseCssColor)

        smoother = ExponentialFalloffSmoother()
        smoother.dataSize = 512
        rawMaxTracker = MaxTracker()
        rawMaxTracker.dataSize = smoother.dataSize

        smoothing.addListener { _ ->
            smoother.factor = smoothing.get().toDouble()
        }
        highPass.addListener { _ -> sizeFrequencyAxis() }
        lowPass.addListener { _ -> sizeFrequencyAxis() }

        changeScalar()
        scalarType.addListener { _ -> changeScalar(); rawMaxTracker.zero() }
        listOf(exaggeratedScalar, dbMax, dbMin, linearScalar)
            .forEach { it.addListener { _ -> refreshScalar() } }
        dbMin.addListener { _ -> refreshScalar() }
        peakLineVisible.addListener { _, _, v -> if (v) rawMaxTracker.zero() }

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

    @Suppress("UNCHECKED_CAST")
    private fun colorSwitch() {
        effectiveStartColor.unbind()
        effectiveEndColor.unbind()
        if (useCssColor.get()) {
            effectiveStartColor.bind(startCssColor as ObservableValue<Color>)
            effectiveEndColor.bind(endCssColor as ObservableValue<Color>)
        } else {
            effectiveStartColor.bind(setStartColor)
            effectiveEndColor.bind(setEndColor)
        }
    }

    override fun initializeSettingMenu() {
        val loader = FXMLLoader()
        loader.location = javaClass.classLoader.getResource("layout/bar")
        loader.resources =
            WaveSyncBootApplication.applicationContext.getBean(LocalizationService::class.java).getDefault()
        val controls: HBox = loader.load(javaClass.classLoader.getResourceAsStream("layout/bar/barSettings.fxml"))
        val controller: BarSettingsController = loader.getController()
        controller.chartSettingsController.initialize(this)

        controlPane.children.add(controls)
    }

    override fun registerPreferences(id: String, preferenceService: PreferenceService) {
        preferenceService.registerProperty(renderMode, "renderMode", RenderMode::class.java, this.javaClass, id)
        preferenceService.registerProperty(smootherType, "smootherType", SmootherType::class.java, this.javaClass, id)
        preferenceService.registerProperty(smoothing, "smoothing", this.javaClass, id)
        preferenceService.registerProperty(exaggeratedScalar, "exaggeratedScaling", this.javaClass, id)
        preferenceService.registerProperty(linearScalar, "linearScaling", this.javaClass, id)
        preferenceService.registerProperty(highPass, "cutoff", this.javaClass, id)
        preferenceService.registerProperty(lowPass, "lowPass", this.javaClass, id)
        preferenceService.registerProperty(targetBarWidth, "targetBarWidth", this.javaClass, id)
        preferenceService.registerProperty(gap, "gap", this.javaClass, id)
        preferenceService.registerProperty(scalarType, "fftScalar", FFTScalarType::class.java, this.javaClass, id)
        preferenceService.registerProperty(dbMin, "dbMin", this.javaClass, id)
        preferenceService.registerProperty(dbMax, "dbMax", this.javaClass, id)
        preferenceService.registerProperty(peakLineVisible, "peakLineVisible", this.javaClass, id)
        preferenceService.registerProperty(fillCurve, "fillCurve", this.javaClass, id)
        preferenceService.registerProperty(smoothCurve, "smoothCurve", this.javaClass, id)
        preferenceService.registerProperty(logarithmic, "logarithmic", this.javaClass, id)
        preferenceService.registerProperty(canvasContainer.xAxisShown, "xAxisShown", this.javaClass, id)
        preferenceService.registerProperty(canvasContainer.yAxisShown, "yAxisShown", this.javaClass, id)
        preferenceService.registerProperty(canvasContainer.horizontalLinesVisible, "horizontalLinesVisible", this.javaClass, id)
        preferenceService.registerProperty(canvasContainer.verticalLinesVisible, "verticalLinesVisible", this.javaClass, id)
        preferenceService.registerProperty(showPeak, "showPeak", this.javaClass, id)
    }

    private fun refreshTooltipLabel() {
        try {
            if (source == null) {
                tooltip.text = "---"
                return
            }
            if (logarithmic.get()) logarithmicTooltipText() else linearTooltipText()
        } catch (_: IndexOutOfBoundsException) { }
        catch (_: NullPointerException) { }
    }

    private fun logarithmicTooltipText() {
        val x = canvasContainer.tooltipPosition.get().x
        val freq = xAxis.getValueForDisplay(x).toDouble()
        val bin = FourierMath.binOfFrequency(rate, fftSize, freq) + 1 + frequencyBinSkip
        val rawValue = fftDataArray.getOrElse(bin) { _ -> Float.NEGATIVE_INFINITY }
        val maxValue = rawMaxTracker.data.getOrElse(bin) { _ -> Float.NEGATIVE_INFINITY }
        tooltip.text = "Freq: ${localizationService.formatNumber(freq, "Hz")}\n" +
                "Bin: ${localizationService.formatNumber(bin + 1)}\n" +
                "Scaled: ${localizationService.formatNumber(fftScalar.scaleRaw(rawValue))}"
        if (peakLineVisible.get())
            tooltip.text += "\nMax: ${localizationService.formatNumber(fftScalar.scaleRaw(maxValue))}"
    }

    private fun linearTooltipText() {
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
                    "FFT: ${localizationService.formatNumber(binStart)} - ${localizationService.formatNumber(binEnd)}\n" +
                    "Freq: ${localizationService.formatNumber(minFreq, "Hz")} - ${
                        localizationService.formatNumber(
                            maxFreq,
                            "Hz"
                        )
                    }\n" +
                    "Scaled: ${localizationService.formatNumber(fftScalar.scaleRaw(rawValue))}"
        if (peakLineVisible.get())
            tooltip.text += "\nMax: ${localizationService.formatNumber(fftScalar.scaleRaw(maxValue))}"
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

    private fun changeSmoother() {
        val oldSize = smoother?.dataSize ?: 500
        val new = when (smootherType.get()) {
            SmootherType.MULTIPLICATIVE -> MultiplicativeSmoother()
            SmootherType.FALLOFF -> ExponentialFalloffSmoother()
        }
        new.dataSize = oldSize
        this.smoother = new
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
        val upper = source!!.trimResultTo(fftSize, highPass.get())
        val lower = source!!.bufferBeginningSkipFor(lowPass.get(), fftSize)
        xAxis.lowerBound = FourierMath.frequencyOfBin(lower, rate, fftSize).toDouble()
        xAxis.upperBound = FourierMath.frequencyOfBin(upper, rate, fftSize).toDouble()
    }

    fun handleFFT(array: FloatArray, source: SupportedCaptureSource) {
        if (isPaused) return

        fftDataArray = array
        val oldFftSize = this.fftSize
        this.fftSize = array.size * 2
        this.rate = source.rate
        if (source != this.source || oldFftSize != this.fftSize) {
            this.source = source
            sizeFrequencyAxis()
        }
        var size = source.trimResultTo(this.fftSize, highPass.get())
        frequencyBinSkip = source.bufferBeginningSkipFor(lowPass.get(), this.fftSize)
        size = (size - frequencyBinSkip).coerceAtLeast(10)
        if (smoother.dataSize != size) {
            synchronized(bufferResizeLock) {
                smoother.dataSize = size
                rawMaxTracker.dataSize = size
            }
        }

        smoother.setData(array, frequencyBinSkip, size) { fftScalar.scale(it) }

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
            val padding = 1.0
            gc.stroke = effectiveStartColor.get()

            calculateLocBuffer(buffer, logarithmic.get(), barWidth, step, height, width)

            when (renderMode.get()!!) {
                RenderMode.LINE -> drawLine(gc, height, width, fillCurve.get())
                RenderMode.BAR -> drawBars(gc, barWidth, padding, height, logarithmic.get())
            }

            if (canvasContainer.tooltipContainer.isVisible) refreshTooltipLabel()

            if (showPeak.get() && audioCaptureService.peakValue.value != 0.0F) {
                val s = 4.0
                gc.fill = peakPointUnderColor.value
                gc.fillRect(
                    xAxis.getDisplayPosition(audioCaptureService.peakFrequency.value) - s / 2,
                    height - height * fftScalar.scale(audioCaptureService.peakValue.value) - s / 2,
                    s, s
                )
            }

            if (!peakLineVisible.get()) return
            gc.stroke = peakLineColor.value
            step = calculateStep(1, bufferLength, width)
            barWidth = width / floor(bufferLength.toDouble() / step)
            val tempBuffer = rawMaxTracker.data.map { fftScalar.scale(it) }.toFloatArray()
            calculateLocBuffer(tempBuffer, logarithmic.get(), barWidth, step, height, width)
            drawLine(gc, height, width, false)
        }
    }

    private fun calculateLocBuffer(
        buffer: FloatArray,
        logarithmic: Boolean,
        barWidth: Double,
        step: Double,
        height: Double,
        width: Double
    ) {
        if (fftLocBuffer.data.size != buffer.size)
            fftLocBuffer.data = Array(buffer.size) { _ -> FftLoc(0.0, 0.0, 0.0) }

        var y = 0.0
        var x = 0.0
        var num = 0
        var added = false
        if (logarithmic) {
            val binOffset = if(renderMode.get()!! == RenderMode.LINE) 0 else 1
            var lastX = 0.0
            for (i in buffer.indices) {
                y = max(buffer[i].toDouble(), y); added = false
                x = xAxis.getDisplayPosition(FourierMath.frequencyOfBinD(i + frequencyBinSkip + binOffset, rate, fftSize))
                if (x - lastX < barWidth) continue

                fftLocBuffer.data[num].x = x
                fftLocBuffer.data[num].y = height - height * y
                fftLocBuffer.data[num].raw = y

                num++; lastX = x; y = 0.0; added = true
            }
        } else {
            var stepAccumulator = 0.0
            for (element in buffer) {
                y = max(element.toDouble(), y); added = false
                if (++stepAccumulator < step) continue
                stepAccumulator -= step
                x += barWidth

                fftLocBuffer.data[num].x = x
                fftLocBuffer.data[num].y = height - height * y
                fftLocBuffer.data[num].raw = y

                num++; y = 0.0; added = true
            }
        }

        if (!added) {
            fftLocBuffer.data[num].x = width
            fftLocBuffer.data[num].y = height - height * y
            fftLocBuffer.data[num].raw = y
            num++
        }

        fftLocBuffer.size = num
    }

    private fun drawLine(gc: GraphicsContext, height: Double, width: Double, fill: Boolean) {
        gc.beginPath()
        gc.moveTo(0.0, fftLocBuffer.data[0].y)

        if (smoothCurve.get()) {
            drawSmoothedLine(gc, height, width)
        } else
            for (i in 0 until fftLocBuffer.size)
                gc.lineTo(fftLocBuffer.data[i].x, fftLocBuffer.data[i].y)

        gc.stroke()
        if (!fillUnderLine(fill, gc, width, height)) gc.closePath()
    }

    private fun drawSmoothedLine(gc: GraphicsContext, height: Double, width: Double) {
        val tension = 0.98

        if (lineAngleArray.size != fftLocBuffer.size + 1)
            lineAngleArray = DoubleArray(fftLocBuffer.size + 1)

        for (i in 1 until fftLocBuffer.size - 1) {
            val current = fftLocBuffer.data[i]
            val previousRelativeX = fftLocBuffer.data[i - 1].x - current.x
            val previousRelativeY = fftLocBuffer.data[i - 1].y - current.y
            val nextRelativeX = fftLocBuffer.data[i + 1].x - current.x
            val nextRelativeY = fftLocBuffer.data[i + 1].y - current.y

            if (min(abs(previousRelativeX), abs(nextRelativeX)) < 3) {
                lineAngleArray[i] = Double.NaN
                break
            }

            // Dot product
            val dotProduct = previousRelativeX * nextRelativeX + previousRelativeY * nextRelativeY
            // Magnitudes
            val magBA = hypot(previousRelativeX, previousRelativeY)
            val magBC = hypot(nextRelativeX, nextRelativeY)

            // Angle in radians
            var angleTriangle = acos(dotProduct / (magBA * magBC))
            if (angleTriangle.isNaN()) angleTriangle = PI
            angleTriangle = 2 * PI - angleTriangle - PI
            val angleC = atan2(nextRelativeY, nextRelativeX)

            val isDown = (previousRelativeY <= 0 && nextRelativeY <= 0)
                    || (abs(previousRelativeY) > abs(nextRelativeY) && previousRelativeY <= 0)
                    || (abs(previousRelativeY) < abs(nextRelativeY) && nextRelativeY <= 0)

            lineAngleArray[i] = angleC + if (isDown) angleTriangle / 2 else angleTriangle / -2
        }

        var doSmooth = true
        for (i in 1 until fftLocBuffer.size - 1) {
            val pos = fftLocBuffer.data[i].x to fftLocBuffer.data[i].y
            if (doSmooth && lineAngleArray[i].isNaN())
                doSmooth = false
            if (!doSmooth) {
                gc.lineTo(pos.first, pos.second)
                continue
            }

            val previousY = fftLocBuffer.data[i - 1].y
            val previousX = fftLocBuffer.data[i - 1].x
            val nextX = fftLocBuffer.data.getOrElse(i + 1) { _ -> FftLoc(width, 0.0, 0.0) }.x
            val maxHandleLength = min(pos.first - previousX, nextX - pos.first) / 2

            val handleLength = maxHandleLength * tension

            val handle1 = cos(lineAngleArray[i - 1]) * handleLength to sin(lineAngleArray[i - 1]) * handleLength
            val handle2 = -cos(lineAngleArray[i]) * handleLength to -sin(lineAngleArray[i]) * handleLength

            gc.bezierCurveTo(
                handle1.first + previousX,
                (handle1.second + previousY).coerceIn(0.0, height.coerceAtLeast(1.0)),
                handle2.first + pos.first,
                (handle2.second + pos.second).coerceIn(0.0, height.coerceAtLeast(1.0)),
                pos.first, pos.second
            )
        }

        if (fftLocBuffer.size > 2) {
            val posPrev = fftLocBuffer.data[fftLocBuffer.size - 2]
            val pos = fftLocBuffer.data[fftLocBuffer.size - 1]
            val handleLength = (pos.x - posPrev.x) / 2.0 * tension
            val handle1 = -handleLength to 0.0
            val handle2 =
                cos(lineAngleArray[fftLocBuffer.size - 2]) * handleLength to sin(lineAngleArray[fftLocBuffer.size - 2]) * handleLength
            gc.bezierCurveTo(
                handle2.first + posPrev.x,
                (handle2.second + posPrev.y).coerceIn(0.0, height.coerceAtLeast(1.0)),
                handle1.first + pos.x,
                (handle1.second + pos.y).coerceIn(0.0, height.coerceAtLeast(1.0)),
                pos.x, pos.y
            )
        }

    }

    private fun fillUnderLine(fill: Boolean, gc: GraphicsContext, width: Double, height: Double): Boolean {
        if (!fill) return false
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
        return true
    }

    private fun drawBars(gc: GraphicsContext, barWidth: Double, padding: Double, height: Double, logarithmic: Boolean) {
        var lastX = 0.0
        for (i in 0 until fftLocBuffer.size) {
            val color = effectiveStartColor.get().interpolate(effectiveEndColor.get(), fftLocBuffer.data[i].raw)
            val width = (if (logarithmic) fftLocBuffer.data[i].x - lastX else barWidth) + padding

            gc.fill = color
            gc.fillRect(lastX, fftLocBuffer.data[i].y, width, height - fftLocBuffer.data[i].y)
            lastX = fftLocBuffer.data[i].x
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

    enum class SmootherType {
        MULTIPLICATIVE,
        FALLOFF
    }

    data class FftLoc(var x: Double, var y: Double, var raw: Double)
    data class FftLocBuffer(var size: Int, var data: Array<FftLoc>) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as FftLocBuffer

            if (size != other.size) return false
            if (!data.contentEquals(other.data)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = size
            result = 31 * result + data.contentHashCode()
            return result
        }
    }
}