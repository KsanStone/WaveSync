package me.ksanstone.wavesync.wavesync.service.audio.backend.xt

import com.sun.jna.Pointer
import javafx.beans.property.ObjectProperty
import javafx.beans.property.ReadOnlyBooleanProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleObjectProperty
import me.ksanstone.wavesync.wavesync.service.audio.backend.AudioBackend
import me.ksanstone.wavesync.wavesync.service.audio.backend.AudioSystem
import me.ksanstone.wavesync.wavesync.service.audio.backend.CaptureSource
import me.ksanstone.wavesync.wavesync.utility.ChannelLabel
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import xt.audio.Enums
import xt.audio.Structs
import xt.audio.XtAudio
import xt.audio.XtSafeBuffer
import xt.audio.XtStream
import java.util.ArrayList
import java.util.EnumSet
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import kotlin.use

@Service
class XtAudioBackend : AudioBackend {
    private val logger: Logger = LoggerFactory.getLogger(javaClass)

    private lateinit var processor: AudioBackend.SampleProcessor
    private var recordingFuture: CompletableFuture<Void>? = null
    private val _captureRunning = SimpleBooleanProperty(false)
    private var lock: CountDownLatch = CountDownLatch(0)

    override val captureRunning: ReadOnlyBooleanProperty =
        ReadOnlyBooleanProperty.readOnlyBooleanProperty(_captureRunning)
    override val currentAudioSystem: ObjectProperty<XtAudioSystem?> = SimpleObjectProperty()

    override fun detectSupportedAudioSystems(): List<AudioSystem> {
        XtAudio.init(null, Pointer.NULL).use { platform ->
            return platform.systems.map { XtAudioSystem(it) }.toList()
        }
    }

