package me.ksanstone.wavesync.wavesync.gui.controller

import javafx.fxml.FXML
import javafx.fxml.Initializable
import javafx.scene.control.*
import javafx.scene.control.SpinnerValueFactory.IntegerSpinnerValueFactory
import javafx.util.Duration
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.MIN_UI_VISUALIZER_WINDOW
import me.ksanstone.wavesync.wavesync.WaveSyncBootApplication
import me.ksanstone.wavesync.wavesync.service.AudioCaptureService
import me.ksanstone.wavesync.wavesync.service.LocalizationService
import xt.audio.Enums.XtSystem
import java.net.URL
import java.util.*
import kotlin.math.pow
import kotlin.math.round

class VisualizerOptionsController : Initializable {

    @FXML
    lateinit var gapSlider: Slider

    @FXML
    lateinit var audioServerChoiceBox: ChoiceBox<String>

    @FXML
    lateinit var applyFreqButton: Button

    @FXML
    lateinit var minFreqSpinner: Spinner<Int>

    @FXML
    lateinit var fftInfoLabel: Label

    @FXML
    lateinit var fftSizeChoiceBox: ChoiceBox<Int>

    @FXML
    lateinit var barWidthSlider: Slider

    @FXML
    lateinit var dropRateSlider: Slider

    @FXML
    lateinit var scalingSlider: Slider

    @FXML
    lateinit var maxFreqSpinner: Spinner<Int>

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

    private fun changeAudioSystem() {
        audioCaptureService.usedAudioSystem.set(XtSystem.valueOf(audioServerChoiceBox.value))
        MainController.instance.refreshDeviceList()
    }

    override fun initialize(location: URL?, resources: ResourceBundle?) {
        audioCaptureService = WaveSyncBootApplication.applicationContext.getBean(AudioCaptureService::class.java)
        localizationService = WaveSyncBootApplication.applicationContext.getBean(LocalizationService::class.java)

        audioServerChoiceBox.items.clear()
        audioServerChoiceBox.items.addAll(audioCaptureService.audioSystems.map { it.name })
        audioServerChoiceBox.value = audioCaptureService.usedAudioSystem.get()?.name
        audioServerChoiceBox.valueProperty().addListener { _ ->
            changeAudioSystem()
        }

        fftSizeChoiceBox.items.clear()
        fftSizeChoiceBox.items.addAll(listOf(8, 9, 10, 11, 12, 13, 14, 15).map { 2.0.pow(it.toDouble()).toInt() }.toList())
        fftSizeChoiceBox.value = audioCaptureService.fftSize.get()
        fftSizeChoiceBox.valueProperty().addListener { _ -> updateFftInfoLabel() }
        audioCaptureService.source.addListener { _ -> updateFftInfoLabel() }

        val tw = MainController.instance.visualizer.targetBarWidth.get()
        val maxFreq = audioCaptureService.source.get()?.getMaxFrequency() ?: 96000

        maxFreqSpinner.valueFactory = IntegerSpinnerValueFactory(MIN_UI_VISUALIZER_WINDOW, maxFreq, MainController.instance.visualizer.cutoff.get())
        minFreqSpinner.valueFactory = IntegerSpinnerValueFactory(0, maxFreq - MIN_UI_VISUALIZER_WINDOW, MainController.instance.visualizer.lowPass.get())
        barWidthSlider.value = tw.toDouble()

        scalingSlider.value = MainController.instance.visualizer.scaling.get().toDouble()
        dropRateSlider.value = MainController.instance.visualizer.smoothing.get().toDouble()
        barWidthSlider.value = MainController.instance.visualizer.targetBarWidth.get().toDouble()
        gapSlider.value = MainController.instance.visualizer.gap.get().toDouble()

        MainController.instance.visualizer.scaling.bind(scalingSlider.valueProperty())
        MainController.instance.visualizer.smoothing.bind(dropRateSlider.valueProperty())
        MainController.instance.visualizer.targetBarWidth.bind(barWidthSlider.valueProperty())
        MainController.instance.visualizer.cutoff.bind(maxFreqSpinner.valueProperty())
        MainController.instance.visualizer.lowPass.bind(minFreqSpinner.valueProperty())
        MainController.instance.visualizer.gap.bind(gapSlider.valueProperty())

        fftSizeChoiceBox.valueProperty().addListener { _, _, v ->
            if (v != audioCaptureService.fftSize.get()) {
                if (!applyFreqButton.styleClass.contains("accent")) applyFreqButton.styleClass.add("accent")
            } else {
                applyFreqButton.styleClass.remove("accent")
            }
        }

        maxFreqSpinner.valueProperty().addListener { _ ->
            if (minFreqSpinner.value > maxFreqSpinner.value - MIN_UI_VISUALIZER_WINDOW)
                minFreqSpinner.valueFactory.value = maxFreqSpinner.value - MIN_UI_VISUALIZER_WINDOW
        }
        minFreqSpinner.valueProperty().addListener { _ ->
            if (minFreqSpinner.value + MIN_UI_VISUALIZER_WINDOW > maxFreqSpinner.value)
                maxFreqSpinner.valueFactory.value = minFreqSpinner.value + MIN_UI_VISUALIZER_WINDOW
        }

        updateFftInfoLabel()
    }

    private fun updateFftInfoLabel() {  
        if (audioCaptureService.source.get() != null) {
            val freq = audioCaptureService.source.get().getMinimumFrequency(fftSizeChoiceBox.value)
            var updateInterval = audioCaptureService.source.get().getUpdateInterval(fftSizeChoiceBox.value)
            updateInterval = Duration.millis(round(updateInterval.toMillis()*10.0)/10.0)
            fftInfoLabel.text = localizationService.format("dialog.deviceOptions.windowSizeInfo", freq, updateInterval)
        } else {
            fftInfoLabel.text = localizationService.get("dialog.deviceOptions.noDevice")
        }
    }

    fun applyFreqSettings() {
        audioCaptureService.fftSize.set(fftSizeChoiceBox.value)
        applyFreqButton.styleClass.remove("accent")
        audioCaptureService.restartCapture()
    }

}