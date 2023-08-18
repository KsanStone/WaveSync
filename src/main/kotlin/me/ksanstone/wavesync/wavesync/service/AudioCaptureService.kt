package me.ksanstone.wavesync.wavesync.service

import com.sun.jna.Pointer
import jakarta.annotation.PreDestroy
import me.ksanstone.wavesync.wavesync.service.windowing.HammingWindowFunction
import me.ksanstone.wavesync.wavesync.service.windowing.WindowFunction
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import xt.audio.Enums.*
import xt.audio.Structs.*
import xt.audio.XtAudio
import xt.audio.XtSafeBuffer
import xt.audio.XtStream
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.function.BiConsumer


@Component
class AudioCaptureService {

    private val logger: Logger = LoggerFactory.getLogger("AudioCaptureService")

    private lateinit var bufferArray: ByteArray
    private lateinit var sampleBufferArray: FloatArray
    private var sampleBufferArrayIndex: Int = 0
    private var currentStream: XtStream? = null
    private var source: SupportedCaptureSource? = null
    private var lock: CountDownLatch = CountDownLatch(0)
    private var recordingFuture: CompletableFuture<Void>? = null
    private var fftObservers: MutableList<BiConsumer<FloatArray, SupportedCaptureSource>> = mutableListOf()
    private var windowFunction: WindowFunction? = null

    lateinit var fftResult: FloatArray

    fun onBuffer(stream: XtStream, buffer: XtBuffer, user: Any?): Int {
        val safe = XtSafeBuffer.get(stream) ?: return 0
        safe.lock(buffer)

        val audio = safe.input as FloatArray
        processAudio(audio, buffer.frames)

        safe.unlock(buffer)
        return 0
    }

    fun processAudio(audio: FloatArray, frames: Int) {
        val channels = source!!.format.channels.inputs
        val sampleFactor = 1.0f / channels.toFloat()
        for (frame in 0 until frames) {
            val sampleIndex = frame * 2
            var sample = 0.0f
            for (channel in 0 until channels) {
                sample += audio[sampleIndex + channel] * sampleFactor
            }
            sampleBufferArray[sampleBufferArrayIndex++] = audio[sampleIndex]
            if (sampleBufferArrayIndex == sampleBufferArray.size) {
                doFFT(sampleBufferArray, source!!.format.mix.rate)
                sampleBufferArrayIndex = 0
            }
        }

    }

    fun doFFT(samples: FloatArray, rate: Int) {
        windowFunction!!.applyFunction(samples)
        val imag = FloatArray(samples.size)
        FourierMath.transform(1, samples.size, samples, imag)
        FourierMath.calculateMagnitudes(samples, imag, fftResult)
        fftObservers.forEach { it.accept(fftResult, source!!) }
    }

    fun registerObserver(observer: BiConsumer<FloatArray, SupportedCaptureSource>) {
        fftObservers.add(observer)
    }

    fun startCapture(source: SupportedCaptureSource) {
        recordingFuture = CompletableFuture.runAsync {
            lock = CountDownLatch(1)
            XtAudio.init(null, Pointer.NULL).use { platform ->
                val service = platform.getService(XtSystem.WASAPI)
                logger.info("Selected device $source")
                service.openDevice(source.id).use {
                    val device = it
                    val format = source.format

                    val bufferSize: XtBufferSize = device.getBufferSize(format)
                    val streamParams = XtStreamParams(true, this::onBuffer, null, null)
                    val deviceParams = XtDeviceStreamParams(streamParams, format, bufferSize.current)

                    this.source = source
                    setScanWindowSize(source.getMinimumSamples(100).closestPowerOf2())
                    val deviceStream = device.openStream(deviceParams, null)
                    deviceStream.use { stream ->
                        logger.info("Stream opened")
                        this.currentStream = stream
                        XtSafeBuffer.register(stream).use { safeBuffer ->
                            bufferArray = ByteArray(
                                stream.getFrames() * format.channels.inputs * XtAudio.getSampleAttributes(format.mix.sample).size
                            )
                            stream.start()
                            logger.info("Capture started")
                            lock.await()
                            stream.stop()
                            logger.info("Capture finished")
                        }
                    }
                }
            }
        }
    }

    fun stopCapture() {
        if (recordingFuture != null) {
            logger.info("Stopping capture")
            lock.countDown()
            recordingFuture?.get()
            recordingFuture = null
        }
    }

    fun setScanWindowSize(size: Int) {
        logger.info("Using window size $size")
        sampleBufferArray = FloatArray(size)
        sampleBufferArrayIndex = 0
        fftResult = FloatArray(size / 2)
        windowFunction = HammingWindowFunction(size)
    }

    @PreDestroy
    fun cleanup() {
        stopCapture()
    }

    companion object {
        fun findSupportedSources(): List<SupportedCaptureSource> {
            val supported = ArrayList<SupportedCaptureSource>()
            XtAudio.init(null, Pointer.NULL).use { platform ->
                val service = platform.getService(XtSystem.WASAPI)
                try {
                    service.openDeviceList(EnumSet.of<XtEnumFlags>(XtEnumFlags.INPUT)).use { list ->
                        for (i in 0 until list.getCount()) {
                            val deviceId = list.getId(i)
                            val caps = list.getCapabilities(deviceId)
                            if (caps.contains(XtDeviceCaps.LOOPBACK)) {
                                val deviceName = list.getName(deviceId)
                                try {
                                    service.openDevice(deviceId).use { device ->
                                        val deviceMix = device.getMix().orElse(XtMix(192000, XtSample.FLOAT32))
                                        val inChannelCount = device.getChannelCount(false)
                                        var channels: XtChannels
                                        var format: XtFormat?
                                        var supportedChannels = 1
                                        while (supportedChannels < inChannelCount) {
                                            channels = XtChannels(supportedChannels, 0, 0, 0)
                                            format = XtFormat(deviceMix, channels)
                                            if (device.supportsFormat(format)) {
                                                supported.add(
                                                    SupportedCaptureSource(
                                                        device,
                                                        format,
                                                        deviceName,
                                                        deviceId
                                                    )
                                                )
                                                break
                                            }
                                            supportedChannels++
                                        }
                                    }
                                } catch (e: Exception) {
                                    println(deviceName + " FAIL " + e.message)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    println("INIT FAILED")
                }
            }
            return supported
        }
    }
}