package me.ksanstone.wavesync.wavesync.service

import com.sun.jna.Platform
import com.sun.jna.Pointer
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import javafx.beans.property.*
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.DEFAULT_FFT_RATE
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.DEFAULT_FFT_SIZE
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.DEFAULT_WINDOWING_FUNCTION
import me.ksanstone.wavesync.wavesync.service.FourierMath.calcRMS
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
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.function.Consumer
import kotlin.math.log10


@Service
class AudioCaptureService(
    private val preferenceService: PreferenceService,
    private var fftTransformerService: FFTTransformerService
) : AsyncInit() {
    private val logger: Logger = LoggerFactory.getLogger("AudioCaptureService")

    private lateinit var pcmDataBuffer: ByteArray
    private lateinit var fftwSignal: FloatPointer
    private lateinit var fftwResult: FloatPointer
    private lateinit var fftwSignalArray: FloatArray
    private var fftSampleBuffer: CyclicFFTChanneledStore = CyclicFFTChanneledStore()
    private var lock: CountDownLatch = CountDownLatch(0)
    private var recordingFuture: CompletableFuture<Void>? = null
    private var fftObservers: IndexedEventEmitter<Int, FftEvent> = IndexedEventEmitter()
    private var sampleObservers: IndexedEventEmitter<Int, SampleEvent> = IndexedEventEmitter()
    private var windowFunction: WindowFunction? = null
    private val _captureRunning = SimpleBooleanProperty(false)

    val fftResult = FloatChanneledStore()
    val samples = FloatChanneledStore()

    val peakFrequency = SimpleDoubleProperty(0.0)
    val peakValue = SimpleFloatProperty(0.0f)
    val captureRunning: ReadOnlyBooleanProperty = ReadOnlyBooleanProperty.readOnlyBooleanProperty(_captureRunning)
    val channelVolumes = FloatChanneledStore()

    val source: ObjectProperty<SupportedCaptureSource> = SimpleObjectProperty()
    val fftSize: IntegerProperty = SimpleIntegerProperty(DEFAULT_FFT_SIZE)
    val fftRate: IntegerProperty = SimpleIntegerProperty(DEFAULT_FFT_RATE)
    val usedAudioSystem: ObjectProperty<XtSystem> = SimpleObjectProperty()
    val usedWindowingFunction: ObjectProperty<WindowFunctionType> = SimpleObjectProperty(DEFAULT_WINDOWING_FUNCTION)
    val paused: BooleanProperty = SimpleBooleanProperty(false)
    var audioSystems: List<XtSystem> = listOf()

    private val defaultChannelLabels = arrayOf(CommonChannel.MASTER.label)

    @PostConstruct
    override fun init() {
        preferenceService.registerProperty(fftSize, "fftSize", this.javaClass)
        preferenceService.registerProperty(usedAudioSystem, "audioSystem", XtSystem::class.java, this.javaClass)
        preferenceService.registerProperty(
            usedWindowingFunction,
            "windowingFunction",
            WindowFunctionType::class.java,
            this.javaClass
        )
        preferenceService.registerProperty(fftRate, "fftRate", this.javaClass)

        // XtAudio cries like a baby when in a daemon thread
        Thread(this::doAsyncInit).apply {
            isDaemon = false
        }.start()
    }

    override fun asyncInit() {
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
    }

    private fun detectSupportedAudioSystems() {
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

    private fun marshalSamples(samples: Any?, format: XtFormat): FloatArray {
        return samples as FloatArray
    }

    private fun processSamples(audio: FloatArray, frames: Int) {
        if (paused.get()) return
        val channels = source.get().format.channels.inputs
        val sampleFactor = 1.0f / channels.toFloat()
        val targetSamplesUntilRefresh = ((1.0 / fftRate.get()) * source.get().rate).toInt().coerceAtMost(fftSize.get())
        for (frame in 0 until frames) {
            val sampleIndex = frame * channels
            var sample = 0.0f
            for (channel in 0 until channels) {
                val point = audio[sampleIndex + channel]
                sample += point * sampleFactor
                samples[1 + channel].data[frame] = point
                fftSampleBuffer[1 + channel].data.insert(point)
            }
            samples[0].data[frame] = sample
            fftSampleBuffer[0].data.insert(sample)
            if (fftSampleBuffer[0].data.written % targetSamplesUntilRefresh == 0L) {
                for (i in 0 until fftSampleBuffer.channels())
                    doFFT(i, fftSampleBuffer[i].data.toFloatArrayInterlaced(fftwSignalArray), source.get().rate)
            }
        }
        for (i in 0 until samples.channels()) {
            samples.setSizeHint(i, frames)
        }
        processSamples(frames)
    }

    private fun processSamples(frames: Int) {
        doLoudnessCalc(frames)

        sampleObservers.forEachIndex {
            if (it < samples.channels()) {
                val sampleSlice = samples[it].data.sliceArray(0 until frames)
                sampleObservers.publishFor(it, SampleEvent(it, sampleSlice, source.get()))
            }
        }
    }

    private fun doLoudnessCalc(frames: Int) {
        for (i in 0 until samples.channels()) {
            channelVolumes[i].data[0] = (20 * log10(calcRMS(samples[i].data, frames))).toFloat()
        }
        channelVolumes.fireDataChanged()
    }

    private fun doFFT(channel: Int, samples: FloatArray, rate: Int) {
        windowFunction!!.applyFunctionInterlaced(samples)
        fftTransformerService.scaleAndPutSamples(samples, windowFunction!!.getSum())
        fftTransformerService.transform()
        fftTransformerService.computeMagnitudesSquared(fftResult[channel].data)
        calcPeak(fftResult[channel].data)

        fftObservers.forEachIndex {
            if (it < fftResult.channels())
                fftObservers.publishFor(it, FftEvent(it, fftResult[it].data, source.get()))
        }
    }

    private val interpolator = ParabolicInterpolator()

    private fun calcPeak(fftResult: FloatArray) {
        val maxIdx = fftResult.indices.maxBy { fftResult[it] }
        if (maxIdx == -1) return

        val peakV = fftResult[maxIdx]
        if (peakV < 0.00001f) {
            peakValue.value = 0.0f
            return
        }

        peakFrequency.value = interpolator.calcPeak(fftResult, maxIdx, source.get().rate).toDouble()
        peakValue.value = peakV
    }

    fun registerFFTObserver(channelId: Int, observer: Consumer<FftEvent>) {
        fftObservers.on(channelId, observer)
    }

    fun registerSampleObserver(channelId: Int, observer: Consumer<SampleEvent>) {
        sampleObservers.on(channelId, observer)
    }

    private val channelLabelProps = mutableMapOf<Int, ObjectProperty<ChannelLabel>>()

    fun getChannelLabelProperty(index: Int): ObjectProperty<ChannelLabel> {
        if (channelLabelProps.containsKey(index)) {
            return channelLabelProps[index]!!
        } else {
            channelLabelProps[index] = SimpleObjectProperty(ChannelLabel.UNDEFINED)
            updateLabels()
            return channelLabelProps[index]!!
        }
    }

    private fun updateLabels() {
        javafx.application.Platform.runLater {
            channelLabelProps.values.forEach { it.value = ChannelLabel.UNDEFINED }
            for (i in 0 until samples.channels()) {
                if (channelLabelProps.containsKey(i)) {
                    channelLabelProps[i]!!.value = samples[i].label
                }
            }
        }
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
                    val channelLabels =
                        (0 until channels).map { idx -> ChannelLabel.resolve(device.getChannelName(false, idx)) }

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
                            fftSampleBuffer.resize(1 + channels, fftSize.get()).label(*defaultChannelLabels.plus(channelLabels))
                            setScanWindowSize(fftSize.get())
                            updateLabels()
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

    private fun setScanWindowSize(size: Int) {
        logger.info("Using window size $size")
        fftSampleBuffer.resizeBuffers(size)
        fftResult.resize(fftSampleBuffer.channels(), size / 2).label(CommonChannel.MASTER)
        if (this::fftwSignal.isInitialized) {
            this.fftwSignal.deallocate()
        }
        if (this::fftwResult.isInitialized) {
            this.fftwResult.deallocate()
        }
        fftwSignal = FloatPointer(size * 2L)
        fftwResult = FloatPointer(size * 2L)
        fftwSignalArray = FloatArray(size * 2)
        fftTransformerService.initializePlan(fftwSignal, fftwResult, size)
        changeWindowingFunction()
    }

    fun changeWindowingFunction() {
        val size = fftSize.get()
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

    class SampleEvent(val channel: Int, val data: FloatArray, val source: SupportedCaptureSource)
    class FftEvent(val channel: Int, val data: FloatArray, val source: SupportedCaptureSource)
}