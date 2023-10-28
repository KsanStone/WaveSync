package me.ksanstone.wavesync.wavesync.service.windowing

abstract class WindowFunction(private var windowSize: Int) {

    private var factors: FloatArray? = null

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

    protected abstract fun getPrecomputedFactors(windowSize: Int): FloatArray

    companion object {
        const val TWO_PI = 2 * Math.PI
    }

}

enum class WindowFunctionType(val displayName: String) {
    HAMMING("Hamming"),
    HANN("Hann");

    companion object {
        fun fromDisplayName(name: String): WindowFunctionType {
            return entries.find { it.displayName == name } ?: HAMMING
        }
    }
}