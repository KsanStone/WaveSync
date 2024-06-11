package me.ksanstone.wavesync.wavesync.gui.component.visualizer

import javafx.beans.property.IntegerProperty
import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.fxml.FXMLLoader
import javafx.geometry.Orientation
import javafx.scene.canvas.GraphicsContext
import javafx.scene.image.WritableImage
import javafx.scene.layout.HBox
import javafx.util.Duration
import me.ksanstone.wavesync.wavesync.WaveSyncBootApplication
import me.ksanstone.wavesync.wavesync.gui.controller.visualizer.spectrogram.SpectrogramSettingsController
import me.ksanstone.wavesync.wavesync.gui.utility.AutoCanvas
import me.ksanstone.wavesync.wavesync.service.AudioCaptureService
import me.ksanstone.wavesync.wavesync.service.LocalizationService
import me.ksanstone.wavesync.wavesync.service.PreferenceService
import me.ksanstone.wavesync.wavesync.utility.RollingBuffer

class SpectrogramVisualizer : AutoCanvas() {

    val bufferDuration: ObjectProperty<Duration> = SimpleObjectProperty(Duration.seconds(1.0))
    val orientation = SimpleObjectProperty(Orientation.VERTICAL)

    private var buffer: RollingBuffer<FloatArray> = RollingBuffer(100, FloatArray(0))
    private var sourceRate = SimpleIntegerProperty(48000)
    private var effectiveBufferSampleRate = 48000
    private val fftSize: IntegerProperty = WaveSyncBootApplication.applicationContext.getBean(AudioCaptureService::class.java).fftSize

    private var imageTop = WritableImage(1,1)
    private var imageBottom = WritableImage(1,1)
    private var imageOffset = 0

    private var canvasWidth = 0
    private var canvasHeight = 0

    init {
        changeBufferWidth()
        fftSize.addListener { _, _, newValue ->
            changeBufferWidth()
        }

        sourceRate.addListener { _, _, v ->
            effectiveBufferSampleRate = 48000.coerceAtMost(v.toInt())
            changeBufferDuration(bufferDuration.get(), effectiveBufferSampleRate)
            resetBuffer()
        }

        bufferDuration.addListener { _, _, _ ->
            changeBufferDuration(bufferDuration.get(), effectiveBufferSampleRate)
            resetBuffer()
            sizeTimeAxis()
        }
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
    }

    private fun changeBufferDuration(time: Duration, rate: Int) {
        val newSize = rate * time.toSeconds()
        this.buffer = RollingBuffer(newSize.toInt(), FloatArray(fftSize.value))
    }

    private fun changeBufferWidth() {
        changeBufferDuration(bufferDuration.get(), effectiveBufferSampleRate)
    }

    private fun resetBuffer() {

    }

    private fun createImageBuffers() {
        imageTop = WritableImage(canvasWidth.coerceAtLeast(1), canvasHeight.coerceAtLeast(1))
        imageBottom = WritableImage(canvasWidth.coerceAtLeast(1), canvasHeight.coerceAtLeast(1))
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

    override fun draw(gc: GraphicsContext, deltaT: Double, now: Long, width: Double, height: Double) {
        if (width.toInt() != canvasWidth || height.toInt() != canvasHeight) {
            canvasWidth = width.toInt()
            canvasHeight = height.toInt()
            createImageBuffers()
        }
        gc.isImageSmoothing = false

        if (orientation.value == Orientation.HORIZONTAL) {
            gc.drawImage(imageTop, -imageOffset.toDouble(), 0.0)
            gc.drawImage(imageBottom, width - imageOffset.toDouble(), 0.0)
        } else {
            gc.drawImage(imageTop, 0.0, -imageOffset.toDouble())
            gc.drawImage(imageBottom, 0.0, height - imageOffset.toDouble())
        }
    }
}