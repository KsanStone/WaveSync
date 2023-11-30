package me.ksanstone.wavesync.wavesync.service

import com.sun.jna.Platform
import com.sun.jna.Pointer
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import javafx.beans.property.*
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.DEFAULT_UPSAMPLING
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.DEFAULT_WINDOWING_FUNCTION
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.FFT_SIZE
import me.ksanstone.wavesync.wavesync.service.FourierMath.frequencyOfBin
import me.ksanstone.wavesync.wavesync.service.windowing.*
import me.ksanstone.wavesync.wavesync.utility.CommonChannel
import me.ksanstone.wavesync.wavesync.utility.FloatChanneledStore
import me.ksanstone.wavesync.wavesync.utility.RollingBuffer
import me.ksanstone.wavesync.wavesync.utility.toFloatArray
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
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt


@Service
class AudioCaptureService(
    private val preferenceService: PreferenceService
) {

    private val logger: Logger = LoggerFactory.getLogger("AudioCaptureService")

    private lateinit var pcmDataBuffer: ByteArray
    private lateinit var fftSampleBuffer: RollingBuffer<Float>
    private var currentStream: XtStream? = null
    private var lock: CountDownLatch = CountDownLatch(0)
    private var recordingFuture: CompletableFuture<Void>? = null
    private var fftObservers: MutableList<BiConsumer<FloatArray, SupportedCaptureSource>> = mutableListOf()
    private var sampleObservers: MutableList<BiConsumer<FloatArray, SupportedCaptureSource>> = mutableListOf()
    private var windowFunction: WindowFunction? = null

    val fftResult = FloatChanneledStore()
    val samples = FloatChanneledStore()

    val peakFrequency = SimpleDoubleProperty(0.0)
    val peakValue = SimpleFloatProperty(0.0f)
    val channelVolumes = FloatChanneledStore()

    val source: ObjectProperty<SupportedCaptureSource> = SimpleObjectProperty()
    val fftSize: IntegerProperty = SimpleIntegerProperty(FFT_SIZE)
    val fftUpsample: IntegerProperty = SimpleIntegerProperty(DEFAULT_UPSAMPLING)
    val usedAudioSystem: ObjectProperty<XtSystem> = SimpleObjectProperty()
    val usedWindowingFunction: ObjectProperty<WindowFunctionType> = SimpleObjectProperty(DEFAULT_WINDOWING_FUNCTION)
    var audioSystems: List<XtSystem> = listOf()

    private val channelLabels = arrayOf(CommonChannel.MASTER, CommonChannel.LEFT, CommonChannel.RIGHT)


    @PostConstruct
    fun registerProperties() {
        preferenceService.registerProperty(fftSize, "fftSize", this.javaClass)
        preferenceService.registerProperty(usedAudioSystem, "audioSystem", XtSystem::class.java, this.javaClass)
        preferenceService.registerProperty(
            usedWindowingFunction,
            "windowingFunction",
            WindowFunctionType::class.java,
            this.javaClass
        )
        preferenceService.registerProperty(fftUpsample, "fftUpsample", this.javaClass)

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
        usedAudioSystem.addListener { _ -> changeWindowingFunction() }
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
        marshalSamples(audio, buffer.frames)

        safe.unlock(buffer)
        return 0
    }

    fun marshalSamples(audio: FloatArray, frames: Int) {
        val channels = source.get().format.channels.inputs
        val sampleFactor = 1.0f / channels.toFloat()
        val targetSamplesUntilRefresh = (fftSampleBuffer.size / fftUpsample.get()).toLong()
        for (frame in 0 until frames) {
            val sampleIndex = frame * channels
            var sample = 0.0f
            for (channel in 0 until channels) {
                sample += audio[sampleIndex + channel] * sampleFactor
                samples[1 + channel].data[frame] = audio[sampleIndex + channel]
            }
            samples[0].data[frame] = sample
            fftSampleBuffer.insert(sample)
            if (fftSampleBuffer.written % targetSamplesUntilRefresh == 0L) {
                doFFT(fftSampleBuffer.toFloatArray(), source.get().format.mix.rate)
            }
        }
        processSamples(frames)
    }

    fun processSamples(frames: Int) {
        doLoudnessCalc(frames)
        val sampleSlice = samples[0].data.sliceArray(0 until frames)
        sampleObservers.forEach { it.accept(sampleSlice, source.get()) }
    }

    private fun calcRMS(samples: FloatArray, frames: Int): Double {
        var s = 0.0
        for (i in 0 until frames) s += samples[i].toDouble().pow(2)
        return sqrt(s / samples.size)
    }

    fun doLoudnessCalc(frames: Int) {
        for (i in 0 until samples.channels()) {
            channelVolumes[i].data[0] = (20 * log10(calcRMS(samples[i].data, frames))).toFloat()
        }
        channelVolumes.fireDataChanged()
    }

    fun doFFT(samples: FloatArray, rate: Int) {
        windowFunction!!.applyFunction(samples)
        val imag = FloatArray(samples.size)
        FourierMath.transform(1, samples.size, samples, imag, windowFunction!!.getSum())
        FourierMath.calculateMagnitudes(samples, imag, fftResult[0].data)
        calcPeak(fftResult[0].data)
        fftObservers.forEach { it.accept(fftResult[0].data, source.get()) }
    }

    private fun calcPeak(fftResult: FloatArray) {
        val maxIdx = fftResult.indices.maxBy { fftResult[it] }
        if (maxIdx == -1) return

        val peakV = fftResult[maxIdx]

        if (peakV < 0.001f) return

        val n1 = (fftResult.getOrNull(maxIdx - 1) ?: 0.0f).toDouble()
        val n2 = (fftResult.getOrNull(maxIdx + 1) ?: 0.0f).toDouble()

        val n1r = n1 / peakV
        val n2r = n2 / peakV

        val factor = n2r - n1r
        var offset = frequencyOfBin(source.get().format.mix.rate, fftResult.size * 2) * factor
        offset = offset.coerceIn(-1.0, 1.0)

        peakFrequency.value =
            frequencyOfBin(maxIdx, source.get().format.mix.rate, fftResult.size * 2).toDouble() + offset
        peakValue.value = peakV
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
                        val channels = format.channels.inputs
                        val rate = deviceParams.format.mix.rate
                        val sample = deviceParams.format.mix.sample
                        this.currentStream = stream
                        XtSafeBuffer.register(stream).use { _ ->
                            pcmDataBuffer = ByteArray(
                                stream.frames * channels * XtAudio.getSampleAttributes(sample).size
                            )
                            samples.resize(1 + channels, stream.frames).label(*channelLabels)
                            channelVolumes.resize(1 + channels, 1).label(*channelLabels)
                            stream.start()
                            logger.info("Capture started, capturing master + $channels channels @ ${rate}Hz $sample")
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
        fftSampleBuffer = RollingBuffer(size, 0.0f)
        fftResult.resize(1, size / 2).label(CommonChannel.MASTER)
        changeWindowingFunction()
    }

    fun changeWindowingFunction() {
        val size = fftSampleBuffer.size
        windowFunction = when (usedWindowingFunction.get()!!) {
            WindowFunctionType.HAMMING -> HammingWindowFunction(size)
            WindowFunctionType.HANN -> HannWindowFunction(size)
            WindowFunctionType.BLACKMAN_HARRIS -> BlackmanHarrisWindowFunction(size)
        }
    }

    @PreDestroy
    fun cleanup() {
        stopCapture()
    }

    /**
     * This implementation is windows only
     */
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