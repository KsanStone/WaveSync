package me.ksanstone.wavesync.wavesync.service.smoothing

interface MagnitudeSmoother {
    var dataSize: Int
    var data: FloatArray
    var dataTarget: FloatArray
    var factor: Double
    fun applySmoothing(deltaT: Double)
    fun setData(data: FloatArray, offset: Int, len: Int, transformer: (Float) -> Float)
}