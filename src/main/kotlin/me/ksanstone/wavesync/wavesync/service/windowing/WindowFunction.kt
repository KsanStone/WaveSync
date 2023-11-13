package me.ksanstone.wavesync.wavesync.service.windowing

import java.lang.Float.isNaN

abstract class WindowFunction(private var windowSize: Int) {

    private var factors: FloatArray? = null
    private var sum: Float = Float.NaN

    init {
        factors = getPrecomputedFactors(windowSize)
    }

    fun applyFunction(window: FloatArray) {
        if (window.size == this.windowSize) {
            for (i in window.indices) {
                window[i] *= factors!![i]
            }
        } else {
            throw IllegalArgumentException(
                ("Incompatible window size for this WindowFunction instance : " +
                        "expected " + windowSize) + ", received " + window.size
            )
        }
    }

    fun getSum(): Float {
        return if (!isNaN(sum)) sum else factors!!.sum()
    }

    fun getWindow(): FloatArray {
        return factors ?: getPrecomputedFactors(windowSize)
    }

    protected abstract fun getPrecomputedFactors(windowSize: Int): FloatArray

    companion object {
        const val TWO_PI = 2 * Math.PI
    }

}

enum class WindowFunctionType(val displayName: String) {
    HAMMING("Hamming"),
    BLACKMAN_HARRIS("Blackman Harris"),
    HANN("Hann");

    companion object {
        fun fromDisplayName(name: String): WindowFunctionType {
            return entries.find { it.displayName == name } ?: HAMMING
        }
    }
}