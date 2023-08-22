package me.ksanstone.wavesync.wavesync.gui.controller

import javafx.fxml.FXML
import javafx.fxml.Initializable
import javafx.scene.control.Label
import javafx.scene.control.Slider
import javafx.scene.control.Spinner
import javafx.scene.control.SpinnerValueFactory.IntegerSpinnerValueFactory
import me.ksanstone.wavesync.wavesync.WaveSyncBootApplication
import me.ksanstone.wavesync.wavesync.service.AudioCaptureService
import me.ksanstone.wavesync.wavesync.service.SupportedCaptureSource
import me.ksanstone.wavesync.wavesync.service.closestPowerOf2
import java.net.URL
import java.util.*

class VisualizerOptionsController : Initializable {

    @FXML
    lateinit var dropRateSlider: Slider

    @FXML
    lateinit var scalingSlider: Slider

    @FXML
    lateinit var minFreqSpinner: Spinner<Int>

    @FXML
    lateinit var maxFreqSpinner: Spinner<Int>

    @FXML
    lateinit var freqInfoLabel: Label

    private lateinit var audioCaptureService: AudioCaptureService

    override fun initialize(location: URL?, resources: ResourceBundle?) {
        audioCaptureService = WaveSyncBootApplication.applicationContext.getBean(AudioCaptureService::class.java)

        MainController.instance.visualizer.scaling.bind(scalingSlider.valueProperty())
        MainController.instance.visualizer.smoothing.bind(dropRateSlider.valueProperty())

        minFreqSpinner.valueFactory = IntegerSpinnerValueFactory(10, 200, audioCaptureService.lowpass.get())
        maxFreqSpinner.valueFactory = IntegerSpinnerValueFactory(3000, 96000, MainController.instance.visualizer.cutoff.get())

        updateInfo()
        audioCaptureService.source.addListener { _ ->
            updateInfo()
        }
    }

    private fun updateInfo() {
        if (audioCaptureService.source.get() == null) {
            freqInfoLabel.text = "No device selected"
        } else {
            val rate = audioCaptureService.source.get().format.mix.rate
            val window = SupportedCaptureSource.getMinimumSamples(minFreqSpinner.value, rate).closestPowerOf2()
            val buffer = SupportedCaptureSource.trimResultBufferTo(window * 2, rate, maxFreqSpinner.value)
            freqInfoLabel.text = "Window: $window • Visible buffer: $buffer"
        }
    }

    fun applyFreqSettings() {
        MainController.instance.visualizer.cutoff.set(maxFreqSpinner.value)
        audioCaptureService.lowpass.set(minFreqSpinner.value)
        audioCaptureService.restartCapture()
        updateInfo()
    }

}