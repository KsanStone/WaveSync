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

        graphStyleController.yAxisToggle.isSelected = visualizer.canvasContainer.yAxisShown.get()
        graphStyleController.xAxisToggle.isSelected = visualizer.canvasContainer.xAxisShown.get()
        graphStyleController.gridToggle.isSelected = visualizer.canvasContainer.horizontalLinesVisible.get()

        visualizer.canvasContainer.yAxisShown.bind(graphStyleController.yAxisToggle.selectedProperty())
        visualizer.canvasContainer.xAxisShown.bind(graphStyleController.xAxisToggle.selectedProperty())
        visualizer.canvasContainer.horizontalLinesVisible.bind(graphStyleController.gridToggle.selectedProperty())
        visualizer.canvasContainer.verticalLinesVisible.bind(graphStyleController.gridToggle.selectedProperty())
        visualizer.bufferDuration.bind(bufferLengthSpinner.valueFactory.valueProperty().map { Duration.seconds(it)})
    }

}