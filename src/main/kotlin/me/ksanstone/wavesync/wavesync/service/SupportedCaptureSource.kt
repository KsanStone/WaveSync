package me.ksanstone.wavesync.wavesync.service

import xt.audio.Structs
import xt.audio.XtDevice
import kotlin.math.ceil

data class SupportedCaptureSource(
    val device: XtDevice,
    val format: Structs.XtFormat,
    val name: String,
    val id: String
) {
    override fun toString(): String {
        return "SupportedCaptureSource { name: $name id: $id freq: ${format.mix.rate} channel: ${format.channels.inputs} format: ${format.mix.sample} }"
    }

    fun getMinimumSamples(frequency: Int): Int {
        return getMinimumSamples(frequency, format.mix.rate)
    }

    fun getMaxFrequency(): Int {
        return getMaxFrequencyForRate(format.mix.rate)
    }

    fun trimResultTo(size: Int, frequency: Int): Int {
        return trimResultBufferTo(size, format.mix.rate, frequency)
    }

    fun getPropertyDescriptor(targetMin: Int, targetMax: Int): String {
        val minSamples = getMinimumSamples(targetMin)
        return "${format.mix.rate}Hz • ${format.mix.sample} • $minSamples [${trimResultTo(minSamples * 2, targetMax)}] samples • ${targetMin}Hz - ${targetMax}Hz"
    }

    companion object {
        fun getMinimumSamples(frequency: Int, rate: Int): Int {
            return (1.0 / frequency * rate).toInt().closestPowerOf2()
        }

        fun getMaxFrequencyForRate(rate: Int): Int {
            return rate / 2
        }

        fun trimResultBufferTo(bufferSize: Int, rate: Int, frequency: Int): Int {
            val factor = rate.toDouble() / bufferSize.toDouble()
            return ceil(frequency.toDouble() / factor).toInt().coerceAtMost(bufferSize)
        }
    }
}


fun Int.closestPowerOf2(): Int {
    var pow = 1
    while (pow < this && pow > 0) {
        pow = pow shl 1
    }
    return pow
}