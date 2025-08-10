package me.ksanstone.wavesync.wavesync.service.audio.backend.xt

import me.ksanstone.wavesync.wavesync.service.audio.backend.CaptureSource
import xt.audio.Enums
import xt.audio.Structs
import xt.audio.XtDevice

data class XtCaptureSource(
    val device: XtDevice,
    val format: Structs.XtFormat,
    override val name: String,
    override val id: String,
) : CaptureSource {

    override val sampleRate: Int
        get() = format.mix.rate

    override val channels: Int
        get() = format.channels.inputs

    override val nativeFormat: CaptureSource.NativeFormat
        get() = when (format.mix.sample) {
            Enums.XtSample.FLOAT32 -> CaptureSource.NativeFormat.FLOAT32
            Enums.XtSample.INT16 -> CaptureSource.NativeFormat.INT16
            Enums.XtSample.INT24 -> CaptureSource.NativeFormat.INT24
            Enums.XtSample.INT32 -> CaptureSource.NativeFormat.INT32
            Enums.XtSample.UINT8 -> CaptureSource.NativeFormat.UINT8
            else -> CaptureSource.NativeFormat.FLOAT32
        }


    override fun toString(): String {
        return "SupportedCaptureSource { name: $name id: $id freq: ${format.mix.rate} channel: ${format.channels.inputs} format: ${format.mix.sample} }"
    }

}