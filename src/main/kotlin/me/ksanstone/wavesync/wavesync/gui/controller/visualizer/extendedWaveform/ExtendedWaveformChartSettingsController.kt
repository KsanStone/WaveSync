package me.ksanstone.wavesync.wavesync.gui.controller.visualizer.extendedWaveform

import javafx.fxml.FXML
import javafx.scene.control.Spinner
import javafx.scene.control.SpinnerValueFactory.DoubleSpinnerValueFactory
import javafx.util.Duration
import me.ksanstone.wavesync.wavesync.gui.component.visualizer.ExtendedWaveformVisualizer
import me.ksanstone.wavesync.wavesync.gui.controller.GraphStyleController

class ExtendedWaveformChartSettingsController {

    @FXML
    lateinit var bufferLengthSpinner: Spinner<Double>

    @FXML
    lateinit var graphStyleController: GraphStyleController

    fun initialize(visualizer: ExtendedWaveformVisualizer) {
        bufferLengthSpinner.valueFactory = DoubleSpinnerValueFactory(
            1.0,
            60.0,
            visualizer.bufferDuration.get().toSeconds(),
            1.0
        )

        visualizer.registerGraphSettings(graphStyleController)
        visualizer.bufferDuration.bind(bufferLengthSpinner.valueFactory.valueProperty().map { Duration.seconds(it) })
    }

}