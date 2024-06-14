package me.ksanstone.wavesync.wavesync.utility

class FreeRangeMapper(
    val from: IntRange,
    val to: IntRange
) {

    private val sizeFrom = from.size()
    private val sizeTo = to.size()

    private val scalarFrom = 1.0 / sizeFrom.toDouble()
    private val scalarTo = 1.0 / sizeTo.toDouble()

    /**
     * Map an index in the `from` range to an index in the `to` range
     */
    fun forwards(index: Int): Int {
        val absolutePosition = (index - from.first).toDouble() * scalarFrom
        return (absolutePosition * sizeTo).toInt() + to.first
    }

    /**
     * Map an index in the `to` range to an index in the `from` range
     */
    fun backwards(index: Int): Int {
        val absolutePosition = (index - from.first).toDouble() * scalarFrom
        return (absolutePosition * sizeTo).toInt() + to.first
    }

}

fun IntRange.size(): Int {
    return this.last - this.first
}