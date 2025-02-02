package me.ksanstone.wavesync.wavesync.gui.component.visualizer

import com.huskerdev.openglfx.canvas.GLCanvas
import javafx.application.Platform
import javafx.beans.binding.IntegerBinding
import javafx.beans.property.*
import javafx.beans.value.ObservableValue
import javafx.fxml.FXMLLoader
import javafx.geometry.Orientation
import javafx.scene.canvas.GraphicsContext
import javafx.scene.chart.NumberAxis
import javafx.scene.chart.ValueAxis
import javafx.scene.control.Label
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
import me.ksanstone.wavesync.wavesync.gui.gradient.pure.SGradient
import me.ksanstone.wavesync.wavesync.gui.utility.AutoCanvas
import me.ksanstone.wavesync.wavesync.gui.utility.GlUtil
import me.ksanstone.wavesync.wavesync.service.*
import me.ksanstone.wavesync.wavesync.service.fftScaling.DeciBelFFTScalar
import me.ksanstone.wavesync.wavesync.service.fftScaling.DeciBelFFTScalarParameters
import me.ksanstone.wavesync.wavesync.service.fftScaling.FFTScalar
import me.ksanstone.wavesync.wavesync.utility.FreeRangeMapper
import me.ksanstone.wavesync.wavesync.utility.LogRangeMapper
import me.ksanstone.wavesync.wavesync.utility.RangeMapper
import me.ksanstone.wavesync.wavesync.utility.size
import org.lwjgl.opengl.GL30.*
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.locks.ReentrantLock

class SpectrogramVisualizer : AutoCanvas(useGL = true) {

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

    private val acs = WaveSyncBootApplication.applicationContext.getBean(AudioCaptureService::class.java)
    private val fftArraySize: IntegerBinding = acs.fftSize.divide(2)
    private val fftRate: IntegerProperty = acs.fftRate
    private val captureRate: ObservableValue<Int> = acs.source.map { it?.rate ?: 10 }
    private val gradientSerializer: GradientSerializer =
        WaveSyncBootApplication.applicationContext.getBean(GradientSerializer::class.java)
    private var localizationService: LocalizationService =
        WaveSyncBootApplication.applicationContext.getBean(LocalizationService::class.java)
    private val tooltip = Label()
    private val scalar = DeciBelFFTScalar()
    private var source: SupportedCaptureSource? = null

    init {
        scalar.update(DeciBelFFTScalarParameters(rangeMin.value, rangeMax.value))

        fftArraySize.addListener { _, _, _ ->
            updateBuffer()
        }

        fftRate.addListener { _, _, _ ->
            updateBuffer()
        }

        bufferDuration.addListener { _, _, _ ->
            updateBuffer()
            sizeAxis()
        }

        listOf(orientation, highPass, lowPass, effectiveLogarithmic).forEach {
            it.addListener { _ ->
                sizeAxis()
            }
        }

        listOf(effectiveRangeMax, effectiveRangeMin).forEach {
            it.addListener { _ ->
                scalar.update(DeciBelFFTScalarParameters(effectiveRangeMin.value, effectiveRangeMax.value))
                sizeAxis()
            }
        }

        sizeAxis()
        gradient.addListener { _ ->
            sizeAxis()
            if (::glData.isInitialized) glData.scheduleSettingChange(changeGradient = true)
        }

        graphCanvas.tooltipEnabled.value = true
        graphCanvas.tooltipContainer.children.add(tooltip)
        tooltip.textProperty().bind(graphCanvas.tooltipPosition.map {
            if (!::glData.isInitialized) {
                return@map ""
            }
            val pos = when (orientation.value!!) {
                Orientation.HORIZONTAL -> it.y
                Orientation.VERTICAL -> it.x
            }
            val scaledBin1 = glData.mapper.forwards(pos.toInt())
            val freq1 = FourierMath.frequencyOfBin(scaledBin1, captureRate.value, fftArraySize.value * 2)
            val scaledBin2 = glData.mapper.forwards(pos.toInt()).plus(1).coerceAtMost(glData.mapper.to.last)
            val freq2 = FourierMath.frequencyOfBin(scaledBin2, captureRate.value, fftArraySize.value * 2)

            return@map "${localizationService.formatNumber(freq1, "Hz")} ${
                localizationService.formatNumber(
                    freq2,
                    "Hz"
                )
            }"
        })
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
        registerGraphPreferences(id, preferenceService)
        preferenceService.registerDurationProperty(bufferDuration, "bufferDuration", this.javaClass, id)
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


