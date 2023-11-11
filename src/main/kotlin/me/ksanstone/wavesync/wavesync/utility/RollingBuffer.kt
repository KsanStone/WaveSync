package me.ksanstone.wavesync.wavesync.utility

import java.lang.Integer.max

class RollingBuffer<T : Any>(val size: Int = 1024, private val default: T) : Iterable<T> {

    var written: Long = 0L
        private set

    var data: Array<Any> = Array(size) { default }
        private set

    var currentHeadPosition = 0
        private set

    fun insert(elems: Array<T>) {
        val start = max(elems.size - size, 0)

        val copiedBatchSize = elems.size - start
        val initialCopyBatch = (size - currentHeadPosition - 1).coerceAtMost(copiedBatchSize)

        System.arraycopy(elems, start, data, currentHeadPosition + 1, initialCopyBatch)
        System.arraycopy(elems, start + initialCopyBatch, data, 0, copiedBatchSize - initialCopyBatch)

        currentHeadPosition = (currentHeadPosition + copiedBatchSize) % size
        written += elems.size
    }

    fun insert(elem: T) {
        currentHeadPosition = (currentHeadPosition + 1) % size
        data[currentHeadPosition] = elem
        written++
    }

    @Suppress("UNCHECKED_CAST")
    operator fun get(index: Int): T {
        if (index !in 0 until size) throw ArrayIndexOutOfBoundsException("index $index is out of bounds 0 - $size")
        return data[(currentHeadPosition + index + 1) % size] as T
    }

    override fun iterator(): Iterator<T> {
        return object : Iterator<T> {

            private var i = 0

            override fun hasNext(): Boolean {
                return i < size
            }

            override fun next(): T {
                return get(i++)
            }

        }
    }

    fun cloneOnto(array: Array<T>): Array<T> {
        assert(array.size == size) { "Array size mismatch" }
        System.arraycopy(data, currentHeadPosition + 1, array, 0, size - currentHeadPosition - 1)
        System.arraycopy(data, 0, array, size - currentHeadPosition - 1, currentHeadPosition + 1)
        return array
    }

    @Suppress("UNCHECKED_CAST")
    fun toArray(): Array<T> {
        return cloneOnto(Array<Any>(size) {default} as Array<T>)
    }
}

fun RollingBuffer<Float>.toFloatArray(): FloatArray {
    val array = FloatArray(this.size)
    // sadly, we can't use System.arraycopy here
    for (i in currentHeadPosition + 1 until size) array[i - currentHeadPosition - 1] = data[i] as Float
    for (i in 0 until currentHeadPosition + 1) array[i + size - currentHeadPosition - 1] = data[i] as Float
    return array
}