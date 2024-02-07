package me.ksanstone.wavesync.wavesync.service.interpolation

import me.ksanstone.wavesync.wavesync.service.FourierMath.frequencyOfBin

class LinearInterpolator : FFTPeakInterpolator {
    override fun calcPeak(samples: FloatArray, peakIndex: Int, rate: Int): Float {
        val peakV = samples[peakIndex]

        val n1 = (samples.getOrNull(peakIndex - 1) ?: 0.0f).toFloat()
        val n2 = (samples.getOrNull(peakIndex + 1) ?: 0.0f).toFloat()

        val n1r = n1 / peakV
        val n2r = n2 / peakV

        val factor = n2r - n1r
        var offset = frequencyOfBin(rate, samples.size * 2) * factor
        offset = offset.coerceIn(-1.0f, 1.0f)

        return frequencyOfBin(peakIndex, rate, samples.size * 2) + offset
    }
}