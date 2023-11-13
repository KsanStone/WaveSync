package me.ksanstone.wavesync.wavesync.service.windowing

import kotlin.math.cos

class BlackmanHarrisWindowFunction(windowSize: Int) : WindowFunction(windowSize) {
    override fun getPrecomputedFactors(windowSize: Int): FloatArray {
        val factors = FloatArray(windowSize)

        val a0 = 0.35875f
        val a1 = 0.48829f
        val a2 = 0.14128f
        val a3 = 0.01168f

        for (i in 0 until windowSize) {
            factors[i] =
                a0 - (a1 * cos((2.0f * Math.PI.toFloat() * i) / (windowSize - 1))) + (a2 * cos((4.0f * Math.PI.toFloat() * i) / (windowSize - 1))) - (a3 * cos(
                    (6.0f * Math.PI.toFloat() * i) / (windowSize - 1)
                ))
        }

        return factors
    }
}