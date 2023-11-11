package me.ksanstone.wavesync.wavesync.gui.component.visualizer

import javafx.application.Platform
import javafx.beans.property.*
import javafx.css.*
import javafx.fxml.FXMLLoader
import javafx.scene.canvas.GraphicsContext
import javafx.scene.control.Label
import javafx.scene.layout.HBox
import javafx.scene.layout.Pane
import javafx.scene.paint.Color
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.WAVEFORM_RANGE_LINK
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.WAVEFORM_RANGE_MAX
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.WAVEFORM_RANGE_MIN
import me.ksanstone.wavesync.wavesync.WaveSyncBootApplication
import me.ksanstone.wavesync.wavesync.gui.controller.visualizer.waveform.WaveformSettingsController
import me.ksanstone.wavesync.wavesync.gui.utility.AutoCanvas
import me.ksanstone.wavesync.wavesync.service.AudioCaptureService
import me.ksanstone.wavesync.wavesync.service.FourierMath.frequencySamplesAtRate
import me.ksanstone.wavesync.wavesync.service.LocalizationService
import me.ksanstone.wavesync.wavesync.service.PreferenceService
import me.ksanstone.wavesync.wavesync.service.SupportedCaptureSource
import me.ksanstone.wavesync.wavesync.utility.RollingBuffer
import kotlin.math.roundToInt


class WaveformVisualizer : AutoCanvas() {

    val enableAutoAlign: BooleanProperty = SimpleBooleanProperty(false)
    val rangeMax: FloatProperty = SimpleFloatProperty(WAVEFORM_RANGE_MAX)
    val rangeMin: FloatProperty = SimpleFloatProperty(WAVEFORM_RANGE_MIN)
    val rangeLink: BooleanProperty = SimpleBooleanProperty(WAVEFORM_RANGE_LINK)

    private val buffer: RollingBuffer<Float> = RollingBuffer(10000, 0.0f)
    private val waveColor: StyleableProperty<Color> = FACTORY.createStyleableColorProperty(this, "waveColor", "-fx-color") { vis -> vis.waveColor }
    private val align: BooleanProperty = SimpleBooleanProperty(false)
    private val alignFrequency: DoubleProperty = SimpleDoubleProperty(100.0)
    private val alignLowPass: DoubleProperty = SimpleDoubleProperty(20.0)
    private var sampleRate: IntegerProperty = SimpleIntegerProperty(48000)
    private val downSampledSize: IntegerProperty = SimpleIntegerProperty(buffer.size)

    init {
        canvasContainer.xAxisShown.value = false
        yAxis.tickUnit = 0.2
        // .toString().toDouble() hack to get an exact conversion
        yAxis.lowerBoundProperty().bind(rangeMin.map { it.toString().toDouble() })
        yAxis.upperBoundProperty().bind(rangeMax.map { it.toString().toDouble() })

        val acs = WaveSyncBootApplication.applicationContext.getBean(AudioCaptureService::class.java)
        val ls = WaveSyncBootApplication.applicationContext.getBean(LocalizationService::class.java)
        alignFrequency.bind(acs.peakFrequency)
        alignLowPass.set(calcAlignLowPass())
        sampleRate.addListener { _ -> alignLowPass.value = calcAlignLowPass() }
        align.bind(
            acs.peakValue.greaterThan(0.05f).and(enableAutoAlign).and(acs.peakFrequency.greaterThan(alignLowPass))
                .and(acs.peakFrequency.lessThanOrEqualTo(20000))
        )

        val alignInfo = Label()
        alignFrequency.addListener { _ -> info(alignInfo) }
        align.addListener { _ -> info(alignInfo) }

        infoPane.add(Label(ls.get("visualizer.waveform.info.align")), 0, 3)
        infoPane.add(alignInfo, 1, 3)

        val downSampleInfoLabel = Label()
        downSampledSize.addListener { _ -> downSampleInfo(downSampleInfoLabel) }

        infoPane.add(Label(ls.get("visualizer.waveform.info.samples")), 0, 4)
        infoPane.add(downSampleInfoLabel, 1, 4)

        styleClass.add("waveform-visualizer")
        stylesheets.add("/styles/waveform-visualizer.css")
    }

    private fun calcAlignLowPass(): Double {
        return 1 / (buffer.size.toDouble() / sampleRate.get().toDouble()) * 1.5
    }

    fun registerPreferences(id: String, preferenceService: PreferenceService) {
        preferenceService.registerProperty(enableAutoAlign, "autoAlign", this.javaClass, id)
        preferenceService.registerProperty(rangeMax, "rangeMax", this.javaClass, id)
        preferenceService.registerProperty(rangeMin, "rangeMin", this.javaClass, id)
        preferenceService.registerProperty(rangeLink, "rangeLink", this.javaClass, id)
        preferenceService.registerProperty(canvasContainer.yAxisShown, "yAxisShown", this.javaClass, id)
    }

    fun initializeSettingMenu() {
        val loader = FXMLLoader()
        loader.location = javaClass.classLoader.getResource("layout/waveform")
        loader.resources =
            WaveSyncBootApplication.applicationContext.getBean(LocalizationService::class.java).getDefault()
        val controls: HBox =
            loader.load(javaClass.classLoader.getResourceAsStream("layout/waveform/waveformSettings.fxml"))
        val controller: WaveformSettingsController = loader.getController()
        controller.waveformChartSettingsController.initialize(this)
        controlPane.children.add(controls)
    }

    private fun info(label: Label) {
        Platform.runLater {
            label.text = "${alignFrequency.value}Hz ${align.value}"
        }
    }

    private fun downSampleInfo(label: Label) {
        Platform.runLater {
            label.text = "${downSampledSize.get()} \u2022 ${buffer.size}"
        }
    }

    override fun draw(gc: GraphicsContext, deltaT: Double, now: Long, width: Double, height: Double) {
        gc.clearRect(0.0, 0.0, width, height)

        var drop = 0
        var take = buffer.size
        val min = rangeMin.get()
        val max = rangeMax.get()
        val rangeBreadth = max - min

        if (align.get()) {
            val waveSize = frequencySamplesAtRate(alignFrequency.value, sampleRate.get())
            drop = (waveSize - buffer.written % waveSize).toInt().coerceIn(0, buffer.size - 50)
            take = (buffer.size - waveSize).coerceIn(10.0, waveSize * 15).roundToInt().coerceAtMost(buffer.size - drop)
        }

        var stepAccumulator = 0.0
        val step = take.toDouble() / width.roundToInt()

        gc.stroke = waveColor.value
        gc.beginPath()
        var acc = 0
        for (i in drop until drop + take) {
            val ai = i - drop
            if (++stepAccumulator < step) continue
            stepAccumulator -= step
            gc.lineTo(ai.toDouble() / take * width, (buffer[i] - min).toDouble() / rangeBreadth * height)
            acc++
        }
        downSampledSize.set(acc)
        gc.stroke()
    }

    fun handleSamples(samples: FloatArray, source: SupportedCaptureSource) {
        sampleRate.value = source.format.mix.rate
        buffer.insert(samples.toTypedArray())
    }

    override fun getCssMetaData(): List<CssMetaData<out Styleable?, *>> {
        return FACTORY.cssMetaData
    }

    companion object {
        private val FACTORY: StyleablePropertyFactory<WaveformVisualizer> = StyleablePropertyFactory<WaveformVisualizer>(
            Pane.getClassCssMetaData()
        )

        @Suppress("unused")
        fun getClassCssMetaData(): List<CssMetaData<out Styleable?, *>> {
            return FACTORY.cssMetaData
        }
    }
}
