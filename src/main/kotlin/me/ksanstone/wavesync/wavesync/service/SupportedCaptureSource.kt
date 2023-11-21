package me.ksanstone.wavesync.wavesync.service

import javafx.util.Duration
import me.ksanstone.wavesync.wavesync.service.FourierMath.maxFrequencyForRate
import me.ksanstone.wavesync.wavesync.service.FourierMath.trimResultBufferTo
import xt.audio.Structs
import xt.audio.XtDevice
import java.text.NumberFormat
import kotlin.math.round

data class SupportedCaptureSource(
    val device: XtDevice,
    val format: Structs.XtFormat,
    val name: String,
    val id: String
) {
    override fun toString(): String {
        return "SupportedCaptureSource { name: $name id: $id freq: ${format.mix.rate} channel: ${format.channels.inputs} format: ${format.mix.sample} }"
    }

    fun getMaxFrequency(): Int {
        return maxFrequencyForRate(format.mix.rate)
    }

    fun trimResultTo(size: Int, frequency: Int): Int {
        return trimResultBufferTo(size, format.mix.rate, frequency)
    }

    fun getPropertyDescriptor(fftSize: Int, targetMin: Int, targetMax: Int, numberFormat: NumberFormat): String {
        val resultingSamples = trimResultTo(fftSize * 2, targetMax) - bufferBeginningSkipFor(targetMin, fftSize * 2)
        return "${numberFormat.format(format.mix.rate)}Hz • ${format.mix.sample} • 🔈 x${format.channels.inputs} • ${numberFormat.format(fftSize)} [${
            numberFormat.format(
                resultingSamples
            )
        }] • ${numberFormat.format(targetMin)}Hz - ${numberFormat.format(targetMax)}Hz"
    }

    fun getMinimumFrequency(samples: Int): Int {
        val sampleT = samples.toDouble() / format.mix.rate
        return round(1.0 / sampleT).toInt()
    }

    fun bufferBeginningSkipFor(freq: Int, bufferSize: Int): Int {
        return trimResultBufferTo(bufferSize, this.format.mix.rate, freq)
    }

    fun getUpdateInterval(samples: Int): Duration {
        return Duration.seconds(1.0 / format.mix.rate * samples)
    }
}