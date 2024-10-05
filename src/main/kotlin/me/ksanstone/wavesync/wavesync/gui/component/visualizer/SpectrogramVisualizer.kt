package me.ksanstone.wavesync.wavesync.gui.component.visualizer

import javafx.beans.binding.IntegerBinding
import javafx.beans.property.*
import javafx.fxml.FXMLLoader
import javafx.geometry.Orientation
import javafx.scene.canvas.GraphicsContext
import javafx.scene.chart.NumberAxis
import javafx.scene.chart.ValueAxis
import javafx.scene.image.WritableImage
import javafx.scene.layout.Background
import javafx.scene.layout.HBox
import javafx.scene.paint.CycleMethod
import javafx.scene.paint.LinearGradient
import javafx.util.Duration
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.DEFAULT_BAR_CUTOFF
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.DEFAULT_BAR_LOW_PASS
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.DEFAULT_DB_MAX
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.DEFAULT_DB_MIN
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.DEFAULT_LOGARITHMIC_MODE
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.DEFAULT_SPECTROGRAM_GRADIENT
import me.ksanstone.wavesync.wavesync.WaveSyncBootApplication
import me.ksanstone.wavesync.wavesync.gui.controller.visualizer.spectrogram.SpectrogramSettingsController
import me.ksanstone.wavesync.wavesync.gui.gradient.pure.GradientSerializer
import me.ksanstone.wavesync.wavesync.gui.utility.AutoCanvas
import me.ksanstone.wavesync.wavesync.service.*
import me.ksanstone.wavesync.wavesync.service.fftScaling.DeciBelFFTScalar
import me.ksanstone.wavesync.wavesync.service.fftScaling.DeciBelFFTScalarParameters
import me.ksanstone.wavesync.wavesync.utility.CachingRangeMapper
import me.ksanstone.wavesync.wavesync.utility.FreeRangeMapper
import me.ksanstone.wavesync.wavesync.utility.LogRangeMapper
import me.ksanstone.wavesync.wavesync.utility.RollingBuffer
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

class SpectrogramVisualizer : AutoCanvas() {

    val bufferDuration: ObjectProperty<Duration> = SimpleObjectProperty(Duration.seconds(20.0))
    val orientation = SimpleObjectProperty(Orientation.VERTICAL)
    val gradient = SimpleObjectProperty(DEFAULT_SPECTROGRAM_GRADIENT)
    val effectiveHighPass: IntegerProperty = SimpleIntegerProperty(DEFAULT_BAR_CUTOFF)
    val effectiveLowPass: IntegerProperty = SimpleIntegerProperty(DEFAULT_BAR_LOW_PASS)
    val effectiveRangeMin: FloatProperty = SimpleFloatProperty(DEFAULT_DB_MIN)
    val effectiveRangeMax: FloatProperty = SimpleFloatProperty(DEFAULT_DB_MAX)
    val effectiveLogarithmic: BooleanProperty = SimpleBooleanProperty(DEFAULT_LOGARITHMIC_MODE)

    val highPass: IntegerProperty = SimpleIntegerProperty(DEFAULT_BAR_CUTOFF)
    val lowPass: IntegerProperty = SimpleIntegerProperty(DEFAULT_BAR_LOW_PASS)
    val rangeMin: FloatProperty = SimpleFloatProperty(DEFAULT_DB_MIN)
    val rangeMax: FloatProperty = SimpleFloatProperty(DEFAULT_DB_MAX)
    val logarithmic: BooleanProperty = SimpleBooleanProperty(DEFAULT_LOGARITHMIC_MODE)

    private var buffer: RollingBuffer<FloatArray> = RollingBuffer(100) { FloatArray(0) }
    private var stripeBuffer: FloatArray = FloatArray(0)
    private val fftArraySize: IntegerBinding =
        WaveSyncBootApplication.applicationContext.getBean(AudioCaptureService::class.java).fftSize.divide(2)
    private val fftRate: IntegerProperty =
        WaveSyncBootApplication.applicationContext.getBean(AudioCaptureService::class.java).fftRate
    private val gradientSerializer: GradientSerializer =
        WaveSyncBootApplication.applicationContext.getBean(GradientSerializer::class.java)

