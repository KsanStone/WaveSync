package me.ksanstone.wavesync.wavesync.service.audio.interpolation

interface FFTPeakInterpolator {
    fun calcPeak(samples: FloatArray, peakIndex: Int, rate: Int): Float
}