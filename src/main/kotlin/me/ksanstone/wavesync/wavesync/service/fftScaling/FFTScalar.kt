package me.ksanstone.wavesync.wavesync.service.fftScaling

interface FFTScalar<E : Any> {
    fun scale(res: Float): Float

    fun update(params: E)

    fun getAxisScale(): AxisScale
}

data class AxisScale(
    val min: Double,
    val max: Double,
    val step: Double
)

enum class FFTScalarType {
    LINEAR,
    EXAGGERATED,
    DECIBEL
}