    private var imageTop = WritableImage(1, 1)
    private var imageBottom = WritableImage(1, 1)
    private var imageOffset = 0
    private var lastWritten = 0L
    private var bufferPos = 0
    private var stripeStepAccumulator = 0.0
    private var processedStripe = FloatArray(1)
    private var stripeMapper: CachingRangeMapper = CachingRangeMapper(FreeRangeMapper(0..1, 2..4))
    private var mapperLog: Boolean = false

    private var canvasWidth = 0
    private var canvasHeight = 0

    private val scalar = DeciBelFFTScalar()
    private var source: SupportedCaptureSource? = null

    init {
        scalar.update(DeciBelFFTScalarParameters(rangeMin.value, rangeMax.value))
        changeBufferWidth()
        fftArraySize.addListener { _, _, _ ->
            changeBufferWidth()
        }

        fftRate.addListener { _, _, v ->
            changeBufferDuration(bufferDuration.get(), v.toInt())
            resetBuffer()
        }

        bufferDuration.addListener { _, _, _ ->
            changeBufferDuration(bufferDuration.get(), fftRate.get())
            resetBuffer()
            sizeAxis()
        }

        listOf(orientation, highPass, lowPass, effectiveLogarithmic).forEach {
            it.addListener { _ ->
                resetBuffer()
                sizeAxis()
            }
        }

        listOf(effectiveRangeMax, effectiveRangeMin).forEach {
            it.addListener { _ ->
                scalar.update(DeciBelFFTScalarParameters(effectiveRangeMin.value, effectiveRangeMax.value))
                resetBuffer()
                sizeAxis()
            }
        }

        sizeAxis()
        gradient.addListener { _ ->
            resetBuffer()
            sizeAxis()
        }
    }

    override fun initializeSettingMenu() {
        val loader = FXMLLoader()
        loader.location = javaClass.classLoader.getResource("layout/spectrogram")
        loader.resources =
            WaveSyncBootApplication.applicationContext.getBean(LocalizationService::class.java).getDefault()
        val controls: HBox =
            loader.load(javaClass.classLoader.getResourceAsStream("layout/spectrogram/spectrogramSettings.fxml"))
        val controller: SpectrogramSettingsController = loader.getController()
        controller.spectrogramChartSettingsController.initialize(this)
        controlPane.children.add(controls)
    }

    override fun registerPreferences(id: String, preferenceService: PreferenceService) {
        preferenceService.registerDurationProperty(bufferDuration, "bufferDuration", this.javaClass, id)
        preferenceService.registerProperty(canvasContainer.xAxisShown, "xAxisShown", this.javaClass, id)
        preferenceService.registerProperty(canvasContainer.yAxisShown, "yAxisShown", this.javaClass, id)
        preferenceService.registerProperty(
            canvasContainer.horizontalLinesVisible,
            "horizontalLinesVisible",
            this.javaClass,
            id
        )
        preferenceService.registerProperty(
            canvasContainer.verticalLinesVisible,
            "verticalLinesVisible",
            this.javaClass,
            id
        )
        preferenceService.registerProperty(highPass, "cutoff", this.javaClass, id)
        preferenceService.registerProperty(lowPass, "lowPass", this.javaClass, id)
        preferenceService.registerSGradientProperty(gradient, "gradient", this.javaClass, id)
        preferenceService.registerProperty(orientation, "orientation", Orientation::class.java, this.javaClass, id)
        preferenceService.registerProperty(rangeMax, "rangeMax", this.javaClass, id)
        preferenceService.registerProperty(rangeMin, "rangeMin", this.javaClass, id)
        preferenceService.registerProperty(logarithmic, "logarithmic", this.javaClass, id)
    }

    fun setBindEffective(v: Boolean) {
        effectiveLowPass.unbind()
        effectiveHighPass.unbind()
        effectiveRangeMax.unbind()
        effectiveRangeMin.unbind()
        effectiveLogarithmic.unbind()

        if (v) {
            effectiveLowPass.bind(lowPass)
            effectiveHighPass.bind(highPass)
            effectiveRangeMin.bind(rangeMin)
            effectiveRangeMax.bind(rangeMax)
            effectiveLogarithmic.bind(logarithmic)
        }
    }

    private fun changeBufferDuration(time: Duration, rate: Int) {
        val newSize = rate * time.toSeconds()
        this.buffer = RollingBuffer(newSize.toInt()) { FloatArray(fftArraySize.value) }
    }

