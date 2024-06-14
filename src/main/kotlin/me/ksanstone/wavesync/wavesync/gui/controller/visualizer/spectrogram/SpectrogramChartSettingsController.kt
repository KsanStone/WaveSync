package me.ksanstone.wavesync.wavesync.gui.controller.visualizer.spectrogram

import atlantafx.base.controls.ToggleSwitch
import javafx.beans.property.SimpleBooleanProperty
import javafx.fxml.FXML
import javafx.geometry.Orientation
import javafx.scene.control.Spinner
import javafx.scene.control.SpinnerValueFactory
import javafx.scene.control.SpinnerValueFactory.DoubleSpinnerValueFactory
import javafx.scene.control.ToggleButton
import javafx.util.Duration
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults
import me.ksanstone.wavesync.wavesync.WaveSyncBootApplication
import me.ksanstone.wavesync.wavesync.gui.component.visualizer.BarVisualizer
import me.ksanstone.wavesync.wavesync.gui.component.visualizer.SpectrogramVisualizer
import me.ksanstone.wavesync.wavesync.gui.controller.GraphStyleController
import me.ksanstone.wavesync.wavesync.service.AudioCaptureService
import me.ksanstone.wavesync.wavesync.service.LayoutStorageService
import me.ksanstone.wavesync.wavesync.service.LayoutStorageService.Companion.MAIN_BAR_VISUALIZER_ID
import me.ksanstone.wavesync.wavesync.service.PreferenceService

class SpectrogramChartSettingsController {

    @FXML
    lateinit var bindToggle: ToggleSwitch

    @FXML
    lateinit var bufferLengthSpinner: Spinner<Double>

    @FXML
    lateinit var renderVerticalButton: ToggleButton

    @FXML
    lateinit var renderHorizontalButton: ToggleButton

    @FXML
    lateinit var minFreqSpinner: Spinner<Int>

    @FXML
    lateinit var maxFreqSpinner: Spinner<Int>

    @FXML
    lateinit var graphStyleController: GraphStyleController

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

    private lateinit var visualizer: SpectrogramVisualizer
    private val audioCaptureService = WaveSyncBootApplication.applicationContext.getBean(AudioCaptureService::class.java)
    private val layoutStorageService = WaveSyncBootApplication.applicationContext.getBean(LayoutStorageService::class.java)
    private val preferenceService = WaveSyncBootApplication.applicationContext.getBean(PreferenceService::class.java)
    private val propsDelegated = SimpleBooleanProperty(false)


    fun initialize(visualizer: SpectrogramVisualizer) {
        this.visualizer = visualizer

        preferenceService.registerProperty(propsDelegated, "propsDelegated", this.javaClass)

        renderVerticalButton.isSelected = visualizer.orientation.value == Orientation.VERTICAL
        renderHorizontalButton.isSelected = visualizer.orientation.value == Orientation.HORIZONTAL

        bufferLengthSpinner.valueFactory = DoubleSpinnerValueFactory(
            10.0,
            60.0,
            visualizer.bufferDuration.get().toSeconds(),
            1.0
        )

        visualizer.registerGraphSettings(graphStyleController)
        visualizer.bufferDuration.bind(bufferLengthSpinner.valueFactory.valueProperty().map { Duration.seconds(it) })

        bindToggle.isSelected = propsDelegated.value
        propsDelegated.bind(bindToggle.selectedProperty())
        propsDelegated.addListener { _ -> handleBindings() }
        handleBindings()
    }

    private fun handleBindings() {
        val maxFreq = audioCaptureService.source.get()?.getMaxFrequency() ?: 96000

        if (!propsDelegated.value) {
            visualizer.setBindEffective(true)
            maxFreqSpinner.valueFactory = SpinnerValueFactory.IntegerSpinnerValueFactory(
                ApplicationSettingDefaults.DEFAULT_MIN_UI_VISUALIZER_WINDOW,
                maxFreq,
                visualizer.highPass.get()
            )
            visualizer.highPass.bind(maxFreqSpinner.valueProperty())

            minFreqSpinner.valueFactory = SpinnerValueFactory.IntegerSpinnerValueFactory(
                0,
                maxFreq - ApplicationSettingDefaults.DEFAULT_MIN_UI_VISUALIZER_WINDOW,
                visualizer.lowPass.get()
            )
            visualizer.lowPass.bind(minFreqSpinner.valueProperty())
        } else {
            visualizer.setBindEffective(false)
            val bars = layoutStorageService.nodeFactory.createNode(MAIN_BAR_VISUALIZER_ID)?.let {
                it as BarVisualizer
            } ?: return

            visualizer.effectiveLowPass.bind(bars.lowPass)
            visualizer.effectiveHighPass.bind(bars.highPass)
        }
    }

    fun renderHorizontal() {
        renderVerticalButton.isSelected = false
        renderHorizontalButton.isSelected = true
        this.visualizer.orientation.value = Orientation.HORIZONTAL
    }

    fun renderVertical() {
        renderVerticalButton.isSelected = true
        renderHorizontalButton.isSelected = false
        this.visualizer.orientation.value = Orientation.VERTICAL
    }


}