    fun handleFFT(event: AudioCaptureService.FftEvent) {
        if (!canDraw || event.data.size != fftArraySize.value || !::glData.isInitialized) return
        this.source = event.source
        glData.scheduleSendRow(event.data)
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

    private lateinit var glData: GlData

    /**
     * Contains all the buffer handles
     */
    data class GlData(
        val scalar: FFTScalar<*>,
        var sampleBuffer: Int = 0,
        /**
         * Number of entries
         */
        var sampleBufferWidth: Int = 0,
        /**
         * Size of each entry
         */
        var sampleBufferHeight: Int = 0,
        var sampleBufferPosition: Int = 0,
        var program: Int = 0,

        var entryMapperBuffer: Int = 0,
        var mapper: RangeMapper = FreeRangeMapper(0..1, 0..1),

        var gradientBuffer: Int = 0,

        var sendBuffer: Queue<FloatArray> = ConcurrentLinkedQueue(),
        var resizeBuffer: Boolean = false,
        var changeGradient: Boolean = true, // initial setup

        var disposed: Boolean = false
    ) {

        companion object {
            const val GRADIENT_TEXTURE_SIZE = 1024
        }

        init {
            sampleBuffer = glGenTextures()
            entryMapperBuffer = glGenTextures()
            gradientBuffer = glGenTextures()
            val vertexShader = GlUtil.compileShader("/shaders/spectrogram.vert", GL_VERTEX_SHADER)
            val fragmentShader = GlUtil.compileShader("/shaders/spectrogram.frag", GL_FRAGMENT_SHADER)
            program = GlUtil.linkProgram(listOf(vertexShader, fragmentShader))

            glBindTexture(GL_TEXTURE_1D, gradientBuffer)
            glTexImage1D(GL_TEXTURE_1D, 0, GL_RGBA32F, GRADIENT_TEXTURE_SIZE, 0, GL_RGBA, GL_FLOAT, null as ByteBuffer?)
            glTexParameteri(GL_TEXTURE_1D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
            glTexParameteri(GL_TEXTURE_1D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
            glTexParameteri(GL_TEXTURE_1D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
        }

        fun scheduleSettingChange(resizeBuffer: Boolean = false, changeGradient: Boolean = false) {
            if (resizeBuffer) this.resizeBuffer = true
            if (changeGradient) this.changeGradient = true
        }

        private fun sampleBuffer(length: Int, sampleSize: Int) {
            glBindTexture(GL_TEXTURE_2D, sampleBuffer)
            glTexImage2D(GL_TEXTURE_2D, 0, GL_R32F, sampleSize, length, 0, GL_RED, GL_FLOAT, null as ByteBuffer?)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
            sampleBufferWidth = length
            sampleBufferHeight = sampleSize
            sampleBufferPosition = 0
            glBindTexture(GL_TEXTURE_2D, 0)
        }

        fun entryMapperBuffer(mapper: RangeMapper) {
            this.mapper = mapper
            glBindTexture(GL_TEXTURE_1D, entryMapperBuffer)
            glTexImage1D(
                GL_TEXTURE_1D,
                0,
                GL_R32I,
                mapper.from.size(),
                0,
                GL_RED_INTEGER,
                GL_INT,
                IntArray(mapper.from.size()) { mapper.forwards(it) })
            glTexParameteri(GL_TEXTURE_1D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
            glTexParameteri(GL_TEXTURE_1D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
            glTexParameteri(GL_TEXTURE_1D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
            GlUtil.checkGLError("mapper buffer")
            glBindTexture(GL_TEXTURE_1D, 0)
        }

        private fun setGradient(gradient: SGradient) {
            val textureData = FloatArray(GRADIENT_TEXTURE_SIZE * 4)
            for (i in 0 until GRADIENT_TEXTURE_SIZE) {
                val pos = i.toFloat() / (GRADIENT_TEXTURE_SIZE - 1)
                val color = gradient[pos]
                textureData[i * 4 + 0] = color.red.toFloat()
                textureData[i * 4 + 1] = color.green.toFloat()
                textureData[i * 4 + 2] = color.blue.toFloat()
                textureData[i * 4 + 3] = color.opacity.toFloat()
            }
            glBindTexture(GL_TEXTURE_1D, gradientBuffer)
            glTexSubImage1D(
                GL_TEXTURE_1D,
                0,
                0,
                GRADIENT_TEXTURE_SIZE,
                GL_RGBA,
                GL_FLOAT,
                textureData,
            )
        }

        fun scheduleSendRow(row: FloatArray) {
            if (sendBuffer.size < 20)
                sendBuffer.add(row)
        }

        private fun sendScheduled() {
            var elem: FloatArray?
            while (sendBuffer.poll().also { elem = it } != null) {
                sendSampleRow(elem!!)
            }
        }

        fun changeScheduled(fftRate: Int, bufferDuration: Duration, fftArraySize: Int, gradient: SGradient) {
            if (resizeBuffer) sampleBuffer((fftRate * bufferDuration.toSeconds()).toInt(), fftArraySize)
            if (changeGradient) setGradient(gradient)

            resizeBuffer = false
            changeGradient = false

            sendScheduled()
        }

        private fun sendSampleRow(row: FloatArray) {
            if (row.size != sampleBufferHeight) return
            sampleBufferPosition = (sampleBufferPosition + 1) % sampleBufferWidth
            glBindTexture(GL_TEXTURE_2D, sampleBuffer)
            glTexSubImage2D(
                GL_TEXTURE_2D,
                0,
                0,
                sampleBufferPosition,
                sampleBufferHeight,
                1,
                GL_RED,
                GL_FLOAT,
                FloatArray(row.size) { scalar.scale(row[it]) },
            )
            GlUtil.checkGLError("send row")
            glBindTexture(GL_TEXTURE_2D, 0)
        }

        fun dispose() {
            disposed = true
            glDeleteTextures(sampleBuffer)
            glDeleteTextures(entryMapperBuffer)
            glDeleteTextures(gradientBuffer)
            glDeleteProgram(program)
            sendBuffer.clear()
        }
    }

    override fun setupGl(canvas: GLCanvas, drawLock: ReentrantLock) {
        canvas.addOnInitEvent {
            try {
                glData = GlData(scalar)
                updateBuffer()
            } catch (e: Throwable) {
                e.printStackTrace()
                Platform.exit()
            }
        }

        canvas.addOnRenderEvent {
            if (source == null || glData.disposed) return@addOnRenderEvent

            val displayEntrySize = if (orientation.value == Orientation.VERTICAL) it.width else it.height
            var effectiveStripeLength = source!!.trimResultTo(fftArraySize.value * 2, effectiveHighPass.get())
            val frequencyBinSkip = source!!.bufferBeginningSkipFor(effectiveLowPass.get(), fftArraySize.value * 2)
            effectiveStripeLength -= frequencyBinSkip

            val fromRange = 0 until displayEntrySize
            val toRange = frequencyBinSkip until frequencyBinSkip + effectiveStripeLength
            val mapper = if (effectiveLogarithmic.value) LogRangeMapper(fromRange, toRange) else FreeRangeMapper(
                fromRange,
                toRange
            )
            if (mapper.from != glData.mapper.from || mapper.to != glData.mapper.to || mapper.javaClass != glData.mapper.javaClass) {
                // the javaClass check checks for the LogRange vs FreeRange mapper type without storing additional booleans
                glData.entryMapperBuffer(mapper)
            }

            glData.changeScheduled(fftRate.value, bufferDuration.get(), fftArraySize.value, gradient.value)
            glUseProgram(glData.program)

            glActiveTexture(GL_TEXTURE0)
            glBindTexture(GL_TEXTURE_2D, glData.sampleBuffer)
            glUniform1i(glGetUniformLocation(glData.program, "tex"), 0)

            glActiveTexture(GL_TEXTURE1)
            glBindTexture(GL_TEXTURE_1D, glData.entryMapperBuffer)
            glUniform1i(glGetUniformLocation(glData.program, "coordMapTex"), 1)

            glActiveTexture(GL_TEXTURE2)
            glBindTexture(GL_TEXTURE_1D, glData.gradientBuffer)
            glUniform1i(glGetUniformLocation(glData.program, "gradientTex"), 2)

            glUniform2i(glGetUniformLocation(glData.program, "size"), it.width, it.height)
            glUniform1i(glGetUniformLocation(glData.program, "bufferSize"), glData.sampleBufferWidth)
            glUniform1i(glGetUniformLocation(glData.program, "headOffset"), glData.sampleBufferPosition)
            glUniform1i(
                glGetUniformLocation(glData.program, "isVertical"),
                if (orientation.value == Orientation.VERTICAL) 1 else 0
            )

            glBegin(GL_QUADS)
            glVertex2d(-1.0, -1.0)
            glVertex2d(-1.0, 1.0)
            glVertex2d(1.0, 1.0)
            glVertex2d(1.0, -1.0)
            glEnd()
        }

        canvas.addOnReshapeEvent {}

        canvas.addOnDisposeEvent {
            glData.dispose()
        }
    }

    private fun updateBuffer() {
        if (::glData.isInitialized) {
            glData.scheduleSettingChange(resizeBuffer = true)
        }
    }

    override fun draw(gc: GraphicsContext, deltaT: Double, now: Long, width: Double, height: Double) {}

    override fun registerListeners(acs: AudioCaptureService) {
        acs.registerFFTObserver(0, this::handleFFT)
    }
}