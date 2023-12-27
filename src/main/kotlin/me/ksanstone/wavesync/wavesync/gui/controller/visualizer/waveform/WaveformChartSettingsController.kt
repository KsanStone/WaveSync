package me.ksanstone.wavesync.wavesync.gui.controller.visualizer.waveform

import atlantafx.base.controls.ToggleSwitch
import javafx.fxml.FXML
import javafx.scene.control.Spinner
import javafx.scene.control.SpinnerValueFactory.DoubleSpinnerValueFactory
import javafx.scene.control.ToggleButton
import me.ksanstone.wavesync.wavesync.gui.component.visualizer.WaveformVisualizer
import me.ksanstone.wavesync.wavesync.gui.controller.GraphStyleController

class WaveformChartSettingsController {

    @FXML
    lateinit var pointCloud: ToggleButton

    @FXML
    lateinit var waveformLine: ToggleButton

    @FXML
    lateinit var alignFrequencyToggleSwitch: ToggleSwitch

    @FXML
    lateinit var alignFrequency: Spinner<Double>

    @FXML
    lateinit var graphStyleController: GraphStyleController

    @FXML
    lateinit var waveformRangeMaxSpinner: Spinner<Double>

    @FXML
    lateinit var waveformRangeMinSpinner: Spinner<Double>

    @FXML
    lateinit var linkToggleButton: ToggleButton

    @FXML
    lateinit var autoAlignToggleSwitch: ToggleSwitch

    @FXML
    fun renderCloud() {
        visualizer.renderMode.set(WaveformVisualizer.RenderMode.POINT_CLOUD)
        waveformLine.selectedProperty().set(false)
        pointCloud.selectedProperty().set(true)
    }

    @FXML
    fun renderLine() {
        visualizer.renderMode.set(WaveformVisualizer.RenderMode.LINE)
        pointCloud.selectedProperty().set(false)
        waveformLine.selectedProperty().set(true)
    }

    lateinit var visualizer: WaveformVisualizer

    fun initialize(visualizer: WaveformVisualizer) {
        this.visualizer = visualizer

        waveformLine.selectedProperty().set(visualizer.renderMode.get() == WaveformVisualizer.RenderMode.LINE)
        pointCloud.selectedProperty().set(visualizer.renderMode.get() == WaveformVisualizer.RenderMode.POINT_CLOUD)


        alignFrequencyToggleSwitch.isSelected = visualizer.enableAlign.get()
        visualizer.enableAlign.bind(alignFrequencyToggleSwitch.selectedProperty())

        autoAlignToggleSwitch.isSelected = visualizer.enableAlign.get()
        visualizer.autoAlign.bind(autoAlignToggleSwitch.selectedProperty())

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

        alignFrequency.valueFactory = DoubleSpinnerValueFactory(
            20.0,
            20000.0,
            visualizer.targetAlignFrequency.get(),
            1.0
        )

        linkToggleButton.isSelected = visualizer.rangeLink.get()
        linkToggleButton.selectedProperty().addListener { _ -> linkAdjust() }
        linkAdjust()

        graphStyleController.yAxisToggle.isSelected = visualizer.canvasContainer.yAxisShown.get()
        graphStyleController.gridToggle.isSelected = visualizer.canvasContainer.horizontalLinesVisible.get()
        graphStyleController.xAxisToggle.isDisable = true

        visualizer.canvasContainer.yAxisShown.bind(graphStyleController.yAxisToggle.selectedProperty())
        visualizer.canvasContainer.horizontalLinesVisible.bind(graphStyleController.gridToggle.selectedProperty())
        visualizer.canvasContainer.verticalLinesVisible.bind(graphStyleController.gridToggle.selectedProperty())
        visualizer.rangeMax.bind(waveformRangeMaxSpinner.valueFactory.valueProperty().map { it.toFloat() })
        visualizer.rangeMin.bind(waveformRangeMinSpinner.valueFactory.valueProperty().map { it.toFloat() })
        visualizer.targetAlignFrequency.bind(alignFrequency.valueProperty())
        visualizer.rangeLink.bind(linkToggleButton.selectedProperty())

        alignFrequency.disableProperty().bind(autoAlignToggleSwitch.selectedProperty().or(alignFrequencyToggleSwitch.selectedProperty().not()))
        autoAlignToggleSwitch.disableProperty().bind(alignFrequencyToggleSwitch.selectedProperty().not())
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