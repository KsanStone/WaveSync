package me.ksanstone.wavesync.wavesync.gui.component.visualizer

import javafx.beans.binding.IntegerBinding
import javafx.beans.property.BooleanProperty
import javafx.beans.property.IntegerProperty
import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.fxml.FXMLLoader
import javafx.geometry.Orientation
import javafx.scene.canvas.GraphicsContext
import javafx.scene.image.WritableImage
import javafx.scene.layout.HBox
import javafx.util.Duration
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.DEFAULT_BAR_CUTOFF
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.DEFAULT_BAR_LOW_PASS
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.DEFAULT_SPECTROGRAM_GRADIENT
import me.ksanstone.wavesync.wavesync.WaveSyncBootApplication
import me.ksanstone.wavesync.wavesync.gui.controller.visualizer.spectrogram.SpectrogramSettingsController
import me.ksanstone.wavesync.wavesync.gui.gradient.pure.SGradient
import me.ksanstone.wavesync.wavesync.gui.utility.AutoCanvas
import me.ksanstone.wavesync.wavesync.service.AudioCaptureService
import me.ksanstone.wavesync.wavesync.service.LocalizationService
import me.ksanstone.wavesync.wavesync.service.PreferenceService
import me.ksanstone.wavesync.wavesync.service.SupportedCaptureSource
import me.ksanstone.wavesync.wavesync.service.fftScaling.DeciBelFFTScalar
import me.ksanstone.wavesync.wavesync.service.fftScaling.DeciBelFFTScalarParameters
import me.ksanstone.wavesync.wavesync.utility.RollingBuffer
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

class SpectrogramVisualizer : AutoCanvas() {

    val bufferDuration: ObjectProperty<Duration> = SimpleObjectProperty(Duration.seconds(20.0))
    val orientation = SimpleObjectProperty(Orientation.VERTICAL)
    val gradient = SimpleObjectProperty<SGradient>(DEFAULT_SPECTROGRAM_GRADIENT)
    val cutoff: IntegerProperty = SimpleIntegerProperty(DEFAULT_BAR_CUTOFF)
    val lowPass: IntegerProperty = SimpleIntegerProperty(DEFAULT_BAR_LOW_PASS)
    val bindToBar: BooleanProperty = SimpleBooleanProperty(false)

    private var buffer: RollingBuffer<FloatArray> = RollingBuffer(100, FloatArray(0))
    private var stripeBuffer: FloatArray = FloatArray(0)
    private val fftArraySize: IntegerBinding = WaveSyncBootApplication.applicationContext.getBean(AudioCaptureService::class.java).fftSize.divide(2)
    private val fftRate: IntegerProperty = WaveSyncBootApplication.applicationContext.getBean(AudioCaptureService::class.java).fftRate

    private var imageTop = WritableImage(1,1)
    private var imageBottom = WritableImage(1,1)
    private var imageOffset = 0
    private var lastWritten = 0L
    private var bufferPos = 0
    private var stripeStepAccumulator = 0.0

    private var canvasWidth = 0
    private var canvasHeight = 0

    private val scalar = DeciBelFFTScalar()
    private var source: SupportedCaptureSource? = null

    init {
        scalar.update(DeciBelFFTScalarParameters(-90F, 0.0F))
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
            sizeTimeAxis()
        }

