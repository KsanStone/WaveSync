package me.ksanstone.wavesync.wavesync.service.audio.backend.java

import me.ksanstone.wavesync.wavesync.service.audio.backend.CaptureSource
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.Mixer

class JavaCaptureSource(
    val format: AudioFormat,
    val mixerInfo: Mixer.Info,
    override val name: String,
    override val id: String,
) : CaptureSource {

    override val sampleRate: Int
        get() = format.sampleRate.toInt()

    override val channels: Int
        get() = format.channels

    // TODO figure out the format from the audio format
    override val nativeFormat: CaptureSource.NativeFormat
        get() = CaptureSource.NativeFormat.FLOAT32

}