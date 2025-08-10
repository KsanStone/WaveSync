package me.ksanstone.wavesync.wavesync.service.audio

import com.sun.jna.Platform
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import javafx.beans.property.BooleanProperty
import javafx.beans.property.IntegerProperty
import javafx.beans.property.ObjectProperty
import javafx.beans.property.ReadOnlyBooleanProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleFloatProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.property.SimpleStringProperty
import javafx.beans.property.StringProperty
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults
import me.ksanstone.wavesync.wavesync.service.PreferenceService
import me.ksanstone.wavesync.wavesync.service.audio.backend.AudioBackend
import me.ksanstone.wavesync.wavesync.service.audio.backend.AudioSystem
import me.ksanstone.wavesync.wavesync.service.audio.backend.CaptureSource
import me.ksanstone.wavesync.wavesync.service.audio.backend.xt.XtAudioBackend
import me.ksanstone.wavesync.wavesync.service.audio.backend.xt.XtAudioSystem
import me.ksanstone.wavesync.wavesync.service.audio.backend.xt.XtCaptureSource
import me.ksanstone.wavesync.wavesync.service.audio.interpolation.ParabolicInterpolator
import me.ksanstone.wavesync.wavesync.service.audio.windowing.BlackmanHarrisWindowFunction
import me.ksanstone.wavesync.wavesync.service.audio.windowing.HammingWindowFunction
import me.ksanstone.wavesync.wavesync.service.audio.windowing.HannWindowFunction
import me.ksanstone.wavesync.wavesync.service.audio.windowing.WindowFunction
import me.ksanstone.wavesync.wavesync.service.audio.windowing.WindowFunctionType
import me.ksanstone.wavesync.wavesync.utility.AsyncInit
import me.ksanstone.wavesync.wavesync.utility.ChannelLabel
import me.ksanstone.wavesync.wavesync.utility.CommonChannel
import me.ksanstone.wavesync.wavesync.utility.CyclicFFTChanneledStore
import me.ksanstone.wavesync.wavesync.utility.FloatChanneledStore
import me.ksanstone.wavesync.wavesync.utility.IndexedEventEmitter
import me.ksanstone.wavesync.wavesync.utility.toFloatArrayInterlaced
import org.bytedeco.javacpp.FloatPointer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import xt.audio.Enums
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer
import kotlin.math.log10

