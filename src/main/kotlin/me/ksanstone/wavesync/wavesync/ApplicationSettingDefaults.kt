package me.ksanstone.wavesync.wavesync

import me.ksanstone.wavesync.wavesync.gui.component.visualizer.BarVisualizer
import me.ksanstone.wavesync.wavesync.gui.component.visualizer.WaveformVisualizer
import me.ksanstone.wavesync.wavesync.service.fftScaling.FFTScalarType
import me.ksanstone.wavesync.wavesync.service.windowing.WindowFunctionType

object ApplicationSettingDefaults {
    const val BAR_SMOOTHING = 0.91F
    const val BAR_SCALING = 20.0F
    const val LINEAR_BAR_SCALING = 1.0F
    const val BAR_LOW_PASS = 0
    const val BAR_CUTOFF = 20000
    const val TARGET_BAR_WIDTH = 4
    const val FFT_SIZE = 1024
    const val MIN_UI_VISUALIZER_WINDOW = 500
    const val THEME = "Primer Dark"
    const val REFRESH_RATE = 60
    const val GAP = 0
    const val INFO_SHOWN = false
    const val DB_MIN = -90.0f
    const val DB_MAX = 5.0f
    const val WAVEFORM_RANGE_MIN = -1.0f
    const val WAVEFORM_RANGE_MAX = 1.0f
    const val WAVEFORM_RANGE_LINK = true
    const val DEFAULT_UPSAMPLING = 1
    const val PEAK_LINE_VISIBLE = false
    const val DEFAULT_LOGARITHMIC_MODE = false
    const val DEFAULT_FILL_UNDER_CURVE = true
    val DEFAULT_SCALAR_TYPE = FFTScalarType.DECIBEL
    val DEFAULT_WINDOWING_FUNCTION = WindowFunctionType.HAMMING
    val DEFAULT_BAR_RENDER_MODE = BarVisualizer.RenderMode.BAR
    val DEFAULT_WAVEFORM_RENDER_MODE = WaveformVisualizer.RenderMode.LINE
}