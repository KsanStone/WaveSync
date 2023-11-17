package me.ksanstone.wavesync.wavesync.service.fftScaling

class LinearFFTScalar : FFTScalar<LinearFFTScalarParams> {
    override fun scale(res: Float): Float {
        return res
    }

    override fun scaleRaw(res: Float): Float {
        return res
    }

    override fun getAxisScale(): AxisScale {
        return AxisScale(
            min = 0.0,
            max = 1.0,
            step = 0.1
        )
    }

    override fun update(params: LinearFFTScalarParams) {}
}

class LinearFFTScalarParams