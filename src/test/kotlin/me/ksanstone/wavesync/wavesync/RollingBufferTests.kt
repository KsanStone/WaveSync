package me.ksanstone.wavesync.wavesync

import me.ksanstone.wavesync.wavesync.utility.RollingBuffer
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RollingBufferTests {

    @Test
    fun dataOrderTest() {
        val buffer = RollingBuffer(100,0)
        IntRange(0,50).toList().forEach { buffer.insert(it) }
        val arr = Array(100) { 0 }
        buffer.cloneOnto(arr)
        var iterated = buffer.iterator().asSequence().toList()

        assertEquals(true, arr.isSorted())
        assertArrayEquals(arr, iterated.toTypedArray())
        assertArrayEquals(arr, buffer.toArray())

        IntRange(50,300).toList().forEach { buffer.insert(it) }
        buffer.cloneOnto(arr)
        iterated = buffer.iterator().asSequence().toList()
        assertEquals(true, arr.isSorted())
        assertArrayEquals(arr, iterated.toTypedArray())
        assertArrayEquals(arr, buffer.toArray())
    }

}

fun Array<Int>.isSorted(): Boolean {
    assert(this.isNotEmpty())
    var a = this[0]
    for (i in indices) {
        if (this[i] < a) return false
        a = this[i]
    }
    return true
}