    private fun changeBufferWidth() {
        changeBufferDuration(bufferDuration.get(), fftRate.value)
        stripeBuffer = FloatArray(fftArraySize.value)
    }

    fun handleFFT(event: AudioCaptureService.FftEvent) {
        if (!canDraw || buffer.size == 2) return
        this.source = event.source
        buffer.incrementPosition()
        System.arraycopy(event.data, 0, buffer.last(), 0, event.data.size)
    }

    private fun resetBuffer() {
        lastWritten = buffer.written - (canvasWidth + canvasHeight).toLong()
        imageOffset = 0
        stripeStepAccumulator = 0.0
        bufferPos = 0
    }

    private fun createImageBuffers() {
        imageTop = WritableImage(canvasWidth.coerceAtLeast(1), canvasHeight.coerceAtLeast(1))
        imageBottom = WritableImage(canvasWidth.coerceAtLeast(1), canvasHeight.coerceAtLeast(1))
    }

    private fun flipImageBuffers() {
        val temp = imageTop
        imageTop = imageBottom
        imageBottom = temp
    }

    private fun sizeAxis() {
        if (orientation.value == Orientation.HORIZONTAL) {
            timeAxis(xAxis)
            valAxis(yAxis)
        } else {
            timeAxis(yAxis)
            valAxis(xAxis)
        }
    }

    private fun timeAxis(axis: ValueAxis<Number>) {
        axis.lowerBound = -bufferDuration.get().toSeconds()
        axis.upperBound = 0.0
        (axis as NumberAxis).tickUnit = 1.0
        axis.background = null
    }

    private fun valAxis(axis: ValueAxis<Number>) {
        axis.lowerBound = effectiveRangeMin.value.toDouble()
        axis.upperBound = effectiveRangeMax.value.toDouble()
        (axis as NumberAxis).tickUnit = 10.0
        axis.background = Background.fill(
            LinearGradient(
                0.0,
                if (orientation.value == Orientation.HORIZONTAL) 1.0 else 0.0,
                if (orientation.value == Orientation.HORIZONTAL) 0.0 else 1.0,
                if (orientation.value == Orientation.HORIZONTAL) 0.0 else 0.0,
                true,
                CycleMethod.NO_CYCLE,
                gradientSerializer.toStops(gradient.value)
            )
        )
    }

    private fun drawChunk(isHorizontal: Boolean) {
        val size = if (isHorizontal) canvasWidth else canvasHeight

        val chunkStep = buffer.size.toDouble() / size
        val chunksWritten = (buffer.written - lastWritten).coerceIn(0, buffer.size.toLong()).toInt()
        lastWritten = buffer.written
        bufferPos -= chunksWritten
        bufferPos = bufferPos.coerceAtLeast(0)
        val chunksLeft = buffer.size - bufferPos

        val stripesToDraw = floor(chunksLeft / chunkStep).toInt()

        for (i in 0 until stripesToDraw) {
            var iterationsDone = 0

            // First iteration done separately to fill the buffer.
            if (bufferPos < buffer.size && stripeStepAccumulator < chunkStep) {
                val currentRes = buffer[bufferPos]
                System.arraycopy(currentRes, 0, stripeBuffer, 0, stripeBuffer.size)
                stripeStepAccumulator++
                iterationsDone++
                bufferPos++
            }

            while (stripeStepAccumulator < chunkStep) {
                if (bufferPos >= buffer.size) break
                val currentRes = buffer[bufferPos]
                for (j in stripeBuffer.indices) {
                    stripeBuffer[j] += currentRes[j]
                }

                stripeStepAccumulator++
                iterationsDone++
                bufferPos++
            }

            stripeStepAccumulator -= chunkStep

            if (iterationsDone == 0) continue

            val avgFactor = 1.0F / iterationsDone.coerceAtLeast(1)
            for (j in stripeBuffer.indices) {
                stripeBuffer[j] *= avgFactor
            }

            val stripeWidth = 1.0 / chunkStep
            drawStripe(stripeBuffer, stripeWidth, isHorizontal, effectiveLogarithmic.value)
        }
    }

    private var justSwitched = false

