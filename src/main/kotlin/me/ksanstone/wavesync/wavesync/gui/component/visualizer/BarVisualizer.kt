package me.ksanstone.wavesync.wavesync.gui.component.visualizer

import javafx.beans.binding.DoubleBinding
import javafx.beans.property.*
import javafx.collections.ListChangeListener
import javafx.fxml.FXMLLoader
import javafx.scene.Node
import javafx.scene.canvas.Canvas
import javafx.scene.canvas.GraphicsContext
import javafx.scene.chart.NumberAxis
import javafx.scene.control.Tooltip
import javafx.scene.layout.HBox
import javafx.scene.paint.Color
import javafx.scene.text.Text
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.BAR_CUTOFF
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.BAR_LOW_PASS
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.BAR_SCALING
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.BAR_SMOOTHING
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.DB_MAX
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.DB_MIN
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.DEFAULT_SCALAR_TYPE
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.GAP
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
import java.text.DecimalFormat
import kotlin.math.floor
import kotlin.math.max


class BarVisualizer : AutoCanvas() {

    private var frequencyAxis: NumberAxis
    private var smoother: MagnitudeSmoother
    private var canvasHeightProperty: DoubleBinding
    private var localizationService: LocalizationService =
        WaveSyncBootApplication.applicationContext.getBean(LocalizationService::class.java)
    private val tooltip: Tooltip = Tooltip("---")
    private val startColor: ObjectProperty<Color> = SimpleObjectProperty(Color.rgb(255, 120, 246))
    private val endColor: ObjectProperty<Color> = SimpleObjectProperty(Color.AQUA)
    val smoothing: FloatProperty = SimpleFloatProperty(BAR_SMOOTHING)
    val cutoff: IntegerProperty = SimpleIntegerProperty(BAR_CUTOFF)
    val lowPass: IntegerProperty = SimpleIntegerProperty(BAR_LOW_PASS)
    val targetBarWidth: IntegerProperty = SimpleIntegerProperty(TARGET_BAR_WIDTH)
    val gap: IntegerProperty = SimpleIntegerProperty(GAP)

    val scalarType: ObjectProperty<FFTScalarType> = SimpleObjectProperty(DEFAULT_SCALAR_TYPE)
    val linearScaling: FloatProperty = SimpleFloatProperty(BAR_SCALING)
    val dbMin: FloatProperty = SimpleFloatProperty(DB_MIN)
    val dbMax: FloatProperty = SimpleFloatProperty(DB_MAX)

    private lateinit var fftScalar: FFTScalar<*>

    init {
        stylesheets.add("/styles/bar-visualizer.css")

        frequencyAxis = NumberAxis(0.0, 20000.0, 1000.0)
        frequencyAxis.childrenUnmodifiable
            .addListener(ListChangeListener<Node?> { c: ListChangeListener.Change<out Node?> ->
                while (c.next()) {
                    if (c.wasAdded()) {
                        for (mark in c.addedSubList) {
                            if (mark is Text) {
                                val parsed = DecimalFormat("###,###.###").parse(mark.text).toDouble()
                                if (parsed == frequencyAxis.lowerBound) {
                                    mark.text =
                                        if (mark.text.contains(" ")) mark.text else " ".repeat(mark.text.length * 2) + mark.text
                                } else if (parsed == frequencyAxis.upperBound) {
                                    mark.text =
                                        if (mark.text.contains(" ")) mark.text else mark.text + " ".repeat(mark.text.length * 2)
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

        changeScalar()
        scalarType.addListener { _ -> changeScalar() }
        linearScaling.addListener{ _ -> refreshScalar() }
        dbMax.addListener{ _ -> refreshScalar() }
        dbMin.addListener{ _ -> refreshScalar() }

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

        Tooltip.install(this, tooltip)

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
        preferenceService.registerProperty(smoothing, "$id-smoothing")
        preferenceService.registerProperty(linearScaling, "$id-scaling")
        preferenceService.registerProperty(cutoff, "$id-cutoff")
        preferenceService.registerProperty(lowPass, "$id-lowPass")
        preferenceService.registerProperty(targetBarWidth, "$id-targetBarWidth")
        preferenceService.registerProperty(gap, "$id-gap")
        preferenceService.registerProperty(scalarType, "$id-fftScalar", FFTScalarType::class.java)
        preferenceService.registerProperty(dbMin, "$id-dbMin")
        preferenceService.registerProperty(dbMax, "$id-dbMax")
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
                (fftScalar as DeciBelFFTScalar).update(DeciBelFFTScalarParameters(rangeMin = dbMin.get(), rangeMax = dbMax.get()))
            }
        }
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
            fftScalar.scale(fl)
        }.toFloatArray()
    }

    private fun calculateStep(targetWidth: Int, bufferLength: Int, width: Double): Double {
        val estimatedWidth = width / bufferLength
        return (targetWidth.toDouble() / estimatedWidth).coerceAtLeast(1.0)
    }

    override fun draw(gc: GraphicsContext, deltaT: Double, now: Long, width: Double, height: Double) {
        smoother.applySmoothing(deltaT)

        val canvasHeight = canvasHeightProperty.doubleValue()
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