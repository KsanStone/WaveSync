package me.ksanstone.wavesync.wavesync.utility

import java.lang.Integer.max

class RollingBuffer<T : Any>(val size: Int = 1024, private val default: T) : Iterable<T> {

    val written: ULong
        get() = _written

    private var data: Array<Any> = Array(size) { default }
    private var dataIndex = 0
    private var _written: ULong = 0u

    fun insert(elems: Array<T>) {
        val start = max(elems.size - size, 0)
        for (i in start until elems.size) {
            this.insert(elems[i])
        }
    }

    @Synchronized
    fun insert(elem: T) {
        dataIndex = (dataIndex + 1) % size
        data[dataIndex] = elem
        _written++
    }

    @Suppress("UNCHECKED_CAST")
    operator fun get(index: Int): T {
        if (index !in 0 until size) throw ArrayIndexOutOfBoundsException("index $index is out of bounds 0 - $size")
        return data[(dataIndex + index) % size] as T
    }

    override fun iterator(): Iterator<T> {
        return object : Iterator<T> {

            private var i = 0

            override fun hasNext(): Boolean {
                return i + 1 < size
            }

            override fun next(): T {
                return get(i++)
            }

        }
    }
}