package me.ksanstone.wavesync.wavesync.gui.controller.visualizer.waveform

import atlantafx.base.controls.ToggleSwitch
import javafx.fxml.FXML
import javafx.scene.control.Spinner
import javafx.scene.control.SpinnerValueFactory.DoubleSpinnerValueFactory
import javafx.scene.control.ToggleButton
import me.ksanstone.wavesync.wavesync.gui.component.visualizer.WaveformVisualizer

class WaveformChartSettingsController {

    @FXML
    lateinit var waveformRangeMaxSpinner: Spinner<Double>

    @FXML
    lateinit var waveformRangeMinSpinner: Spinner<Double>

    @FXML
    lateinit var linkToggleButton: ToggleButton

    @FXML
    lateinit var autoAlignToggleSwitch: ToggleSwitch

    fun initialize(visualizer: WaveformVisualizer) {
        autoAlignToggleSwitch.isSelected = visualizer.enableAutoAlign.get()
        visualizer.enableAutoAlign.bind(autoAlignToggleSwitch.selectedProperty())

        waveformRangeMaxSpinner.valueFactory = DoubleSpinnerValueFactory(
            0.01,
            10.0,
            visualizer.rangeMax.get().toDouble(),
            0.1
        )

        waveformRangeMinSpinner.valueFactory = DoubleSpinnerValueFactory(
            -10.0,
            -0.01,
            visualizer.rangeMin.get().toDouble(),
            0.1
        )

        linkToggleButton.isSelected = visualizer.rangeLink.get()
        linkToggleButton.selectedProperty().addListener { _ -> linkAdjust() }
        linkAdjust()

        visualizer.rangeMax.bind(waveformRangeMaxSpinner.valueFactory.valueProperty().map { it.toFloat() })
        visualizer.rangeMin.bind(waveformRangeMinSpinner.valueFactory.valueProperty().map { it.toFloat() })
        visualizer.rangeLink.bind(linkToggleButton.selectedProperty())
    }

    private fun linkAdjust() {
        if (linkToggleButton.isSelected) {
            waveformRangeMaxSpinner.valueFactory.valueProperty()
                .bind(waveformRangeMinSpinner.valueFactory.valueProperty().map { -it })
            waveformRangeMaxSpinner.isDisable = true
        } else {
            if (waveformRangeMaxSpinner.valueFactory.valueProperty().isBound)
                waveformRangeMaxSpinner.valueFactory.valueProperty().unbind()
            waveformRangeMaxSpinner.isDisable = false
        }
    }

}