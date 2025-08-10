package me.ksanstone.wavesync.wavesync.service.audio.backend

import javafx.beans.property.ObjectProperty
import javafx.beans.property.ReadOnlyBooleanProperty
import me.ksanstone.wavesync.wavesync.utility.ChannelLabel

/**
 * Talks to {@link AudioSystem}s, and marshalls samples.
 */
interface AudioBackend {

    val captureRunning: ReadOnlyBooleanProperty
    val currentAudioSystem: ObjectProperty<AudioSystem?>

    fun detectSupportedAudioSystems(): List<AudioSystem>

    fun detectSupportedCaptureSources(): List<CaptureSource>

    fun findDefaultCaptureSource(devices: List<CaptureSource>): CaptureSource?

    fun setSampleProcessor(processor: SampleProcessor)

    fun startCapture(source: CaptureSource, preCaptureCallback: PreCaptureCallback)

    fun stopCapture()

    fun interface SampleProcessor {
        fun process(samples: FloatArray, frames: Int)
    }

    fun interface PreCaptureCallback {
        fun onPreCapture(frames: Int, channelLabels: List<ChannelLabel>)
    }

}