    private fun drawStripe(stripe: FloatArray, width: Double, isHorizontal: Boolean, isLog: Boolean) {
        if (source == null) return
        val stripePixelLength: Int
        val size: Int
        var realWidth = width
        if (isHorizontal) {
            stripePixelLength = canvasHeight
            size = canvasWidth
        } else {
            stripePixelLength = canvasWidth
            size = canvasHeight
        }

        if (justSwitched) {
            if (imageOffset != 0) {
                realWidth += imageOffset
                imageOffset = 0
            }
            justSwitched = false
        }

        var effectiveStripeLength = source!!.trimResultTo(stripe.size * 2, effectiveHighPass.get())
        val frequencyBinSkip = source!!.bufferBeginningSkipFor(effectiveLowPass.get(), stripe.size * 2)
        effectiveStripeLength -= frequencyBinSkip


        val rate = source!!.rate
        val fftSize = stripe.size * 2
        val startFreq = FourierMath.frequencyOfBin(frequencyBinSkip, rate, fftSize)
        val endFreq = FourierMath.frequencyOfBin(frequencyBinSkip + effectiveStripeLength, rate, fftSize)

        val resizeStripe = processedStripe.size != stripePixelLength
        if (resizeStripe)
            processedStripe = FloatArray(stripePixelLength)

        val secRangeEqual = stripeMapper.to.first == startFreq && stripeMapper.to.last == endFreq
        if (resizeStripe || !secRangeEqual || isLog != mapperLog) {
            val newToRange = startFreq..endFreq
            stripeMapper = CachingRangeMapper(
                if (isLog) {
                    LogRangeMapper(processedStripe.indices, newToRange)
                } else {
                    FreeRangeMapper(processedStripe.indices, newToRange)
                },
            ) { FourierMath.binOfFrequency(rate, fftSize, it) }
            mapperLog = isLog
        }

        for (i in processedStripe.indices) {
            val rMin = stripeMapper.forwards(i)
            val rMax = if (i + 1 == processedStripe.size) processedStripe.size
            else (stripeMapper.forwards(i + 1)) - 1

            var value = 0.0F
            for (j in rMin..rMax.coerceAtLeast(rMin)) {
                value = max(value, stripe[j])
            }
            processedStripe[i] = scalar.scale(value)
        }

        val writer = imageTop.pixelWriter
        val effectiveGradient = gradient.value
        val realOffset = ceil(realWidth).toInt().coerceIn(1, size - imageOffset)

        try {
            for (offset in 0 until realOffset) {
                if (isHorizontal) {
                    for (i in 0 until stripePixelLength) {
                        writer.setArgb(
                            imageOffset + offset,
                            stripePixelLength - i - 1,
                            effectiveGradient.argb(processedStripe[i])
                        )
                    }
                } else {
                    for (i in 0 until stripePixelLength) {
                        writer.setArgb(
                            i,
                            (imageTop.height - imageOffset - 1 + offset).toInt(),
                            effectiveGradient.argb(processedStripe[i])
                        )
                    }
                }
            }
        } catch (ignored: IndexOutOfBoundsException) {
        }

        imageOffset += realOffset
        if (imageOffset >= size) {
            imageOffset %= size
            flipImageBuffers()
            justSwitched = true
        }
    }

    override fun draw(gc: GraphicsContext, deltaT: Double, now: Long, width: Double, height: Double) {
        if (width.toInt() != canvasWidth || height.toInt() != canvasHeight) {
            canvasWidth = width.toInt()
            canvasHeight = height.toInt()
            resetBuffer()
            createImageBuffers()
        }

        gc.isImageSmoothing = false
        gc.clearRect(0.0, 0.0, width, height)
        val isHorizontal = orientation.value == Orientation.HORIZONTAL
        drawChunk(isHorizontal)

        if (isHorizontal) {
            val rOffset = imageOffset.toDouble()
            gc.drawImage(imageBottom, -rOffset, 0.0)
            gc.drawImage(imageTop, width - rOffset, 0.0)
        } else {
            val rOffset = height - imageOffset
            gc.drawImage(imageTop, 0.0, -rOffset)
            gc.drawImage(imageBottom, 0.0, height - rOffset)
        }
    }

    override fun usedState(state: Boolean) {
        if (state) {
            resetBuffer()
            createImageBuffers()
            changeBufferWidth()
        } else {
            this.buffer = RollingBuffer(2) { FloatArray(0) }
            this.imageTop = WritableImage(1, 1)
            this.imageBottom = WritableImage(1, 1)
        }
    }

    override fun registerListeners(acs: AudioCaptureService) {
        acs.registerFFTObserver(0, this::handleFFT)
    }
}