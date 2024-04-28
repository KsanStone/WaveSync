package me.ksanstone.wavesync.wavesync.service.interpolation

import me.ksanstone.wavesync.wavesync.service.FourierMath
import me.ksanstone.wavesync.wavesync.service.fftScaling.DeciBelFFTScalar

class ParabolicInterpolator : FFTPeakInterpolator {

    private val dbScalar: DeciBelFFTScalar = DeciBelFFTScalar()

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
            dbScalar.scaleRaw(samples.getOrElse(peakIndex - 1) { 0.0f }),
            FourierMath.frequencyOfBin(peakIndex, rate, samples.size * 2).toFloat(),
            dbScalar.scaleRaw(samples.getOrElse(peakIndex) { 0.0f }),
            FourierMath.frequencyOfBin(peakIndex + 1, rate, samples.size * 2).toFloat(),
            dbScalar.scaleRaw(samples.getOrElse(peakIndex + 1) { 0.0f }),
        ).let { if (it.isNaN()) 0.0f else it }
    }
}