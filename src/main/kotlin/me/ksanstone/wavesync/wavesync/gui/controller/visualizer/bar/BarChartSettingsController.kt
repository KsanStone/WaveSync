package me.ksanstone.wavesync.wavesync.gui.controller.visualizer.bar

import javafx.fxml.FXML
import javafx.fxml.Initializable
import javafx.scene.control.Slider
import javafx.scene.control.Spinner
import javafx.scene.control.SpinnerValueFactory
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults
import me.ksanstone.wavesync.wavesync.WaveSyncBootApplication
import me.ksanstone.wavesync.wavesync.gui.component.visualizer.BarVisualizer
import me.ksanstone.wavesync.wavesync.service.AudioCaptureService
import me.ksanstone.wavesync.wavesync.service.LocalizationService
import java.net.URL
import java.util.*

class BarChartSettingsController : Initializable {

    @FXML
    lateinit var gapSlider: Slider

    @FXML
    lateinit var minFreqSpinner: Spinner<Int>

    @FXML
    lateinit var maxFreqSpinner: Spinner<Int>

    @FXML
    lateinit var barWidthSlider: Slider

    @FXML
    lateinit var dropRateSlider: Slider

    @FXML
    lateinit var scalingSlider: Slider

    private lateinit var audioCaptureService: AudioCaptureService
    private lateinit var localizationService: LocalizationService

    @FXML
    fun setMinFreqOnVisualizer() {
        minFreqSpinner.valueFactory.value = 0
    }

    @FXML
    fun setMaxFreqOnVisualizer() {
        val maxFreq = audioCaptureService.source.get()?.getMaxFrequency() ?: 96000
        maxFreqSpinner.valueFactory.value = maxFreq
    }

    @FXML
    fun set20khzFreqOnVisualizer() {
        maxFreqSpinner.valueFactory.value = 20_000
    }

    fun initialize(visualizer: BarVisualizer) {

        val tw = visualizer.targetBarWidth.get()
        val maxFreq = audioCaptureService.source.get()?.getMaxFrequency() ?: 96000

        maxFreqSpinner.valueFactory = SpinnerValueFactory.IntegerSpinnerValueFactory(
            ApplicationSettingDefaults.MIN_UI_VISUALIZER_WINDOW,
            maxFreq,
            visualizer.cutoff.get()
        )
        minFreqSpinner.valueFactory = SpinnerValueFactory.IntegerSpinnerValueFactory(
            0,
            maxFreq - ApplicationSettingDefaults.MIN_UI_VISUALIZER_WINDOW,
            visualizer.lowPass.get()
        )

        maxFreqSpinner.valueProperty().addListener { _ ->
            if (minFreqSpinner.valueFactory.value > maxFreqSpinner.valueFactory.value - ApplicationSettingDefaults.MIN_UI_VISUALIZER_WINDOW)
                minFreqSpinner.valueFactory.value =
                    maxFreqSpinner.valueFactory.value - ApplicationSettingDefaults.MIN_UI_VISUALIZER_WINDOW
        }
        minFreqSpinner.valueProperty().addListener { _ ->
            if (minFreqSpinner.valueFactory.value + ApplicationSettingDefaults.MIN_UI_VISUALIZER_WINDOW > maxFreqSpinner.value)
                maxFreqSpinner.valueFactory.value =
                    minFreqSpinner.valueFactory.value + ApplicationSettingDefaults.MIN_UI_VISUALIZER_WINDOW
        }

        barWidthSlider.value = tw.toDouble()

        scalingSlider.value = visualizer.scaling.get().toDouble()
        dropRateSlider.value = visualizer.smoothing.get().toDouble()
        barWidthSlider.value = visualizer.targetBarWidth.get().toDouble()
        gapSlider.value = visualizer.gap.get().toDouble()

        visualizer.scaling.bind(scalingSlider.valueProperty())
        visualizer.smoothing.bind(dropRateSlider.valueProperty())
        visualizer.targetBarWidth.bind(barWidthSlider.valueProperty())
        visualizer.cutoff.bind(maxFreqSpinner.valueProperty())
        visualizer.lowPass.bind(minFreqSpinner.valueProperty())
        visualizer.gap.bind(gapSlider.valueProperty())
    }

    @FXML
    override fun initialize(location: URL?, resources: ResourceBundle?) {
        audioCaptureService = WaveSyncBootApplication.applicationContext.getBean(AudioCaptureService::class.java)
        localizationService = WaveSyncBootApplication.applicationContext.getBean(LocalizationService::class.java)
    }
}