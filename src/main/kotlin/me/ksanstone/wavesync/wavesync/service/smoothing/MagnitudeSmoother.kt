package me.ksanstone.wavesync.wavesync.service.smoothing

interface MagnitudeSmoother {
    var dataSize: Int
    var data: FloatArray
    var factor: Double
    fun applySmoothing(deltaT: Double)
}