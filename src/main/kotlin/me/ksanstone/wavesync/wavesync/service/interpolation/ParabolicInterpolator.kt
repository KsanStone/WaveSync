package me.ksanstone.wavesync.wavesync.service.interpolation

import me.ksanstone.wavesync.wavesync.service.FourierMath

class ParabolicInterpolator : FFTPeakInterpolator {

    private fun parabolicInterpolate(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float): Float {
        val denominator = (x1 - x2) * (x1 - x3) * (x2 - x3)
        val a = (x3 * (y2 - y1) + x2 * (y1 - y3) + x1 * (y3 - y2)) / denominator
        val b = (x3 * x3 * (y1 - y2) + x2 * x2 * (y3 - y1) + x1 * x1 * (y2 - y3)) / denominator
        // we do not need the c coefficient, as we only care about the peak

        return -b / (a*2)
    }

    override fun calcPeak(samples: FloatArray, peakIndex: Int, rate: Int): Float {
        return parabolicInterpolate(
            FourierMath.frequencyOfBin(peakIndex - 1, rate, samples.size * 2).toFloat(),
            samples.getOrElse(peakIndex - 1) { 0.0f },
            FourierMath.frequencyOfBin(peakIndex, rate, samples.size * 2).toFloat(),
            samples.getOrElse(peakIndex) { 0.0f },
            FourierMath.frequencyOfBin(peakIndex + 1, rate, samples.size * 2).toFloat(),
            samples.getOrElse(peakIndex + 1) { 0.0f },
        )
    }
}