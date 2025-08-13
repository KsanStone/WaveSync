package me.ksanstone.wavesync.wavesync.service.audio.backend.java

import javafx.beans.property.ObjectProperty
import javafx.beans.property.ReadOnlyBooleanProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleObjectProperty
import me.ksanstone.wavesync.wavesync.service.audio.backend.AudioBackend
import me.ksanstone.wavesync.wavesync.service.audio.backend.CaptureSource
import me.ksanstone.wavesync.wavesync.utility.ChannelLabel
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.IOException
import java.util.concurrent.CompletableFuture
import javax.sound.sampled.*


@Service
class JavaAudioBackend : AudioBackend {

    private val logger: Logger = LoggerFactory.getLogger(javaClass)

    private var recordingFuture: CompletableFuture<Void>? = null
    private var currentLine: TargetDataLine? = null
    private val _captureRunning = SimpleBooleanProperty(false)
    override val captureRunning: ReadOnlyBooleanProperty =
        ReadOnlyBooleanProperty.readOnlyBooleanProperty(_captureRunning)
    override val currentAudioSystem: ObjectProperty<me.ksanstone.wavesync.wavesync.service.audio.backend.AudioSystem?> =
        SimpleObjectProperty()

    private lateinit var processor: AudioBackend.SampleProcessor

    override fun detectSupportedAudioSystems(): List<me.ksanstone.wavesync.wavesync.service.audio.backend.AudioSystem> {
        return listOf(JavaAudioSystem("JAVA"))
    }

    override fun detectSupportedCaptureSources(): List<CaptureSource> {
        val sources = ArrayList<CaptureSource>()
        AudioSystem.getMixerInfo().forEach { mixerInfo ->
            val mixer = AudioSystem.getMixer(mixerInfo)
            val captureFormats = mixer.targetLineInfo
                .filter { TargetDataLine::class.java.isAssignableFrom(it.lineClass) && it is DataLine.Info }
                .flatMap { (it as DataLine.Info).formats.asList() }
                .filter { it.sampleRate.toInt() != AudioSystem.NOT_SPECIFIED && it.sampleSizeInBits != AudioSystem.NOT_SPECIFIED }

            if (captureFormats.isNotEmpty()) {
                val bestFormat = captureFormats.maxWithOrNull(
                    compareBy<AudioFormat> { it.sampleRate }
                        .thenBy { it.sampleSizeInBits }
                )

                if (bestFormat != null) {
                    sources.add(JavaCaptureSource(bestFormat, mixerInfo, mixerInfo.name, mixerInfo.name))
                }
            }
        }
        return sources
    }

    override fun findDefaultCaptureSource(devices: List<CaptureSource>): CaptureSource? {
        return devices.find { it.name.lowercase().contains("default") }
    }

    override fun setSampleProcessor(processor: AudioBackend.SampleProcessor) {
        this.processor = processor
    }

    override fun startCapture(
        source: CaptureSource,
        preCaptureCallback: AudioBackend.PreCaptureCallback
    ) {
        val javaSource = source as JavaCaptureSource
        val dataLineInfo = DataLine.Info(TargetDataLine::class.java, javaSource.format)
        if (!AudioSystem.isLineSupported(dataLineInfo)) throw IllegalArgumentException("Audio format not supported")
        val mixer = AudioSystem.getMixer(javaSource.mixerInfo)
        val line = TargetDataLine::class.java.cast(mixer.getLine(dataLineInfo))
        line.open()
        line.start()
        currentLine = line

        recordingFuture = CompletableFuture.runAsync {
            try {
                logger.info("Starting capture using javax.sound.sampled on ${line.lineInfo} ${line.format}")
                val chunkSize = 512
                val channels = javaSource.format.channels
                val sampleSize = javaSource.format.sampleSizeInBits
                val bytesPerSample = sampleSize / 8
                val buffer = ByteArray(chunkSize * channels * bytesPerSample)
                val sampleBuffer = FloatArray(chunkSize * channels)
                val channelLabels = ChannelLabel.numGeneric(channels)

                AudioInputStream(line).use { ais ->
                    preCaptureCallback.onPreCapture(chunkSize, channelLabels)
                    _captureRunning.set(true)
                    while (true) {
                        val bytesRead = ais.read(buffer, 0, buffer.size)
                        if (bytesRead > 0) {
                            val framesRead = bytesRead / (bytesPerSample * channels)
                            bytesToFloats(buffer, javaSource.format, sampleBuffer, bytesRead)

                            processor.process(sampleBuffer, framesRead)
                        } else {
                            logger.info("Audio input stream returned 0 bytes read")
                            break
                        }
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            } finally {
                _captureRunning.set(false)
            }
        }
    }

    override fun stopCapture() {
        if (captureRunning.get()) {
            currentLine?.close()
            currentLine = null
            recordingFuture?.get()
            recordingFuture = null
        }
    }

    fun bytesToFloats(bytes: ByteArray, format: AudioFormat, floatOut: FloatArray, len: Int): FloatArray {
        val bytesPerSample = format.sampleSizeInBits / 8
        val isBigEndian = format.isBigEndian
        val signed = format.encoding == AudioFormat.Encoding.PCM_SIGNED

        var outIndex = 0
        for (i in 0 until len step bytesPerSample) {
            var sample = 0

            // Assemble sample depending on endianness
            if (isBigEndian) {
                for (b in 0 until bytesPerSample) {
                    sample = (sample shl 8) or (bytes[i + b].toInt() and 0xFF)
                }
            } else {
                for (b in (bytesPerSample - 1) downTo 0) {
                    sample = (sample shl 8) or (bytes[i + b].toInt() and 0xFF)
                }
            }

            // Sign-extend if signed PCM
            if (signed) {
                val shift = 32 - format.sampleSizeInBits
                sample = (sample shl shift) shr shift
            }

            // Normalize
            val maxVal = if (signed) (1 shl (format.sampleSizeInBits - 1)).toFloat()
            else (1 shl format.sampleSizeInBits).toFloat() / 2f
            val floatSample = if (signed) sample / maxVal else (sample - maxVal) / maxVal

            floatOut[outIndex++] = floatSample
        }

        return floatOut
    }


}