package me.ksanstone.wavesync.wavesync.service.fftScaling

interface FFTScalar<E: Any> {
    fun scale(res: Float): Float

    fun update(params: E)
}

enum class FFTScalarType {
    LINEAR,
    EXAGGERATED,
    DECIBEL
}