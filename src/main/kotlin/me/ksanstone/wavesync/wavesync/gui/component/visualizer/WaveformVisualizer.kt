package me.ksanstone.wavesync.wavesync.gui.component.visualizer

import javafx.application.Platform
import javafx.beans.property.*
import javafx.fxml.FXMLLoader
import javafx.scene.canvas.GraphicsContext
import javafx.scene.control.Label
import javafx.scene.layout.HBox
import javafx.scene.paint.Color
import me.ksanstone.wavesync.wavesync.WaveSyncBootApplication
import me.ksanstone.wavesync.wavesync.gui.controller.visualizer.waveform.WaveformSettingsController
import me.ksanstone.wavesync.wavesync.gui.utility.AutoCanvas
import me.ksanstone.wavesync.wavesync.service.AudioCaptureService
import me.ksanstone.wavesync.wavesync.service.FourierMath.frequencySamplesAtRate
import me.ksanstone.wavesync.wavesync.service.LocalizationService
import me.ksanstone.wavesync.wavesync.service.PreferenceService
import me.ksanstone.wavesync.wavesync.service.SupportedCaptureSource
import me.ksanstone.wavesync.wavesync.service.downsampling.DownSampler
import me.ksanstone.wavesync.wavesync.service.downsampling.UniformDownSampler
import me.ksanstone.wavesync.wavesync.utility.RollingBuffer
import kotlin.math.roundToInt

class WaveformVisualizer : AutoCanvas() {

    val enableAutoAlign: BooleanProperty = SimpleBooleanProperty(false)

    private val buffer: RollingBuffer<Float> = RollingBuffer(10000, 0.0f)
    private val startColor: ObjectProperty<Color> = SimpleObjectProperty(Color.rgb(255, 120, 246))
    private val endColor: ObjectProperty<Color> = SimpleObjectProperty(Color.AQUA)
    private val align: BooleanProperty = SimpleBooleanProperty(false)
    private val alignFrequency: DoubleProperty = SimpleDoubleProperty(100.0)
    private var sampleRate: Int = 48000
    private var downSampler: DownSampler<Float> = UniformDownSampler()
    private val downSampledSize: IntegerProperty = SimpleIntegerProperty(buffer.size)

    init {
        val acs = WaveSyncBootApplication.applicationContext.getBean(AudioCaptureService::class.java)
        val ls = WaveSyncBootApplication.applicationContext.getBean(LocalizationService::class.java)
        alignFrequency.bind(acs.peakFrequency)
        align.bind(acs.peakValue.greaterThan(0.05f).and(enableAutoAlign))

        val alignInfo = Label()
        alignFrequency.addListener { _ -> info(alignInfo) }
        align.addListener { _ -> info(alignInfo) }

        infoPane.add(Label(ls.get("visualizer.waveform.info.align")), 0, 3)
        infoPane.add(alignInfo, 1, 3)

        val downSampleInfoLabel = Label()
        downSampledSize.addListener { _ -> downSampleInfo(downSampleInfoLabel) }

        infoPane.add(Label(ls.get("visualizer.waveform.info.samples")), 0, 4)
        infoPane.add(downSampleInfoLabel, 1, 4)
    }

    fun registerPreferences(id: String, preferenceService: PreferenceService) {
        preferenceService.registerProperty(enableAutoAlign, "$id-waveformVisualizer-autoAlign")
    }

    fun initializeSettingMenu() {
        val loader = FXMLLoader()
        loader.location = javaClass.classLoader.getResource("layout/waveform")
        loader.resources =
            WaveSyncBootApplication.applicationContext.getBean(LocalizationService::class.java).getDefault()
        val controls: HBox = loader.load(javaClass.classLoader.getResourceAsStream("layout/waveform/waveformSettings.fxml"))
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

        var iter: Iterable<Float> = buffer
        val size: Int

        if (align.get() && alignFrequency.value > 0 && alignFrequency.value < 20000) {
            val waveSize = frequencySamplesAtRate(alignFrequency.value, sampleRate)
            val drop = waveSize - (buffer.written % waveSize.toUInt()).toInt()
            val take = (buffer.size - waveSize).coerceIn(10.0, waveSize * 15)
            iter = iter.drop(drop.roundToInt()).take(take.roundToInt())
        }

        val points = downSampler.downSample(iter.toList(), width.roundToInt())
        size = points.size
        downSampledSize.set(size)

        gc.stroke = endColor.get()
        gc.beginPath()
        for ((i, sample) in points.withIndex()) {
            gc.lineTo(i.toDouble() / size * width, (sample + 1.0f).toDouble() / 2.0 * height)
        }
        gc.stroke()
    }

    // val color = startColor.get().interpolate(endColor.get(), abs(sample).toDouble().coerceIn(0.0, 1.0))
    // gc.fill = color

    fun handleSamples(samples: FloatArray, source: SupportedCaptureSource) {
        sampleRate = source.format.mix.rate
        buffer.insert(samples.toTypedArray())
    }
}
