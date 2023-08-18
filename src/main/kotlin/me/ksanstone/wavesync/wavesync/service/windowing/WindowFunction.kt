package me.ksanstone.wavesync.wavesync.service.windowing

abstract class WindowFunction(private var windowSize: Int) {

    private var factors: FloatArray? = null
    protected val TWO_PI = 2 * Math.PI

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
                        "expected " + windowSize).toString() + ", received " + window.size
            )
        }
    }

    protected abstract fun getPrecomputedFactors(windowSize: Int): FloatArray

}