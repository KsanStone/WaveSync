package me.ksanstone.wavesync.wavesync.service

import me.ksanstone.wavesync.wavesync.utility.RollingBuffer
import kotlin.math.*

object FourierMath {

    const val ALIGN_THRESHOLD = 0.0001 // -40 Db

    fun binOfFrequency(rate: Int, fftSize: Int, freq: Double): Int {
        val freqPerBin = rate.toDouble() / fftSize
        return floor(freq / freqPerBin).toInt().coerceIn(0, fftSize)
    }

    fun binOfFrequency(rate: Int, fftSize: Int, freq: Int): Int {
        val freqPerBin = rate.toDouble() / fftSize
        return floor(freq.toDouble() / freqPerBin).toInt().coerceIn(0, fftSize)
    }

    fun frequencyOfBin(rate: Int, fftSize: Int): Int {
        return frequencyOfBin(1, rate, fftSize)
    }

    fun frequencyOfBin(bin: Int, rate: Int, fftSize: Int): Int {
        return (bin * (rate.toDouble() / fftSize)).roundToInt()
    }

    fun frequencyOfBinD(bin: Int, rate: Int, fftSize: Int): Double {
        return bin * (rate.toDouble() / fftSize)
    }

    fun maxFrequencyForRate(rate: Int): Int {
        return rate / 2
    }

    fun trimResultBufferTo(fftSize: Int, rate: Int, frequency: Int): Int {
        val factor = rate.toDouble() / fftSize
        return min(ceil(frequency.toDouble() / factor).toInt(), fftSize / 2)
    }

    fun frequencySamplesAtRate(frequency: Double, rate: Int): Double {
        return 1.0 / frequency * rate
    }

    fun calcRMS(samples: FloatArray, frames: Int): Double {
        var s = 0.0
        for (i in 0 until frames) s += samples[i].toDouble().pow(2)
        return sqrt(s / samples.size)
    }

    fun calcRMS(samples: RollingBuffer<Float>, start: Int, frames: Int): Double {
        var s = 0.0
        for (i in start until start + frames) s += samples[i].toDouble().pow(2)
        return sqrt(s / samples.size)
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