        sizeTimeAxis()
    }

    fun initializeSettingMenu() {
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

    fun registerPreferences(id: String, preferenceService: PreferenceService) {
        preferenceService.registerDurationProperty(bufferDuration, "bufferDuration", this.javaClass, id)
        preferenceService.registerProperty(canvasContainer.xAxisShown, "xAxisShown", this.javaClass, id)
        preferenceService.registerProperty(canvasContainer.yAxisShown, "yAxisShown", this.javaClass, id)
        preferenceService.registerProperty(canvasContainer.horizontalLinesVisible, "horizontalLinesVisible", this.javaClass, id)
        preferenceService.registerProperty(canvasContainer.verticalLinesVisible, "verticalLinesVisible", this.javaClass, id)
        preferenceService.registerProperty(cutoff, "cutoff", this.javaClass, id)
        preferenceService.registerProperty(lowPass, "lowPass", this.javaClass, id)
        preferenceService.registerSGradientProperty(gradient, "gradient", this.javaClass, id)
    }

    private fun changeBufferDuration(time: Duration, rate: Int) {
        val newSize = rate * time.toSeconds()
        this.buffer = RollingBuffer(newSize.toInt(), FloatArray(fftArraySize.value))
    }

    private fun changeBufferWidth() {
        changeBufferDuration(bufferDuration.get(), fftRate.value)
        stripeBuffer = FloatArray(fftArraySize.value)
    }

    fun handleFFT(res: FloatArray, source: SupportedCaptureSource) {
        if (isPaused) return
        this.source = source
        buffer.incrementPosition()
        System.arraycopy(res, 0, buffer.last(), 0, res.size)
    }

    private fun resetBuffer() {
        lastWritten = 0L
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

    private fun sizeTimeAxis() {
        if (orientation.value == Orientation.HORIZONTAL) {
            xAxis.lowerBound = -bufferDuration.get().toSeconds()
            xAxis.upperBound = 0.0
        } else {
            yAxis.lowerBound = -bufferDuration.get().toSeconds()
            yAxis.upperBound = 0.0
        }
    }

    private fun drawChunk() {
        val size = when(orientation.value!!) {
            Orientation.HORIZONTAL -> canvasWidth
            Orientation.VERTICAL -> canvasHeight
        }

        val chunkStep = buffer.size.toDouble() / size
        val chunksWritten = (buffer.written - lastWritten).coerceIn(0, buffer.size.toLong()).toInt()
        lastWritten = buffer.written
        bufferPos -= chunksWritten
        bufferPos = bufferPos.coerceAtLeast(0)
        val chunksLeft = buffer.size - bufferPos

        val stripesToDraw = floor(chunksLeft / chunkStep).toInt()

        for (i in 0 until stripesToDraw) {
            stripeBuffer.fill(0.0f)
            var iterationsDone = 0

            while(stripeStepAccumulator < chunkStep) {
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

            if(iterationsDone == 0) continue

            val avgFactor = 1.0F / iterationsDone.coerceAtLeast(1)
            for (j in stripeBuffer.indices) {
                stripeBuffer[j] *= avgFactor
            }

            val stripeWidth = 1.0 / chunkStep
            drawStripe(stripeBuffer, stripeWidth)
        }
    }

    private fun drawStripe(stripe: FloatArray, width: Double) {
        if (source == null) return
        val stripePixelLength = when (orientation.value!!) {
            Orientation.HORIZONTAL -> canvasHeight
            Orientation.VERTICAL -> canvasWidth
        }
        val size = when (orientation.value!!) {
            Orientation.HORIZONTAL -> canvasWidth
            Orientation.VERTICAL -> canvasHeight
        }

        var effectiveStripeLength = source!!.trimResultTo(stripe.size * 2, cutoff.get())
        val frequencyBinSkip = source!!.bufferBeginningSkipFor(lowPass.get(), stripe.size * 2)
        effectiveStripeLength -= frequencyBinSkip

        val step = effectiveStripeLength.toDouble() / stripePixelLength
        val processedStripe = FloatArray(stripePixelLength)
        var accumulator = 0.0
        var pos = 0
        var y = 0.0F
        var added = false

        if (step >= 1) {
            for (i in frequencyBinSkip until frequencyBinSkip + effectiveStripeLength) {
                val element = stripe[i]
                y = max(element, y); added = false
                if (++accumulator < step) continue
                accumulator -= step

                processedStripe[pos.coerceIn(0, processedStripe.size)] = scalar.scale(y)

                y = 0.0F
                pos++
                added = true
            }
        } else {
            var i = 0
            for (j in 0 until stripePixelLength) {
                val element = stripe[i]
                y = element
                if (accumulator >= step) {
                    accumulator -= step
                } else {
                    accumulator++
                    i++
                }
                processedStripe[pos.coerceIn(0, processedStripe.size)] = scalar.scale(y)
                added = true
                pos++
            }
        }

        if (!added)
            processedStripe[pos.coerceIn(0, processedStripe.size)] = scalar.scale(y)

        val writer = imageTop.pixelWriter
        val effectiveGradient = gradient.value
        val realOffset = ceil(width).toInt().coerceIn(1, imageOffset + 1)
        for (offset in 0 until realOffset) {
            when (orientation.value!!) {
                Orientation.HORIZONTAL -> {
                    for (i in 0 until stripePixelLength) {
                        writer.setColor(imageOffset - offset, i, effectiveGradient[processedStripe[i]])
                    }
                }

                Orientation.VERTICAL -> {
                    for (i in 0 until stripePixelLength) {
                        writer.setColor(
                            i,
                            (imageTop.height - imageOffset - 1 - offset).toInt(),
                            effectiveGradient[processedStripe[i]]
                        )
                    }
                }
            }
        }

        imageOffset += realOffset
        if (imageOffset >= size) {
            imageOffset = 0
            flipImageBuffers()
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
        drawChunk()

        if (orientation.value == Orientation.HORIZONTAL) {
            val rOffset = imageOffset.toDouble()
            gc.drawImage(imageBottom, -rOffset, 0.0)
            gc.drawImage(imageTop, width - rOffset, 0.0)
        } else {
            val rOffset = height - imageOffset
            gc.drawImage(imageTop, 0.0, -rOffset)
            gc.drawImage(imageBottom, 0.0, height - rOffset)
        }
    }
}