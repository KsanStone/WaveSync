package me.ksanstone.wavesync.wavesync

import me.ksanstone.wavesync.wavesync.service.windowing.BlackmanHarrisWindowFunction
import me.ksanstone.wavesync.wavesync.service.windowing.HammingWindowFunction
import me.ksanstone.wavesync.wavesync.service.windowing.HannWindowFunction
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test

class TestWindows {

    @Test
    fun hammingWindowTest() {
        val len = 10
        val result = floatArrayOf(0.08f, 0.1876f, 0.4601f, 0.77f, 0.9723f, 0.9723f, 0.77f, 0.4601f, 0.1876f, 0.08f)
        val w1 = HammingWindowFunction(len)
        val out: FloatArray = w1.getWindow()
        assertArrayEquals(result, out, 0.0001f)
    }

    @Test
    fun hanningWindowTest() {
        val len = 10
        val result = floatArrayOf(0.0F, 0.117F, 0.4132F, 0.75F, 0.9698F, 0.9698F, 0.75F, 0.4132F, 0.117F, 0.0F)
        val w1 = HannWindowFunction(len)
        val out = w1.getWindow()
        assertArrayEquals(result, out, 0.0001f)
    }

    @Test
    fun blackmanHarrisWindowTest() {
        val len = 10
        val result = floatArrayOf(0.0001F, 0.0151F, 0.147F, 0.5206F, 0.9317F, 0.9317F, 0.5206F, 0.147F, 0.0151F, 0.0001F)
        val w1 = BlackmanHarrisWindowFunction(len)
        val out = w1.getWindow()
        assertArrayEquals(result, out, 0.0001f)
    }
}