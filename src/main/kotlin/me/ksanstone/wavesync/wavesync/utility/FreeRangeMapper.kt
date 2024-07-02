package me.ksanstone.wavesync.wavesync.utility

import kotlin.math.log10
import kotlin.math.pow

interface RangeMapper {
    val from: IntRange
    val to: IntRange

    /**
     * Map an index in the `from` range to an index in the `to` range
     */
    fun forwards(index: Int): Int

    /**
     * Map an index in the `to` range to an index in the `from` range
     */
    fun backwards(index: Int): Int
}

class FreeRangeMapper(
    override val from: IntRange,
    override val to: IntRange
) : RangeMapper {

    private val sizeFrom = from.size()
    private val sizeTo = to.size()

    private val scalarFrom = 1.0 / sizeFrom.toDouble()
    private val scalarTo = 1.0 / sizeTo.toDouble()

    override fun forwards(index: Int): Int {
        val absolutePosition = (index - from.first).toDouble() * scalarFrom
        return (absolutePosition * sizeTo).toInt() + to.first
    }

    override fun backwards(index: Int): Int {
        val absolutePosition = (index - from.first).toDouble() * scalarTo
        return (absolutePosition * sizeTo).toInt() + to.first
    }

}

class LogRangeMapper(
    override val from: IntRange,
    override val to: IntRange
) : RangeMapper {
    private val sizeFrom = from.size()
    private val sizeTo = to.size()

    private val logLowerBoundForwards = log(to.first().toDouble())
    private val deltaForwards = log(to.last().toDouble()) - log(to.first().toDouble())

    private val logLowerBoundBackwards = log(to.first().toDouble())
    private val deltaBackwards = log(to.last().toDouble()) - log(to.first().toDouble())

    override fun forwards(index: Int): Int {
        return (10.0.pow(((((index.toDouble() - from.first) / sizeFrom) * deltaForwards) + logLowerBoundForwards))).toInt()
            .coerceIn(to)
    }

    override fun backwards(index: Int): Int {
        return (10.0.pow(((((index.toDouble() - to.first) / sizeTo) * deltaBackwards) + logLowerBoundBackwards))).toInt()
            .coerceIn(from)
    }

    private fun log(num: Double): Double {
        if (num == 0.0) return 0.0
        return log10(num)
    }
}

fun IntRange.size(): Int {
    return this.last - this.first + 1
}

class CachingRangeMapper(
    backingMapper: RangeMapper,
) : RangeMapper by backingMapper {

    private val cache = IntArray(backingMapper.from.size())

    init {
        val offset = from.first
        for (i in cache.indices) {
            cache[i] = backingMapper.forwards(i + offset)
        }
    }

    override fun forwards(index: Int): Int {
        return cache[index - from.first]
    }

}