package me.ksanstone.wavesync.wavesync.utility

import java.lang.Integer.max

class RollingBuffer<T : Any>(val size: Int = 1024, private val default: T) : Iterable<T> {

    var written: ULong = 0uL
        private set

    private var data: Array<Any> = Array(size) { default }
    private var dataIndex = 0

    fun insert(elems: Array<T>) {
        val start = max(elems.size - size, 0)

        val copiedBatchSize = elems.size - start
        val initialCopyBatch = (size - dataIndex - 1).coerceAtMost(copiedBatchSize)

        System.arraycopy(elems, start, data, dataIndex + 1, initialCopyBatch)
        System.arraycopy(elems, start + initialCopyBatch, data, 0, copiedBatchSize - initialCopyBatch)

        dataIndex = (dataIndex + copiedBatchSize) % size
        written += elems.size.toUInt()
    }

    @Synchronized
    fun insert(elem: T) {
        dataIndex = (dataIndex + 1) % size
        data[dataIndex] = elem
        written++
    }

    @Suppress("UNCHECKED_CAST")
    operator fun get(index: Int): T {
        if (index !in 0 until size) throw ArrayIndexOutOfBoundsException("index $index is out of bounds 0 - $size")
        return data[(dataIndex + index + 1) % size] as T
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
        System.arraycopy(data, dataIndex + 1, array, 0, size - dataIndex - 1)
        System.arraycopy(data, 0, array, size - dataIndex - 1, dataIndex + 1)
        return array
    }

    @Suppress("UNCHECKED_CAST")
    fun toArray(): Array<T> {
        return cloneOnto(Array<Any>(size) {default} as Array<T>)
    }
}