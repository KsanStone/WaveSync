package me.ksanstone.wavesync.wavesync.gui.controller.visualizer.vector

import javafx.fxml.FXML
import javafx.scene.control.Slider
import javafx.scene.control.Spinner
import javafx.scene.control.SpinnerValueFactory.DoubleSpinnerValueFactory
import javafx.scene.control.ToggleButton
import me.ksanstone.wavesync.wavesync.gui.component.visualizer.VectorScopeVisualizer
import me.ksanstone.wavesync.wavesync.gui.controller.GraphStyleController

class VectorChartSettingsController {

    @FXML
    lateinit var decaySlider: Slider

    @FXML
    lateinit var skewed: ToggleButton

    @FXML
    lateinit var straight: ToggleButton

    @FXML
    lateinit var graphStyleController: GraphStyleController

    @FXML
    lateinit var waveformRangeMaxSpinner: Spinner<Double>

    @FXML
    lateinit var waveformRangeMinSpinner: Spinner<Double>

    @FXML
    lateinit var linkToggleButton: ToggleButton

    @FXML
    fun renderSkewed() {
        visualizer.renderMode.set(VectorScopeVisualizer.VectorOrientation.SKEWED)
        straight.selectedProperty().set(false)
        skewed.selectedProperty().set(true)
    }

    @FXML
    fun renderStraight() {
        visualizer.renderMode.set(VectorScopeVisualizer.VectorOrientation.STRAIGHT)
        skewed.selectedProperty().set(false)
        straight.selectedProperty().set(true)
    }

    lateinit var visualizer: VectorScopeVisualizer

    fun initialize(visualizer: VectorScopeVisualizer) {
        this.visualizer = visualizer

        straight.selectedProperty().set(visualizer.renderMode.get() == VectorScopeVisualizer.VectorOrientation.STRAIGHT)
        skewed.selectedProperty().set(visualizer.renderMode.get() == VectorScopeVisualizer.VectorOrientation.SKEWED)

        waveformRangeMaxSpinner.valueFactory = DoubleSpinnerValueFactory(
            0.05,
            1.0,
            visualizer.rangeX.get(),
            0.1
        )

        waveformRangeMinSpinner.valueFactory = DoubleSpinnerValueFactory(
            0.05,
            1.00,
            visualizer.rangeY.get(),
            0.1
        )

        decaySlider.value = visualizer.decay.value.toDouble()

        linkToggleButton.isSelected = visualizer.rangeLink.get()
        linkToggleButton.selectedProperty().addListener { _ -> linkAdjust() }
        linkAdjust()

        visualizer.registerGraphSettings(graphStyleController)

        visualizer.rangeX.bind(waveformRangeMaxSpinner.valueFactory.valueProperty().map { it.toFloat() })
        visualizer.rangeY.bind(waveformRangeMinSpinner.valueFactory.valueProperty().map { it.toFloat() })
        visualizer.decay.bind(decaySlider.valueProperty().map { it.toFloat() })
        visualizer.rangeLink.bind(linkToggleButton.selectedProperty())

    }

    private fun linkAdjust() {
        if (linkToggleButton.isSelected) {
            waveformRangeMaxSpinner.valueFactory.valueProperty()
                .bind(waveformRangeMinSpinner.valueFactory.valueProperty().map { it })
            waveformRangeMaxSpinner.isDisable = true
        } else {
            if (waveformRangeMaxSpinner.valueFactory.valueProperty().isBound)
                waveformRangeMaxSpinner.valueFactory.valueProperty().unbind()
            waveformRangeMaxSpinner.isDisable = false
        }
    }

}