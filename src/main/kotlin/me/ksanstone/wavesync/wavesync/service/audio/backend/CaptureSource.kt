package me.ksanstone.wavesync.wavesync.service.audio.backend

import javafx.util.Duration
import me.ksanstone.wavesync.wavesync.service.audio.FourierMath
import java.text.NumberFormat
import kotlin.math.round

/**
 * Represents an audio source we can capture audio data from.
 */
interface CaptureSource {
    val name: String
    val id: String
    val sampleRate: Int
    val channels: Int
    val nativeFormat: NativeFormat

    fun getMaxFrequency(): Int {
        return FourierMath.maxFrequencyForRate(sampleRate)
    }

    fun trimResultTo(size: Int, frequency: Int): Int {
        return FourierMath.trimResultBufferTo(size, sampleRate, frequency)
    }

    fun getPropertyDescriptor(fftSize: Int, targetMin: Int, targetMax: Int, numberFormat: NumberFormat): String {
        val resultingSamples = trimResultTo(fftSize * 2, targetMax) - bufferBeginningSkipFor(targetMin, fftSize * 2)
        return "${numberFormat.format(sampleRate)}Hz • $nativeFormat • 🔈 x${channels} • ${
            numberFormat.format(
                fftSize
            )
        } [${
            numberFormat.format(
                resultingSamples
            )
        }] • ${numberFormat.format(targetMin)}Hz - ${numberFormat.format(targetMax)}Hz"
    }

    fun getMinimumFrequency(samples: Int): Int {
        val sampleT = samples.toDouble() / sampleRate
        return round(1.0 / sampleT).toInt()
    }

    fun bufferBeginningSkipFor(freq: Int, bufferSize: Int): Int {
        return FourierMath.trimResultBufferTo(bufferSize, sampleRate, freq)
    }

    /**
     * @param samples The number of samples we need to receive.
     * @return How much time is needed to receive this many samples.
     */
    fun getUpdateInterval(samples: Int): Duration {
        return Duration.seconds(1.0 / sampleRate * samples)
    }

    enum class NativeFormat(val bytesPerSample: Int) {
        UINT8(1),
        INT16(2),
        INT24(3),
        INT32(4),
        FLOAT32(4);
    }
}