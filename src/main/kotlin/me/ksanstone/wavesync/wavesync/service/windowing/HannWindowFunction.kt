package me.ksanstone.wavesync.wavesync.service.windowing

import kotlin.math.cos

class HannWindowFunction(windowSize: Int) : WindowFunction(windowSize) {
    override fun getPrecomputedFactors(windowSize: Int): FloatArray {
        synchronized(HannWindowFunction::class.java) {
            val factors: FloatArray
            if (factorsByWindowSize.containsKey(windowSize)) {
                factors = factorsByWindowSize[windowSize]!!
            } else {
                factors = FloatArray(windowSize)
                val sizeMinusOne = windowSize - 1
                for (i in 0 until windowSize) {
                    factors[i] = (0.5 * (1 - cos(TWO_PI * i / sizeMinusOne))).toFloat()
                }
                factorsByWindowSize[windowSize] = factors
            }
            return factors
        }
    }

    companion object {
        private val factorsByWindowSize: MutableMap<Int, FloatArray> = HashMap()
    }
}