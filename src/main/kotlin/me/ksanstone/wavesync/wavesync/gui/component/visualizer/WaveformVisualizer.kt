package me.ksanstone.wavesync.wavesync.gui.component.visualizer

import javafx.beans.property.*
import javafx.scene.canvas.GraphicsContext
import javafx.scene.paint.Color
import me.ksanstone.wavesync.wavesync.gui.utility.AutoCanvas
import me.ksanstone.wavesync.wavesync.service.FourierMath.frequencySamplesAtRate
import me.ksanstone.wavesync.wavesync.service.SupportedCaptureSource
import me.ksanstone.wavesync.wavesync.utility.RollingBuffer

class WaveformVisualizer : AutoCanvas() {

    private val buffer: RollingBuffer<Float> = RollingBuffer(10000, 0.0f)
    private val color: ObjectProperty<Color> = SimpleObjectProperty(Color.rgb(255, 120, 246))
    private val align: BooleanProperty = SimpleBooleanProperty(false)
    private val alignFrequency: IntegerProperty = SimpleIntegerProperty(100)
    private var sampleRate: Int = 48000

    override fun draw(gc: GraphicsContext, deltaT: Double, now: Long, width: Double, height: Double) {
        gc.clearRect(0.0, 0.0, width, height)

        gc.stroke = color.get()
        gc.fill = color.get()

        var iter: Iterable<Float> = buffer
        var size = buffer.size

        if(align.get()) {
            val waveSize = frequencySamplesAtRate(alignFrequency.value, sampleRate)
            val drop = waveSize - (buffer.written % waveSize.toUInt()).toInt()
            val take = (buffer.size - waveSize).coerceAtMost(waveSize * 15)
            size = take
            iter = iter.drop(drop).take(take)
        }

        for ((i, sample) in iter.withIndex()) {
            gc.fillRect(i.toDouble() / size * width, (sample + 1.0f).toDouble() / 2.0 * height, 1.0, 1.0)
        }
    }

    fun handleSamples(samples: FloatArray, source: SupportedCaptureSource) {
        sampleRate = source.format.mix.rate
        buffer.insert(samples.toTypedArray())
    }
}
