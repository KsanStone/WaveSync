package me.ksanstone.wavesync.wavesync.service

import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object FourierMath {
    private const val MAX_SIZE_LOG_2 = 16
    private var reverseTables = arrayOfNulls<BitReverseTable>(MAX_SIZE_LOG_2)
    private var floatSineTables = arrayOfNulls<FloatSineTable>(MAX_SIZE_LOG_2)

    private fun getFloatSineTable(n: Int): FloatArray {
        var sineTable = floatSineTables[n]
        if (sineTable == null) {
            sineTable = FloatSineTable(n)
            floatSineTables[n] = sineTable
        }
        return sineTable.sineValues
    }

    private fun getReverseTable(n: Int): IntArray {
        var reverseTable = reverseTables[n]
        if (reverseTable == null) {
            reverseTable = BitReverseTable(n)
            reverseTables[n] = reverseTable
        }
        return reverseTable.reversedBits
    }

    /**
     * Calculate the amplitude of the sine wave associated with each bin of a complex FFT result.
     *
     * @param ar
     * @param ai
     * @param magnitudes
     */
    fun calculateMagnitudes(ar: FloatArray, ai: FloatArray, magnitudes: FloatArray) {
        for (i in magnitudes.indices) {
            magnitudes[i] = sqrt((ar[i] * ar[i] + ai[i] * ai[i]).toDouble()).toFloat()
        }
    }

    fun transform(sign: Int, n: Int, ar: FloatArray, ai: FloatArray) {
        val scale = if (sign > 0) 2.0f / n else 0.5f
        val numBits = numBits(n)
        val reverseTable = getReverseTable(numBits)
        val sineTable = getFloatSineTable(numBits)
        val mask = n - 1
        val cosineOffset = n / 4 // phase offset between cos and sin
        var i: Int
        var j: Int
        i = 0
        while (i < n) {
            j = reverseTable[i]
            if (j >= i) {
                val tempr = ar[j] * scale
                val tempi = ai[j] * scale
                ar[j] = ar[i] * scale
                ai[j] = ai[i] * scale
                ar[i] = tempr
                ai[i] = tempi
            }
            i++
        }
        var mmax: Int
        var stride: Int
        val numerator = sign * n
        mmax = 1
        stride = 2 * mmax
        while (mmax < n) {
            var phase = 0
            val phaseIncrement = numerator / (2 * mmax)
            for (m in 0 until mmax) {
                val wr = sineTable[phase + cosineOffset and mask] // cosine
                val wi = sineTable[phase]
                i = m
                while (i < n) {
                    j = i + mmax
                    val tr = wr * ar[j] - wi * ai[j]
                    val ti = wr * ai[j] + wi * ar[j]
                    ar[j] = ar[i] - tr
                    ai[j] = ai[i] - ti
                    ar[i] += tr
                    ai[i] += ti
                    i += stride
                }
                phase = phase + phaseIncrement and mask
            }
            mmax = stride
            stride = 2 * mmax
        }
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

    /**
     * Calculate log2(n)
     *
     * @param powerOf2 must be a power of two, for example, 512 or 1024
     * @return for example, 9 for an input value of 512
     */
    private fun numBits(powerOf2: Int): Int {
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

    class FloatSineTable internal constructor(numBits: Int) {
        var sineValues: FloatArray

        init {
            val len = 1 shl numBits
            sineValues = FloatArray(1 shl numBits)
            for (i in 0 until len) {
                sineValues[i] = sin(i * Math.PI * 2.0 / len).toFloat()
            }
        }
    }

    class BitReverseTable internal constructor(numBits: Int) {
        var reversedBits: IntArray

        init {
            reversedBits = IntArray(1 shl numBits)
            for (i in reversedBits.indices) {
                reversedBits[i] = reverseBits(i, numBits)
            }
        }

        companion object {
            fun reverseBits(index: Int, numBits: Int): Int {
                var index1 = index
                var i: Int
                var rev: Int
                i = 0.also { rev = it }
                while (i < numBits) {
                    rev = rev shl 1 or (index1 and 1)
                    index1 = index1 shr 1
                    i++
                }
                return rev
            }
        }
    }
}