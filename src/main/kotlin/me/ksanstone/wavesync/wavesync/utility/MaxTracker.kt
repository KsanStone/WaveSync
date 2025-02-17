package me.ksanstone.wavesync.wavesync.utility

import kotlin.math.max

class MaxTracker {

    private lateinit var dataArray: FloatArray

    var data: FloatArray
        get() = dataArray
        set(value) {
            if (value.size != dataSize) throw IllegalArgumentException("Size mismatch ${value.size} $dataSize")
            for (i in value.indices) {
                dataArray[i] = max(dataArray[i], value[i])
            }
        }
    var dataSize: Int
        get() = dataArray.size
        set(value) {
            dataArray = FloatArray(value)
        }

    fun zero() {
        this.dataSize = dataSize
    }

    fun applyData(data: FloatArray, offset: Int, limit: Int) {
        if (offset + limit > data.size || limit != dataSize) throw IllegalArgumentException("Size mismatch")
        for (i in 0 until limit) {
            dataArray[i] = max(data[i + offset], dataArray[i])
        }
    }
}