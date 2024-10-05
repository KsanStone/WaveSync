package me.ksanstone.wavesync.wavesync

import javafx.scene.paint.Color
import me.ksanstone.wavesync.wavesync.gui.component.visualizer.BarVisualizer
import me.ksanstone.wavesync.wavesync.gui.component.visualizer.VectorScopeVisualizer
import me.ksanstone.wavesync.wavesync.gui.component.visualizer.WaveformVisualizer
import me.ksanstone.wavesync.wavesync.gui.gradient.pure.DefaultGradient
import me.ksanstone.wavesync.wavesync.service.fftScaling.FFTScalarType
import me.ksanstone.wavesync.wavesync.service.windowing.WindowFunctionType

object ApplicationSettingDefaults {
    const val DEFAULT_BAR_SMOOTHING = 0.91F
    const val DEFAULT_BAR_SCALING = 20.0F
    const val DEFAULT_LINEAR_BAR_SCALING = 1.0F
    const val DEFAULT_BAR_LOW_PASS = 0
    const val DEFAULT_BAR_CUTOFF = 20000
    const val DEFAULT_TARGET_BAR_WIDTH = 4
    const val DEFAULT_FFT_SIZE = 1024
    const val DEFAULT_MIN_UI_VISUALIZER_WINDOW = 500
    const val DEFAULT_THEME = "Primer Dark"
    const val DEFAULT_REFRESH_RATE = 60
    const val DEFAULT_GAP = 0
    const val DEFAULT_INFO_SHOWN = false
    const val DEFAULT_DB_MIN = -90.0f
    const val DEFAULT_DB_MAX = 5.0f
    const val DEFAULT_WAVEFORM_RANGE_MIN = -1.0f
    const val DEFAULT_WAVEFORM_RANGE_MAX = 1.0f
    const val DEFAULT_WAVEFORM_RANGE_LINK = true
    const val DEFAULT_FFT_RATE = 60
    const val DEFAULT_PEAK_LINE_VISIBLE = false
    const val DEFAULT_LOGARITHMIC_MODE = false
    const val DEFAULT_FILL_UNDER_CURVE = true
    const val DEFAULT_SMOOTH_CURVE = true
    const val DEFAULT_USE_CSS_COLOR = true
    const val DEFAULT_SHOW_PEAK = false
    const val DEFAULT_VECTOR_X_RANGE = 0.5
    const val DEFAULT_VECTOR_Y_RANGE = 0.5
    const val DEFAULT_VECTOR_RANGE_LINK = true
    val DEFAULT_VECTOR_MODE = VectorScopeVisualizer.VectorOrientation.SKEWED
    val DEFAULT_SCALAR_TYPE = FFTScalarType.DECIBEL
    val DEFAULT_WINDOWING_FUNCTION = WindowFunctionType.HAMMING
    val DEFAULT_BAR_RENDER_MODE = BarVisualizer.RenderMode.BAR
    val DEFAULT_BAR_SMOOTHER_TYPE = BarVisualizer.SmootherType.FALLOFF
    val DEFAULT_WAVEFORM_RENDER_MODE = WaveformVisualizer.RenderMode.LINE
    val DEFAULT_START_COLOR: Color = Color.rgb(255, 120, 246)
    val DEFAULT_END_COLOR: Color = Color.AQUA
    val DEFAULT_SPECTROGRAM_GRADIENT = DefaultGradient.SPECTROGRAM.gradient
}