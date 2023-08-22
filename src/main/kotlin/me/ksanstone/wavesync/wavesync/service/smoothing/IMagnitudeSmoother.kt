package me.ksanstone.wavesync.wavesync.service.smoothing

interface IMagnitudeSmoother {
    var dataSize: Int
    var data: FloatArray
    var factor: Double
    fun applySmoothing(deltaT: Double)
}