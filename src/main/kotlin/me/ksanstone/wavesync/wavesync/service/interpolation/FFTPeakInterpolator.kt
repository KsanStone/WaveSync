package me.ksanstone.wavesync.wavesync.service.interpolation

interface FFTPeakInterpolator {
    fun calcPeak(samples: FloatArray, peakIndex: Int, rate: Int): Float
}