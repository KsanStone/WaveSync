package me.ksanstone.wavesync.wavesync.service.fftScaling

import kotlin.math.sqrt

class LinearFFTScalar : FFTScalar<LinearFFTScalarParams> {

    private var scale: Float = 1.0F

    override fun scale(res: Float): Float {
        return (sqrt(res) * scale).coerceIn(0.0F, 1.0F)
    }

    override fun scaleRaw(res: Float): Float {
        return sqrt(res) * scale
    }

    override fun getAxisScale(): AxisScale {
        return AxisScale(
            min = 0.0,
            max = 1.0,
            step = 0.1
        )
    }

    override fun update(params: LinearFFTScalarParams) {
        this.scale = params.scaling
    }
}

class LinearFFTScalarParams(
    val scaling: Float = 1.0F
)