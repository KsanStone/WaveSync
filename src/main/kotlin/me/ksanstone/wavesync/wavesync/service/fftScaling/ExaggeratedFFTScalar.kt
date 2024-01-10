package me.ksanstone.wavesync.wavesync.service.fftScaling

import kotlin.math.ln
import kotlin.math.sqrt

class ExaggeratedFFTScalar : FFTScalar<ExaggeratedFFTScalarParams> {

    private var scaling = 20f

    override fun scale(res: Float): Float {
        val res2 = sqrt(res)
        return (res2 * (scaling * (1.0f - ln(res2 + 0.2f) - 0.813f) + 1)).coerceAtMost(1.0f)
    }

    override fun scaleRaw(res: Float): Float {
        return this.scale(res)
    }

    override fun getAxisScale(): AxisScale {
        return AxisScale(
            min = 0.0,
            max = 1.0,
            step = 0.1
        )
    }

    override fun update(params: ExaggeratedFFTScalarParams) {
        this.scaling = params.scaling
    }
}

data class ExaggeratedFFTScalarParams(
    val scaling: Float
)