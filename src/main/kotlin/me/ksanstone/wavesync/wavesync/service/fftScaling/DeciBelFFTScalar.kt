package me.ksanstone.wavesync.wavesync.service.fftScaling

import kotlin.math.log10

class DeciBelFFTScalar : FFTScalar<DeciBelFFTScalarParameters> {

    private var rangeMin = -100f
    private var rangeMax = 0f
    private var scale = rangeMax - rangeMin

    override fun scale(res: Float): Float {
        return (10 * log10(res) - rangeMin).coerceIn(0.0F, scale) / scale
    }

    override fun scaleRaw(res: Float): Float {
        return 10 * log10(res)
    }

    override fun getAxisScale(): AxisScale {
        return AxisScale(
            min = rangeMin.toDouble(),
            max = rangeMax.toDouble(),
            step = 10.0
        )
    }

    override fun update(params: DeciBelFFTScalarParameters) {
        rangeMin = params.rangeMin
        rangeMax = params.rangeMax
        scale = rangeMax - rangeMin
    }
}

data class DeciBelFFTScalarParameters(
    val rangeMin: Float,
    val rangeMax: Float
)