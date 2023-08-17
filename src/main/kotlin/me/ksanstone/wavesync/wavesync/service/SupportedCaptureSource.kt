package me.ksanstone.wavesync.wavesync.service

import xt.audio.Structs
import xt.audio.XtDevice

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
        return SupportedCaptureSource.getMinimumSamples(frequency, format.mix.rate)
    }

    companion object {
        fun getMinimumSamples(frequency: Int, rate: Int): Int {
            return (1.0 / frequency * rate).toInt()
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