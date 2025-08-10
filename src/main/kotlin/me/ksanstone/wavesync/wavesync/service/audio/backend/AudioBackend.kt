package me.ksanstone.wavesync.wavesync.service.audio.backend

import javafx.beans.property.ObjectProperty
import javafx.beans.property.ReadOnlyBooleanProperty

/**
 * Talks to {@link AudioSystem}s, and marshalls samples.
 */
interface AudioBackend {

    val captureRunning: ReadOnlyBooleanProperty
    val currentAudioSystem: ObjectProperty<out AudioSystem?>

    fun detectSupportedAudioSystems(): List<AudioSystem>

    fun detectSupportedCaptureSources(): List<CaptureSource>

    fun findDefaultCaptureSource(devices: List<CaptureSource>): CaptureSource?

    fun setSampleProcessor(processor: SampleProcessor)

    fun startCapture(source: CaptureSource, preCaptureCallback: PreCaptureCallback)

    fun stopCapture()

    @FunctionalInterface
    interface SampleProcessor {
        fun process(samples: FloatArray, frames: Int)
    }

    @FunctionalInterface
    interface PreCaptureCallback {
        fun onPreCapture(frames: Int)
    }

}