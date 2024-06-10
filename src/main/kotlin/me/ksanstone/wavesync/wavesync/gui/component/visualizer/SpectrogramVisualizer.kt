package me.ksanstone.wavesync.wavesync.gui.component.visualizer

import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.fxml.FXMLLoader
import javafx.geometry.Orientation
import javafx.scene.canvas.GraphicsContext
import javafx.scene.layout.HBox
import javafx.util.Duration
import me.ksanstone.wavesync.wavesync.WaveSyncBootApplication
import me.ksanstone.wavesync.wavesync.gui.controller.visualizer.spectrogram.SpectrogramSettingsController
import me.ksanstone.wavesync.wavesync.gui.utility.AutoCanvas
import me.ksanstone.wavesync.wavesync.service.LocalizationService
import me.ksanstone.wavesync.wavesync.service.PreferenceService
import me.ksanstone.wavesync.wavesync.utility.RollingBuffer

class SpectrogramVisualizer : AutoCanvas() {

    val bufferDuration: ObjectProperty<Duration> = SimpleObjectProperty(Duration.seconds(1.0))
    val orientation = SimpleObjectProperty(Orientation.VERTICAL)

    private var buffer: RollingBuffer<FloatArray> = RollingBuffer(100, FloatArray(0))
    private var sourceRate = SimpleIntegerProperty(48000)
    private var effectiveBufferSampleRate = 48000

    init {
        sourceRate.addListener { _, _, v ->
            effectiveBufferSampleRate = 48000.coerceAtMost(v.toInt())
            resizeBuffer(bufferDuration.get(), effectiveBufferSampleRate)
            resetBuffer()
        }

        bufferDuration.addListener { _, _, _ ->
            resizeBuffer(bufferDuration.get(), effectiveBufferSampleRate)
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

    }

    private fun resizeBuffer(time: Duration, rate: Int) {
        val newSize = rate * time.toSeconds()
        this.buffer = RollingBuffer(newSize.toInt(), FloatArray(0))
    }

    private fun resetBuffer() {

    }

    private fun sizeTimeAxis() {

    }

    override fun draw(gc: GraphicsContext, deltaT: Double, now: Long, width: Double, height: Double) {

    }
}