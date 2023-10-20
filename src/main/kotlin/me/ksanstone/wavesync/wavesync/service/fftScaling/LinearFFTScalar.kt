package me.ksanstone.wavesync.wavesync.service.fftScaling

class LinearFFTScalar : FFTScalar<LinearFFTScalarParams> {
    override fun scale(res: Float): Float {
        return res
    }

    override fun update(params: LinearFFTScalarParams) {}
}

class LinearFFTScalarParams () {}