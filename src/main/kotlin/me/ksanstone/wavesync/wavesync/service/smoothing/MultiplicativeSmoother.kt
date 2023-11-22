package me.ksanstone.wavesync.wavesync.service.smoothing

class MultiplicativeSmoother : MagnitudeSmoother {

    override lateinit var dataTarget: FloatArray
    private lateinit var dataArray: FloatArray

    override var factor: Double = 0.91
    var boundMin = 0.0f
    var boundMax = 1.0f

    override var dataSize: Int
        get() = dataTarget.size
        set(value) {
            dataTarget = FloatArray(value)
            dataArray = FloatArray(value)
        }
    override var data: FloatArray
        get() = dataArray
        set(value) {
            value.copyInto(dataTarget)
        }

    override fun applySmoothing(deltaT: Double) {
        val f = (1 - factor.coerceIn(0.0, 1.0)).toFloat()
        val dt = (deltaT * 100).toFloat().coerceAtMost(1.0f)

        for (i in 0 until dataSize) {
            dataArray[i] = (
                    dataArray[i] + (dataTarget[i] - dataArray[i]) * f * dt
                    ).coerceIn(boundMin, boundMax)
        }
    }
}