    override fun detectSupportedCaptureSources(): List<CaptureSource> {
        val supported = ArrayList<XtCaptureSource>()
        try {
            XtAudio.init(null, Pointer.NULL).use { platform ->
                val service = platform.getService(currentAudioSystem.get()!!.system)
                try {
                    service.openDeviceList(EnumSet.of(Enums.XtEnumFlags.ALL)).use { list ->
                        for (i in 0 until list.count) {
                            val deviceId = list.getId(i)
                            val caps = list.getCapabilities(deviceId)
                            if (caps.contains(Enums.XtDeviceCaps.LOOPBACK) || caps.contains(Enums.XtDeviceCaps.INPUT)) {
                                val deviceName = list.getName(deviceId)
                                try {
                                    service.openDevice(deviceId).use { device ->
                                        val deviceMix = device.mix.orElse(Structs.XtMix(192000, Enums.XtSample.FLOAT32))
                                        val inChannelCount = device.getChannelCount(false)
                                        var channels: Structs.XtChannels
                                        var format: Structs.XtFormat?
                                        var supportedChannels = 1
                                        while (supportedChannels < inChannelCount) {
                                            channels = Structs.XtChannels(supportedChannels, 0, 0, 0)
                                            format = Structs.XtFormat(deviceMix, channels)
                                            if (device.supportsFormat(format)) {
                                                supported.add(
                                                    XtCaptureSource(
                                                        device,
                                                        format,
                                                        deviceName,
                                                        deviceId
                                                    )
                                                )
                                                logger.info("Detected: ${supported[supported.size - 1]}")
                                                break
                                            }
                                            supportedChannels++
                                        }
                                    }
                                } catch (e: Exception) {
                                    logger.error(deviceName + "FAIL " + e.message)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    logger.error("INIT FAILED", e)
                }
            }
        } catch (e: AssertionError) {
            logger.error("Audio query FAIL", e)
        }
        return supported
    }

    /**
     * This implementation is windows only
     */
    private fun extractDeviceUUID(deviceId: String): String {
        return deviceId.split("}.{").getOrElse(1) { return deviceId }
    }

    override fun findDefaultCaptureSource(devices: List<CaptureSource>): CaptureSource? {
        try {
            XtAudio.init(null, Pointer.NULL).use { platform ->
                val service = platform.getService(currentAudioSystem.get()!!.system)
                val device = service.getDefaultDeviceId(true) ?: return null
                val extractedId = extractDeviceUUID(device)
                return devices.find { extractDeviceUUID(it.id) == extractedId }
            }
        } catch (e: AssertionError) {
            logger.error("Default device query FAIL", e)
        } catch (_: NullPointerException) {
        }
        return null
    }

    override fun setSampleProcessor(processor: AudioBackend.SampleProcessor) {
        this.processor = processor
    }

    override fun startCapture(source: CaptureSource, preCaptureCallback: AudioBackend.PreCaptureCallback) {
        val xtSource = source as XtCaptureSource
        recordingFuture = CompletableFuture.runAsync {
            lock = CountDownLatch(1)
            XtAudio.init(null, Pointer.NULL).use { platform ->
                val service = platform.getService(currentAudioSystem.get()!!.system)
                logger.info("Selected device $xtSource")
                service.openDevice(xtSource.id).use { device ->
                    val format = xtSource.format
                    format.mix.sample = Enums.XtSample.FLOAT32

                    val bufferSize: Structs.XtBufferSize = device.getBufferSize(format)
                    val streamParams = Structs.XtStreamParams(true, this::onBuffer, null, null)
                    val deviceParams = Structs.XtDeviceStreamParams(streamParams, format, bufferSize.current)

                    val channels = format.channels.inputs
                    val rate = deviceParams.format.mix.rate
                    val sample = deviceParams.format.mix.sample
                    val channelLabels =
                        (0 until channels).map { idx ->
                            ChannelLabel.Companion.resolve(
                                device.getChannelName(
                                    false,
                                    idx
                                )
                            )
                        }

                    val deviceStream = device.openStream(deviceParams, null)
                    deviceStream.use { stream ->
                        logger.info("Stream opened Input latency ${stream.latency.input}")
                        logger.info("Channels: $channelLabels")
                        XtSafeBuffer.register(stream).use { _ ->
                            preCaptureCallback.onPreCapture(stream.frames)
//                            setScanWindowSize(fftSize.get())
//                            pcmDataBuffer = ByteArray(
//                                stream.frames * channels * XtAudio.getSampleAttributes(sample).size
//                            )
//                            samples.resize(1 + channels, stream.frames).label(*defaultChannelLabels.plus(channelLabels))
//                            channelVolumes.resize(1 + channels, 1).label(*defaultChannelLabels.plus(channelLabels))
//                            fftSampleBuffer.resize(1 + channels, fftSize.get())
//                                .label(*defaultChannelLabels.plus(channelLabels))
//                            setScanWindowSize(fftSize.get())
//                            updateLabels()
                            logger.info("Capture started, capturing master + $channels channels @ ${rate}Hz $sample")
                            _captureRunning.set(true)
                            stream.start()
                            lock.await()
                            stream.stop()
                            logger.info("Capture finished")
                            _captureRunning.set(false)
                        }
                    }
                }
            }
        }
        recordingFuture!!.exceptionally { _ -> _captureRunning.set(false); return@exceptionally null }
    }

    override fun stopCapture() {
        if (recordingFuture != null) {
            logger.info("Stopping capture")
            lock.countDown()
            recordingFuture?.get()
            recordingFuture = null
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun onBuffer(stream: XtStream, buffer: Structs.XtBuffer, user: Any?): Int {
        val safe = XtSafeBuffer.get(stream) ?: return 0
        safe.lock(buffer)

        processor.process(marshalSamples(safe.input, stream.format), buffer.frames)

        safe.unlock(buffer)
        return 0
    }

    private fun marshalSamples(samples: Any?, format: Structs.XtFormat): FloatArray {
        return samples as FloatArray
    }

}