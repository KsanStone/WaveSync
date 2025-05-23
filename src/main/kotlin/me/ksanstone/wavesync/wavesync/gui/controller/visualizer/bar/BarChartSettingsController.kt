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
    lateinit var multiplicative: ToggleButton

    @FXML
    lateinit var falloff: ToggleButton

    @FXML
    lateinit var peakPointToggleSwitch: ToggleSwitch

    @FXML
    lateinit var smoothToggleSwitch: ToggleSwitch

    @FXML
    lateinit var fillToggleSwitch: ToggleSwitch

    @FXML
    lateinit var axisLogarithmicToggle: ToggleButton

    @FXML
    lateinit var axisLinearToggle: ToggleButton

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
        falloff.selectedProperty().set(visualizer.smootherType.get() == BarVisualizer.SmootherType.FALLOFF)
        multiplicative.selectedProperty()
            .set(visualizer.smootherType.get() == BarVisualizer.SmootherType.MULTIPLICATIVE)
        axisLogarithmicToggle.selectedProperty().set(visualizer.logarithmic.get())
        peakPointToggleSwitch.selectedProperty().set(visualizer.showPeak.value)
        axisLinearToggle.selectedProperty().set(!visualizer.logarithmic.get())
        fillToggleSwitch.selectedProperty().set(visualizer.fillCurve.get())
        smoothToggleSwitch.selectedProperty().set(visualizer.smoothCurve.get())
        fillToggleSwitch.disableProperty().bind(visualizer.renderMode.map { it == BarVisualizer.RenderMode.BAR })
        smoothToggleSwitch.disableProperty().bind(visualizer.renderMode.map { it == BarVisualizer.RenderMode.BAR })
        visualizer.fillCurve.bind(fillToggleSwitch.selectedProperty())
        visualizer.smoothCurve.bind(smoothToggleSwitch.selectedProperty())
        visualizer.showPeak.bind(peakPointToggleSwitch.selectedProperty())

        maxFreqSpinner.valueFactory = SpinnerValueFactory.IntegerSpinnerValueFactory(
            ApplicationSettingDefaults.DEFAULT_MIN_UI_VISUALIZER_WINDOW,
            maxFreq,
            visualizer.highPass.get()
        )
        minFreqSpinner.valueFactory = SpinnerValueFactory.IntegerSpinnerValueFactory(
            0,
            maxFreq - ApplicationSettingDefaults.DEFAULT_MIN_UI_VISUALIZER_WINDOW,
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
            if (minFreqSpinner.valueFactory.value > maxFreqSpinner.valueFactory.value - ApplicationSettingDefaults.DEFAULT_MIN_UI_VISUALIZER_WINDOW)
                minFreqSpinner.valueFactory.value =
                    maxFreqSpinner.valueFactory.value - ApplicationSettingDefaults.DEFAULT_MIN_UI_VISUALIZER_WINDOW
        }
        minFreqSpinner.valueProperty().addListener { _ ->
            if (minFreqSpinner.valueFactory.value + ApplicationSettingDefaults.DEFAULT_MIN_UI_VISUALIZER_WINDOW > maxFreqSpinner.value)
                maxFreqSpinner.valueFactory.value =
                    minFreqSpinner.valueFactory.value + ApplicationSettingDefaults.DEFAULT_MIN_UI_VISUALIZER_WINDOW
        }

        barWidthSlider.value = tw.toDouble()
        gapSlider.disableProperty().bind(line.selectedProperty().or(visualizer.logarithmic))
        scalingSlider.value = visualizer.exaggeratedScalar.get().toDouble()
        linearScalingSlider.value = visualizer.linearScalar.get().toDouble()
        dropRateSlider.value = visualizer.smoothing.get().toDouble()
        barWidthSlider.value = visualizer.targetBarWidth.get().toDouble()
        peakToggleSwitch.isSelected = visualizer.peakLineVisible.get()
        gapSlider.value = visualizer.gap.get().toDouble()
        gapSlider.valueProperty().addListener { _, _, v ->
            if (barWidthSlider.value <= v.toDouble()) barWidthSlider.value = v.toDouble() + 1
        }
        scalarTypeTabPane.selectionModel.select(
            when (visualizer.scalarType.value) {
                FFTScalarType.LINEAR -> 0
                FFTScalarType.EXAGGERATED -> 1
                FFTScalarType.DECIBEL -> 2
                else -> 0
            }
        )

        visualizer.registerGraphSettings(graphStyleController)
        visualizer.exaggeratedScalar.bind(scalingSlider.valueProperty())
        visualizer.linearScalar.bind(linearScalingSlider.valueProperty())
        visualizer.smoothing.bind(dropRateSlider.valueProperty())
        visualizer.targetBarWidth.bind(barWidthSlider.valueProperty())
        visualizer.highPass.bind(maxFreqSpinner.valueProperty())
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

        audioCaptureService.source.addListener { _, _, _ ->
            val maxFreq = audioCaptureService.source.get()?.getMaxFrequency() ?: 96000
            maxFreqSpinner.valueFactory = SpinnerValueFactory.IntegerSpinnerValueFactory(
                ApplicationSettingDefaults.DEFAULT_MIN_UI_VISUALIZER_WINDOW,
                maxFreq,
                visualizer.highPass.get()
            )
        }
    }

    @FXML
    override fun initialize(location: URL?, resources: ResourceBundle?) {
        audioCaptureService = WaveSyncBootApplication.applicationContext.getBean(AudioCaptureService::class.java)
        localizationService = WaveSyncBootApplication.applicationContext.getBean(LocalizationService::class.java)
    }

    fun axisLinear() {
        visualizer.logarithmic.set(false)
        axisLogarithmicToggle.isSelected = false
        axisLinearToggle.isSelected = true
    }

    fun axisLogarithmic() {
        visualizer.logarithmic.set(true)
        axisLinearToggle.isSelected = false
        axisLogarithmicToggle.isSelected = true
    }

    fun smootherFalloff() {
        visualizer.smootherType.value = BarVisualizer.SmootherType.FALLOFF
        falloff.isSelected = true
        multiplicative.isSelected = false
    }

    fun smootherMultiplicative() {
        visualizer.smootherType.value = BarVisualizer.SmootherType.MULTIPLICATIVE
        falloff.isSelected = false
        multiplicative.isSelected = true
    }
}