@Service
class AudioCaptureService(
    private val preferenceService: PreferenceService,
    private var fftTransformerService: FFTTransformerService,
    xtAudioBackend: XtAudioBackend
) : AsyncInit() {
    private val logger: Logger = LoggerFactory.getLogger("AudioCaptureService")

    private val audioBackend: AudioBackend = xtAudioBackend
    private lateinit var pcmDataBuffer: ByteArray
    private lateinit var fftwSignal: FloatPointer
    private lateinit var fftwResult: FloatPointer
    private lateinit var fftwSignalArray: FloatArray
    private var fftSampleBuffer: CyclicFFTChanneledStore = CyclicFFTChanneledStore()
    private var fftObservers: IndexedEventEmitter<Int, FftEvent> = IndexedEventEmitter()
    private var sampleObservers: IndexedEventEmitter<Int, SampleEvent> = IndexedEventEmitter()
    private var windowFunction: WindowFunction? = null
    private val fftResult = FloatChanneledStore()
    val samples = FloatChanneledStore()

    val peakFrequency = List(ApplicationSettingDefaults.SUPPORTED_CHANNELS) { SimpleFloatProperty() }
    val peakValue = List(ApplicationSettingDefaults.SUPPORTED_CHANNELS) { SimpleFloatProperty() }
    val captureRunning: ReadOnlyBooleanProperty
        get() = audioBackend.captureRunning
    val channelVolumes = FloatChanneledStore()

    val source: ObjectProperty<CaptureSource> = SimpleObjectProperty()
    val fftSize: IntegerProperty = SimpleIntegerProperty(ApplicationSettingDefaults.DEFAULT_FFT_SIZE)
    val fftRate: IntegerProperty = SimpleIntegerProperty(ApplicationSettingDefaults.DEFAULT_FFT_RATE)
    val usedAudioSystem: ObjectProperty<AudioSystem?>
        get() = audioBackend.currentAudioSystem
    val audioSystemDataProxy: StringProperty = SimpleStringProperty(usedAudioSystem.get()?.name ?: "")
    val usedWindowingFunction: ObjectProperty<WindowFunctionType> =
        SimpleObjectProperty(ApplicationSettingDefaults.DEFAULT_WINDOWING_FUNCTION)
    val paused: BooleanProperty = SimpleBooleanProperty(false)
    var audioSystems: List<AudioSystem> = listOf()

    private val defaultChannelLabels = arrayOf(CommonChannel.MASTER.label)

    @PostConstruct
    override fun init() {
        usedAudioSystem.addListener { _, _, v -> audioSystemDataProxy.set(v?.name ?: "") }
        preferenceService.registerProperty(audioSystemDataProxy, "audioSystem", this.javaClass)

        val storedSystem = audioSystemDataProxy.get()
        if (storedSystem.isNotBlank()) {
            if (audioBackend is XtAudioBackend) {
                usedAudioSystem.set(XtAudioSystem(Enums.XtSystem.valueOf(storedSystem)))
            } else {
                usedAudioSystem.set(null)
            }
        } else {
            usedAudioSystem.set(null)
        }

        preferenceService.registerProperty(fftSize, "fftSize", this.javaClass)
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
        audioBackend.setSampleProcessor(this::processSamples)
        if (usedAudioSystem.get() == null) {
            if (Platform.isWindows() && audioSystems.any { it.name == "WASAPI" }) {
                val wasapi: AudioSystem = XtAudioSystem(Enums.XtSystem.WASAPI)
                usedAudioSystem.set(wasapi)
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
        audioSystems = audioBackend.detectSupportedAudioSystems()
    }

    private fun processSamples(audio: FloatArray, frames: Int) {
        if (paused.get()) return
        val channels = source.get().channels
        val sampleFactor = 1.0f / channels.toFloat()
        val targetSamplesUntilRefresh =
            ((1.0 / fftRate.get()) * source.get().sampleRate).toInt().coerceAtMost(fftSize.get())
        for (frame in 0 until frames) {
            val sampleIndex = frame * channels
            var combinedSample = 0.0f
            for (channel in 0 until channels) {
                val point = audio[sampleIndex + channel]
                combinedSample += point
                samples[1 + channel].data[frame] = point
                fftSampleBuffer[1 + channel].data.insert(point)
            }

            samples[0].data[frame] = combinedSample * sampleFactor
            fftSampleBuffer[0].data.insert(combinedSample)
            if (fftSampleBuffer[0].data.written % targetSamplesUntilRefresh == 0L) {
                doFFT()
            }
        }
        for (i in 0 until samples.channels()) {
            samples.setSizeHint(i, frames)
        }
        furtherProcessSamples(frames)
    }

    private fun furtherProcessSamples(frames: Int) {
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
            channelVolumes[i].data[0] = (20 * log10(FourierMath.calcRMS(samples[i].data, frames))).toFloat()
        }
        channelVolumes.fireDataChanged()
    }

    private fun doFFT() {
        val source = source.get()
        for (i in 0 until fftSampleBuffer.channels())
            doFFT(i, fftSampleBuffer[i].data.toFloatArrayInterlaced(fftwSignalArray))

        fftObservers.forEachIndex {
            if (it < fftResult.channels())
                fftObservers.publishFor(it, FftEvent(it, fftResult[it].data, source))
        }
    }

    private fun doFFT(channel: Int, samples: FloatArray) {
        windowFunction!!.applyFunctionInterlaced(samples)
        fftTransformerService.scaleAndPutSamples(samples, windowFunction!!.getSum())
        fftTransformerService.transform()
        fftTransformerService.computeMagnitudesSquared(fftResult[channel].data)
        calcPeak(channel, fftResult[channel].data)
    }

    private val interpolator = ParabolicInterpolator()

    private fun calcPeak(channel: Int, fftResult: FloatArray) {
        if (channel >= ApplicationSettingDefaults.SUPPORTED_CHANNELS) return
        val maxIdx = fftResult.indices.maxBy { fftResult[it] }
        if (maxIdx == -1) return

        val peakV = fftResult[maxIdx]
        if (peakV < 0.00001f) {
            peakValue[channel].value = 0.0f
            return
        }

        peakFrequency[channel].value = interpolator.calcPeak(fftResult, maxIdx, source.get().sampleRate)
        peakValue[channel].value = peakV
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
            channelLabelProps[index] = SimpleObjectProperty(ChannelLabel.Companion.UNDEFINED)
            updateLabels()
            return channelLabelProps[index]!!
        }
    }

    private fun updateLabels() {
        javafx.application.Platform.runLater {
            channelLabelProps.values.forEach { it.value = ChannelLabel.Companion.UNDEFINED }
            for (i in 0 until samples.channels()) {
                if (channelLabelProps.containsKey(i)) {
                    channelLabelProps[i]!!.value = samples[i].label
                }
            }
        }
    }

    @Synchronized
    fun startCapture(source: CaptureSource) {
        this.source.set(source)
        audioBackend.startCapture(source) { frames, channelLabels ->
            setScanWindowSize(fftSize.get())
            pcmDataBuffer = ByteArray(
                frames * source.channels * source.nativeFormat.bytesPerSample
            )
            samples.resize(1 + source.channels, frames).label(*defaultChannelLabels.plus(channelLabels))
            channelVolumes.resize(1 + source.channels, 1).label(*defaultChannelLabels.plus(channelLabels))
            fftSampleBuffer.resize(1 + source.channels, fftSize.get())
                .label(*defaultChannelLabels.plus(channelLabels))
            setScanWindowSize(fftSize.get())
            updateLabels()
        }
    }

    fun stopCapture() {
        audioBackend.stopCapture()
    }

    fun restartCapture() {
        if (source.get() != null) {
            stopCapture()
            startCapture(source.get())
        }
    }

    fun changeSource(source: CaptureSource) {
        if (source == this.source.get()) return
        stopCapture()
        startCapture(source as XtCaptureSource)
    }

    private fun setScanWindowSize(size: Int) {
        logger.info("Using window size $size")
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

    private fun changeWindowingFunction() {
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

    fun findSimilarAudioSource(device: String, devices: List<CaptureSource>): CaptureSource? {
        val extractedId = extractDeviceUUID(device)
        return devices.find { extractDeviceUUID(it.id) == extractedId }
    }

    fun findDefaultAudioSource(devices: List<CaptureSource>): CaptureSource? {
        return audioBackend.findDefaultCaptureSource(devices)
    }

    fun findSupportedSources(): List<CaptureSource> {
        return audioBackend.detectSupportedCaptureSources()
    }

    class SampleEvent(val channel: Int, val data: FloatArray, val source: CaptureSource)
    class FftEvent(val channel: Int, val data: FloatArray, val source: CaptureSource)
}