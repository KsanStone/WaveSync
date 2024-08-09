package me.ksanstone.wavesync.wavesync.service.smoothing

class ExponentialFalloffSmoother : MagnitudeSmoother {
    override lateinit var dataTarget: FloatArray
    private lateinit var dataArray: FloatArray

    override var factor: Double = 1.0
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
            setData(value, 0, value.size) { it }
        }

    override fun applySmoothing(deltaT: Double) {
        val f = factor.toFloat() * 30
        val dt = deltaT.toFloat().coerceAtMost(1.0f)

        for (i in 0 until dataSize) {
            dataArray[i] = (dataArray[i] - dataTarget[i] * dt).coerceIn(boundMin, boundMax)
            dataTarget[i] += f * dt
        }
    }

    override fun setData(data: FloatArray, offset: Int, len: Int, transformer: (Float) -> Float) {
        for (i in offset until len + offset) {
            val j = i - offset
            val point = transformer(data[i])
            if (dataArray[j] < point) {
                dataArray[j] = point
                dataTarget[j] = 0.0F
            }
        }
    }
}