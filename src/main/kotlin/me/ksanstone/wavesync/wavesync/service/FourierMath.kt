package me.ksanstone.wavesync.wavesync.service

import kotlin.math.ceil
import kotlin.math.roundToInt

object FourierMath {

    fun frequencyOfBin(rate: Int, fftSize: Int): Int {
        return frequencyOfBin(1, rate, fftSize)
    }

    fun frequencyOfBin(bin: Int, rate: Int, fftSize: Int): Int {
        return (bin * (rate.toDouble() / fftSize)).roundToInt()
    }

    fun maxFrequencyForRate(rate: Int): Int {
        return rate / 2
    }

    fun trimResultBufferTo(fftSize: Int, rate: Int, frequency: Int): Int {
        val factor = rate.toDouble() / fftSize.toDouble()
        return ceil(frequency.toDouble() / factor).toInt().coerceAtMost(fftSize / 2)
    }

    fun frequencySamplesAtRate(frequency: Double, rate: Int): Double {
        return 1.0 / frequency * rate
    }

    /**
     * Calculate log2(n)
     *
     * @param powerOf2 must be a power of two, for example, 512 or 1024
     * @return for example, 9 for an input value of 512
     */
    fun numBitsWhile(powerOf2: Int): Int {
        var of2 = powerOf2
        assert(
            of2 and of2 - 1 == 0 // is it a power of 2?
        )
        var i: Int = -1
        while (of2 > 0) {
            of2 = of2 shr 1
            i++
        }
        return i
    }

    fun log2nlz(bits: Int): Int {
        if (bits == 0) return 0

        return 31 - Integer.numberOfLeadingZeros(bits)
    }

    fun binlog(bits: Int): Int {
        var bits = bits
        var log = 0
        if ((bits and -0x10000) != 0) {
            bits = bits ushr 16
            log = 16
        }
        if (bits >= 256) {
            bits = bits ushr 8
            log += 8
        }
        if (bits >= 16) {
            bits = bits ushr 4
            log += 4
        }
        if (bits >= 4) {
            bits = bits ushr 2
            log += 2
        }
        return log + (bits ushr 1)
    }
}