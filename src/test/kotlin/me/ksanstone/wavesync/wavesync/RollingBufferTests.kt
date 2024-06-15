package me.ksanstone.wavesync.wavesync

import me.ksanstone.wavesync.wavesync.utility.RollingBuffer
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RollingBufferTests {

    @Test
    fun dataOrderTest() {
        val testCases =
            listOf(50 to IntRange(0, 50), 50 to IntRange(0, 10), 50 to IntRange(0, 100), 50 to IntRange(0, -1), 50 to IntRange(0, 49))
        testCases.forEach { testCase ->
            val buffer = RollingBuffer(testCase.first) { 0 }
            val floatBuffer = RollingBuffer(testCase.first) { 0.0f }
            testCase.second.toList().forEach { buffer.insert(it); floatBuffer.insert(it.toFloat()) }

            val arr = Array(testCase.first) { 0 }
            buffer.cloneOnto(arr)

            val iterated = buffer.toList()

            assertArrayEquals(arr, iterated.toTypedArray())
            assertArrayEquals(arr, buffer.toArray())
            assertArrayEquals(
                getExpectedRange(testCase.second, buffer.size).toTypedArray(),
                buffer.toArray()
            )
            assertEquals(testCase.second.size().toLong(), buffer.written)
            assertArrayEquals(buffer.toArray(), floatBuffer.toArray().map { it.toInt() }.toTypedArray())
        }
    }

    /**
     * This test is needed as the array insert method works differently from the regular insert method,
     * as it copies over large chunks of data at once
     */
    @Test
    fun arrayInsertTest() {
        val testCases =
            listOf(50 to IntRange(0, 50), 50 to IntRange(0, 10), 50 to IntRange(0, 100), 50 to IntRange(0, -1), 50 to IntRange(0, 49))
        testCases.forEach { testCase ->
            val referenceBuffer = RollingBuffer(testCase.first) { 0 }
            testCase.second.toList().forEach { referenceBuffer.insert(it) }

            val arrayInsertBuffer = RollingBuffer(testCase.first) { 0 }
            arrayInsertBuffer.insert(testCase.second.toList().toTypedArray())

            assertArrayEquals(referenceBuffer.toArray(), arrayInsertBuffer.toArray())
            assertArrayEquals(
                getExpectedRange(testCase.second, referenceBuffer.size).toTypedArray(),
                referenceBuffer.toArray()
            )
            assertEquals(testCase.second.size().toLong(), referenceBuffer.written)
        }
    }

    private fun getExpectedRange(range: IntRange, bufSize: Int): MutableList<Int> {
        val expectedRange = range.toList()
            .slice(range.first.coerceAtLeast(range.size() - bufSize)..range.last)
            .toMutableList()
        for (i in 0..<bufSize - expectedRange.size) expectedRange.add(0, 0)
        return expectedRange
    }
}

fun IntRange.size(): Int {
    return this.last - this.first + 1
}