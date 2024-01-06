package me.ksanstone.wavesync.wavesync.gui.controller.visualizer.bar

import atlantafx.base.controls.ToggleSwitch
import javafx.fxml.FXML
import javafx.fxml.Initializable
import javafx.scene.control.*
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults
import me.ksanstone.wavesync.wavesync.WaveSyncBootApplication
import me.ksanstone.wavesync.wavesync.gui.component.visualizer.BarVisualizer
import me.ksanstone.wavesync.wavesync.gui.controller.GraphStyleController
import me.ksanstone.wavesync.wavesync.service.AudioCaptureService
import me.ksanstone.wavesync.wavesync.service.LocalizationService
import me.ksanstone.wavesync.wavesync.service.fftScaling.FFTScalarType
import java.net.URL
import java.util.*

class BarChartSettingsController : Initializable {

    @FXML
    lateinit var bar: ToggleButton

    @FXML
    lateinit var line: ToggleButton

    @FXML
    lateinit var linearScalingSlider: Slider

    @FXML
    lateinit var peakToggleSwitch: ToggleSwitch

    @FXML
    lateinit var graphStyleController: GraphStyleController

    @FXML
    lateinit var dbMinSpinner: Spinner<Double>

    @FXML
    lateinit var dbMaxSpinner: Spinner<Double>

    @FXML
    lateinit var scalarTypeTabPane: TabPane

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

    @FXML
    fun renderBar() {
        visualizer.renderMode.set(BarVisualizer.RenderMode.BAR)
        line.selectedProperty().set(false)
        bar.selectedProperty().set(true)
    }

    @FXML
    fun renderLine() {
        visualizer.renderMode.set(BarVisualizer.RenderMode.LINE)
        bar.selectedProperty().set(false)
        line.selectedProperty().set(true)
    }

    lateinit var visualizer: BarVisualizer

    fun initialize(visualizer: BarVisualizer) {
        this.visualizer = visualizer
        val tw = visualizer.targetBarWidth.get()
        val maxFreq = audioCaptureService.source.get()?.getMaxFrequency() ?: 96000

        line.selectedProperty().set(visualizer.renderMode.get() == BarVisualizer.RenderMode.LINE)
        bar.selectedProperty().set(visualizer.renderMode.get() == BarVisualizer.RenderMode.BAR)

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
        dbMinSpinner.valueFactory = SpinnerValueFactory.DoubleSpinnerValueFactory(
            -150.0,
            -40.0,
            visualizer.dbMin.get().toDouble()
        )
        dbMaxSpinner.valueFactory = SpinnerValueFactory.DoubleSpinnerValueFactory(
            -30.0,
            20.0,
            visualizer.dbMax.get().toDouble()
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
        gapSlider.disableProperty().bind(line.selectedProperty())
        scalingSlider.value = visualizer.exaggeratedScalar.get().toDouble()
        linearScalingSlider.value = visualizer.linearScalar.get().toDouble()
        dropRateSlider.value = visualizer.smoothing.get().toDouble()
        barWidthSlider.value = visualizer.targetBarWidth.get().toDouble()
        peakToggleSwitch.isSelected = visualizer.peakLineVisible.get()
        gapSlider.value = visualizer.gap.get().toDouble()
        gapSlider.valueProperty().addListener { _, _, v -> if (barWidthSlider.value <= v.toDouble()) barWidthSlider.value = v.toDouble() + 1 }
        scalarTypeTabPane.selectionModel.select(
            when (visualizer.scalarType.value) {
                FFTScalarType.LINEAR -> 0
                FFTScalarType.EXAGGERATED -> 1
                FFTScalarType.DECIBEL -> 2
                else -> 0
            }
        )
        graphStyleController.yAxisToggle.isSelected = visualizer.canvasContainer.yAxisShown.get()
        graphStyleController.xAxisToggle.isSelected = visualizer.canvasContainer.xAxisShown.get()
        graphStyleController.gridToggle.isSelected = visualizer.canvasContainer.horizontalLinesVisible.get()

        visualizer.canvasContainer.yAxisShown.bind(graphStyleController.yAxisToggle.selectedProperty())
        visualizer.canvasContainer.xAxisShown.bind(graphStyleController.xAxisToggle.selectedProperty())
        visualizer.canvasContainer.horizontalLinesVisible.bind(graphStyleController.gridToggle.selectedProperty())
        visualizer.canvasContainer.verticalLinesVisible.bind(graphStyleController.gridToggle.selectedProperty())
        visualizer.exaggeratedScalar.bind(scalingSlider.valueProperty())
        visualizer.linearScalar.bind(linearScalingSlider.valueProperty())
        visualizer.smoothing.bind(dropRateSlider.valueProperty())
        visualizer.targetBarWidth.bind(barWidthSlider.valueProperty())
        visualizer.cutoff.bind(maxFreqSpinner.valueProperty())
        visualizer.lowPass.bind(minFreqSpinner.valueProperty())
        visualizer.gap.bind(gapSlider.valueProperty())
        visualizer.peakLineVisible.bind(peakToggleSwitch.selectedProperty())
        visualizer.dbMin.bind(dbMinSpinner.valueProperty().map { it.toFloat() })
        visualizer.dbMax.bind(dbMaxSpinner.valueProperty().map { it.toFloat() })
        visualizer.scalarType.bind(scalarTypeTabPane.selectionModel.selectedIndexProperty().map {
            when (it) {
                0 -> FFTScalarType.LINEAR
                1 -> FFTScalarType.EXAGGERATED
                2 -> FFTScalarType.DECIBEL
                else -> FFTScalarType.DECIBEL
            }
        })
    }

    @FXML
    override fun initialize(location: URL?, resources: ResourceBundle?) {
        audioCaptureService = WaveSyncBootApplication.applicationContext.getBean(AudioCaptureService::class.java)
        localizationService = WaveSyncBootApplication.applicationContext.getBean(LocalizationService::class.java)
    }
}