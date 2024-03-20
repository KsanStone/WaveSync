package me.ksanstone.wavesync.wavesync.service

import com.sun.jna.Platform
import com.sun.jna.Pointer
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import javafx.beans.property.*
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.DEFAULT_UPSAMPLING
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.DEFAULT_WINDOWING_FUNCTION
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.FFT_SIZE
import me.ksanstone.wavesync.wavesync.service.interpolation.ParabolicInterpolator
import me.ksanstone.wavesync.wavesync.service.windowing.*
import me.ksanstone.wavesync.wavesync.utility.*
import org.bytedeco.javacpp.FloatPointer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import xt.audio.Enums.*
import xt.audio.Structs.*
import xt.audio.XtAudio
import xt.audio.XtSafeBuffer
import xt.audio.XtStream
import java.lang.AssertionError
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.function.BiConsumer
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt


@Service
class AudioCaptureService(
    private val preferenceService: PreferenceService,
    private var fftTransformerService: FFTTransformerService
) {

    private val logger: Logger = LoggerFactory.getLogger("AudioCaptureService")

    private lateinit var pcmDataBuffer: ByteArray
    private lateinit var fftSampleBuffer: RollingBuffer<Float>
    private lateinit var fftwSignal: FloatPointer
    private lateinit var fftwResult: FloatPointer
    private lateinit var fftwSignalArray: FloatArray
    private var lock: CountDownLatch = CountDownLatch(0)
    private var recordingFuture: CompletableFuture<Void>? = null
    private var fftObservers: MutableList<BiConsumer<FloatArray, SupportedCaptureSource>> = mutableListOf()
    private var sampleObservers: MutableList<BiConsumer<FloatArray, SupportedCaptureSource>> = mutableListOf()
    private var windowFunction: WindowFunction? = null
    private val _captureRunning = SimpleBooleanProperty(false)

    val fftResult = FloatChanneledStore()
    val samples = FloatChanneledStore()

    val peakFrequency = SimpleDoubleProperty(0.0)
    val peakValue = SimpleFloatProperty(0.0f)
    val captureRunning = ReadOnlyBooleanProperty.readOnlyBooleanProperty(_captureRunning)
    val channelVolumes = FloatChanneledStore()

    val source: ObjectProperty<SupportedCaptureSource> = SimpleObjectProperty()
    val fftSize: IntegerProperty = SimpleIntegerProperty(FFT_SIZE)
    val fftUpsample: IntegerProperty = SimpleIntegerProperty(DEFAULT_UPSAMPLING)
    val usedAudioSystem: ObjectProperty<XtSystem> = SimpleObjectProperty()
    val usedWindowingFunction: ObjectProperty<WindowFunctionType> = SimpleObjectProperty(DEFAULT_WINDOWING_FUNCTION)
    val paused: BooleanProperty = SimpleBooleanProperty(false)
    var audioSystems: List<XtSystem> = listOf()
    val initLatch = CountDownLatch(1)

    private val defaultChannelLabels = arrayOf(CommonChannel.MASTER.label)

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

        CompletableFuture.runAsync(this::asyncInit)
    }

    protected fun asyncInit() {
        setScanWindowSize(512)
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
        initLatch.countDown()
    }

    fun detectSupportedAudioSystems() {
        XtAudio.init(null, Pointer.NULL).use { platform ->
            audioSystems = platform.systems.toList()
        }
    }
    
    fun onBuffer(stream: XtStream, buffer: XtBuffer, user: Any?): Int {
        val safe = XtSafeBuffer.get(stream) ?: return 0
        safe.lock(buffer)
        
        processSamples(marshalSamples(safe.input, stream.format), buffer.frames)

        safe.unlock(buffer)
        return 0
    }

    fun marshalSamples(samples: Any?, format: XtFormat): FloatArray {
        return samples as FloatArray
    }
    
    fun processSamples(audio: FloatArray, frames: Int) {
        if (paused.get()) return
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
                doFFT(fftSampleBuffer.toFloatArrayInterlaced(fftwSignalArray), source.get().rate)
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
        windowFunction!!.applyFunctionInterlaced(samples)
        fftTransformerService.scaleAndPutSamples(samples, windowFunction!!.getSum())
        fftTransformerService.transform()
        fftTransformerService.computeMagnitudesSquared(fftResult[0].data)
        calcPeak(fftResult[0].data)
        fftObservers.forEach { it.accept(fftResult[0].data, source.get()) }
    }

    private val interpolator = ParabolicInterpolator()

    private fun calcPeak(fftResult: FloatArray) {
        val maxIdx = fftResult.indices.maxBy { fftResult[it] }
        if (maxIdx == -1) return

        val peakV = fftResult[maxIdx]
        if (peakV < 0.001f) return

        peakFrequency.value = interpolator.calcPeak(fftResult, maxIdx, source.get().rate).toDouble()
        peakValue.value = peakV
    }

    fun registerFFTObserver(observer: BiConsumer<FloatArray, SupportedCaptureSource>) {
        fftObservers.add(observer)
    }

    fun registerSampleObserver(observer: BiConsumer<FloatArray, SupportedCaptureSource>) {
        sampleObservers.add(observer)
    }

    @Synchronized
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
                    format.mix.sample = XtSample.FLOAT32

                    val bufferSize: XtBufferSize = device.getBufferSize(format)
                    val streamParams = XtStreamParams(true, this::onBuffer, null, null)
                    val deviceParams = XtDeviceStreamParams(streamParams, format, bufferSize.current)

                    val channels = format.channels.inputs
                    val rate = deviceParams.format.mix.rate
                    val sample = deviceParams.format.mix.sample
                    val channelLabels = (0 until channels).map { idx -> ChannelLabel.resolve(device.getChannelName(false, idx)) }

                    setScanWindowSize(fftSize.get())
                    val deviceStream = device.openStream(deviceParams, null)
                    deviceStream.use { stream ->
                        logger.info("Stream opened Input latency ${stream.latency.input}")
                        logger.info("Channels: $channelLabels")
                        XtSafeBuffer.register(stream).use { _ ->
                            pcmDataBuffer = ByteArray(
                                stream.frames * channels * XtAudio.getSampleAttributes(sample).size
                            )
                            samples.resize(1 + channels, stream.frames).label(*defaultChannelLabels.plus(channelLabels))
                            channelVolumes.resize(1 + channels, 1).label(*defaultChannelLabels.plus(channelLabels))
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

    private final fun setScanWindowSize(size: Int) {
        logger.info("Using window size $size")
        fftSampleBuffer = RollingBuffer(size, 0.0f)
        fftResult.resize(1, size / 2).label(CommonChannel.MASTER)
        if(this::fftwSignal.isInitialized) {
            this.fftwSignal.deallocate()
        }
        if(this::fftwResult.isInitialized) {
            this.fftwResult.deallocate()
        }
        fftwSignal = FloatPointer(size * 2L)
        fftwResult = FloatPointer(size * 2L)
        fftwSignalArray = FloatArray(size * 2)
        fftTransformerService.initializePlan(fftwSignal, fftwResult, size)
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
        try {
            XtAudio.init(null, Pointer.NULL).use { platform ->
                val service = platform.getService(usedAudioSystem.get())
                val device = service.getDefaultDeviceId(true) ?: return null
                val extractedId = extractDeviceUUID(device)
                return devices.find { extractDeviceUUID(it.id) == extractedId }
            }
        } catch (e: AssertionError) {
            logger.error("Default device query FAIL", e)
        }; return null
    }

    fun findSupportedSources(): List<SupportedCaptureSource> {
        val supported = ArrayList<SupportedCaptureSource>()
        try {
            XtAudio.init(null, Pointer.NULL).use { platform ->
                val service = platform.getService(usedAudioSystem.get())
                try {
                    service.openDeviceList(EnumSet.of(XtEnumFlags.ALL)).use { list ->
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
                    logger.error("INIT FAILED", e)
                }
            }
        } catch (e: AssertionError) {
            logger.error("Audio query FAIL", e)
        }
        return supported
    }
}