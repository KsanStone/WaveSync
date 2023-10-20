package me.ksanstone.wavesync.wavesync

import me.ksanstone.wavesync.wavesync.service.fftScaling.FFTScalarType

object ApplicationSettingDefaults {
    const val BAR_SMOOTHING = 0.91F
    const val BAR_SCALING = 20.0F
    const val BAR_LOW_PASS = 0
    const val BAR_CUTOFF = 20000
    const val TARGET_BAR_WIDTH = 4
    const val FFT_SIZE = 1024
    const val MIN_UI_VISUALIZER_WINDOW = 500
    const val THEME = "Primer Dark"
    const val REFRESH_RATE = 60
    const val GAP = 0
    const val INFO_SHOWN = false
    val DEFAULT_SCALAR_TYPE = FFTScalarType.DECIBEL
}