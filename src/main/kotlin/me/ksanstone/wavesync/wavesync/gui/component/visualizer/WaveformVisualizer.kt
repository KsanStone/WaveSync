package me.ksanstone.wavesync.wavesync.gui.component.visualizer

import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.scene.canvas.GraphicsContext
import javafx.scene.paint.Color
import me.ksanstone.wavesync.wavesync.RollingBuffer
import me.ksanstone.wavesync.wavesync.gui.utility.AutoCanvas
import me.ksanstone.wavesync.wavesync.service.FourierMath.frequencySamplesAtRate
import me.ksanstone.wavesync.wavesync.service.SupportedCaptureSource

class WaveformVisualizer : AutoCanvas() {

    private val buffer: RollingBuffer<Float> = RollingBuffer(10000, 0.0f)
    private val color: ObjectProperty<Color> = SimpleObjectProperty(Color.rgb(255, 120, 246))

    override fun draw(gc: GraphicsContext, deltaT: Double, now: Long, width: Double, height: Double) {
        gc.clearRect(0.0, 0.0, width, height)

        gc.stroke = color.get()
        gc.fill = color.get()

        for ((i, sample) in buffer.withIndex()) {
            gc.fillRect(i.toDouble() / buffer.size * width, (sample + 1.0f).toDouble() / 2.0 * height, 1.0, 1.0)
        }

    }

    fun handleSamples(samples: FloatArray, source: SupportedCaptureSource) {
        buffer.insert(samples.toTypedArray())
    }

    companion object {
        fun alignSampleCount(target: Int, rate: Int, samples: Int): Int {
            val targetSamples = frequencySamplesAtRate(target, rate)
            val fitWaves = (samples.toDouble() / targetSamples).toInt()
            return (fitWaves * targetSamples).coerceIn(100, samples)
        }
    }
}
