package me.ksanstone.wavesync.wavesync.service

import com.sun.jna.Platform
import com.sun.jna.Pointer
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import javafx.beans.property.IntegerProperty
import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleObjectProperty
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.FFT_SIZE
import me.ksanstone.wavesync.wavesync.service.windowing.HammingWindowFunction
import me.ksanstone.wavesync.wavesync.service.windowing.WindowFunction
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import xt.audio.Enums.*
import xt.audio.Structs.*
import xt.audio.XtAudio
import xt.audio.XtSafeBuffer
import xt.audio.XtStream
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.function.BiConsumer


@Service
class AudioCaptureService(
    private val preferenceService: PreferenceService
) {

    private val logger: Logger = LoggerFactory.getLogger("AudioCaptureService")

    private lateinit var bufferArray: ByteArray
    private lateinit var sampleBufferArray: FloatArray
    private var sampleBufferArrayIndex: Int = 0
    private var currentStream: XtStream? = null
    private var lock: CountDownLatch = CountDownLatch(0)
    private var recordingFuture: CompletableFuture<Void>? = null
    private var fftObservers: MutableList<BiConsumer<FloatArray, SupportedCaptureSource>> = mutableListOf()
    private var sampleObservers: MutableList<BiConsumer<FloatArray, SupportedCaptureSource>> = mutableListOf()
    private var windowFunction: WindowFunction? = null

    lateinit var fftResult: FloatArray
    lateinit var samples: FloatArray

    val source: ObjectProperty<SupportedCaptureSource> = SimpleObjectProperty()
    val fftSize: IntegerProperty = SimpleIntegerProperty(FFT_SIZE)
    val usedAudioSystem: ObjectProperty<XtSystem> = SimpleObjectProperty()
    var audioSystems: List<XtSystem> = listOf()

    @PostConstruct
    fun registerProperties() {
        preferenceService.registerProperty(fftSize, "fftSize")
        preferenceService.registerProperty(usedAudioSystem, "audioSystem", XtSystem::class.java)
        detectSupportedAudioSystems()
        if (usedAudioSystem.get() == null) {
            if (Platform.isWindows() && audioSystems.contains(XtSystem.WASAPI)) {
                usedAudioSystem.set(XtSystem.WASAPI)
            }
        }
        if (!audioSystems.contains(usedAudioSystem.get())) {
            usedAudioSystem.set(null)
            logger.warn("No audio system selected")
        }

        usedAudioSystem.addListener { _ -> stopCapture() }
    }

    fun detectSupportedAudioSystems() {
        XtAudio.init(null, Pointer.NULL).use { platform ->
            audioSystems = platform.systems.toList()
        }
    }

    fun onBuffer(stream: XtStream, buffer: XtBuffer, user: Any?): Int {
        val safe = XtSafeBuffer.get(stream) ?: return 0
        safe.lock(buffer)

        val audio = safe.input as FloatArray
        processAudio(audio, buffer.frames)

        safe.unlock(buffer)
        return 0
    }

    fun processAudio(audio: FloatArray, frames: Int) {
        val channels = source.get().format.channels.inputs
        val sampleFactor = 1.0f / channels.toFloat()
        for (frame in 0 until frames) {
            val sampleIndex = frame * 2
            var sample = 0.0f
            for (channel in 0 until channels) {
                sample += audio[sampleIndex + channel] * sampleFactor
            }
            sampleBufferArray[sampleBufferArrayIndex++] = sample
            samples[frame] = sample
            if (sampleBufferArrayIndex == sampleBufferArray.size) {
                doFFT(sampleBufferArray, source.get().format.mix.rate)
                sampleBufferArrayIndex = 0
            }
        }
        sampleObservers.forEach { it.accept(samples.sliceArray(0 until frames), source.get()) }
    }

    fun doFFT(samples: FloatArray, rate: Int) {
        windowFunction!!.applyFunction(samples)
        val imag = FloatArray(samples.size)
        FourierMath.transform(1, samples.size, samples, imag)
        FourierMath.calculateMagnitudes(samples, imag, fftResult)
        fftObservers.forEach { it.accept(fftResult, source.get()) }
    }

    fun registerFFTObserver(observer: BiConsumer<FloatArray, SupportedCaptureSource>) {
        fftObservers.add(observer)
    }

    fun registerSampleObserver(observer: BiConsumer<FloatArray, SupportedCaptureSource>) {
        sampleObservers.add(observer)
    }

    fun startCapture(source: SupportedCaptureSource) {
        this.source.set(source)
        recordingFuture = CompletableFuture.runAsync {
            lock = CountDownLatch(1)
            XtAudio.init(null, Pointer.NULL).use { platform ->
                val service = platform.getService(usedAudioSystem.get())
                logger.info("Selected device $source")
                service.openDevice(source.id).use {
                    val device = it
                    val format = source.format

                    val bufferSize: XtBufferSize = device.getBufferSize(format)
                    val streamParams = XtStreamParams(true, this::onBuffer, null, null)
                    val deviceParams = XtDeviceStreamParams(streamParams, format, bufferSize.current)

                    setScanWindowSize(fftSize.get())
                    val deviceStream = device.openStream(deviceParams, null)
                    deviceStream.use { stream ->
                        logger.info("Stream opened")
                        this.currentStream = stream
                        XtSafeBuffer.register(stream).use { _ ->
                            bufferArray = ByteArray(
                                stream.frames * format.channels.inputs * XtAudio.getSampleAttributes(format.mix.sample).size
                            )
                            samples = FloatArray(stream.frames)
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

    fun restartCapture() {
        if (source.get() != null) {
            stopCapture()
            startCapture(source.get())
        }
    }

    fun changeSource(source: SupportedCaptureSource) {
        if (source == this.source.get()) return
        stopCapture()
        startCapture(source)
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

    private fun extractDeviceUUID(deviceId: String): String {
        return deviceId.split("}.{").getOrElse(1) { return deviceId }
    }

    fun findSimilarAudioSource(device: String, devices: List<SupportedCaptureSource>): SupportedCaptureSource? {
        val extractedId = extractDeviceUUID(device)
        return devices.find { extractDeviceUUID(it.id) == extractedId }
    }

    fun findDefaultAudioSource(devices: List<SupportedCaptureSource>): SupportedCaptureSource? {
        XtAudio.init(null, Pointer.NULL).use { platform ->
            val service = platform.getService(usedAudioSystem.get())
            val device = service.getDefaultDeviceId(true) ?: return null
            val extractedId = extractDeviceUUID(device)
            return devices.find { extractDeviceUUID(it.id) == extractedId }
        }
    }

    fun findSupportedSources(): List<SupportedCaptureSource> {
        val supported = ArrayList<SupportedCaptureSource>()
        XtAudio.init(null, Pointer.NULL).use { platform ->
            val service = platform.getService(usedAudioSystem.get())
            try {
                service.openDeviceList(EnumSet.of(XtEnumFlags.INPUT)).use { list ->
                    for (i in 0 until list.count) {
                        val deviceId = list.getId(i)
                        val caps = list.getCapabilities(deviceId)
                        if (caps.contains(XtDeviceCaps.LOOPBACK) || caps.contains(XtDeviceCaps.INPUT)) {
                            val deviceName = list.getName(deviceId)
                            try {
                                service.openDevice(deviceId).use { device ->
                                    val deviceMix = device.mix.orElse(XtMix(192000, XtSample.FLOAT32))
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
                logger.error("INIT FAILED")
            }
        }
        return supported
    }
}