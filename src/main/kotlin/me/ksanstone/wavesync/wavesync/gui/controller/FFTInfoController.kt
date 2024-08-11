package me.ksanstone.wavesync.wavesync.gui.controller

import javafx.application.Platform
import javafx.fxml.FXML
import javafx.fxml.Initializable
import javafx.scene.control.Label
import me.ksanstone.wavesync.wavesync.WaveSyncBootApplication
import me.ksanstone.wavesync.wavesync.service.AudioCaptureService
import me.ksanstone.wavesync.wavesync.service.LocalizationService
import me.ksanstone.wavesync.wavesync.service.fftScaling.DeciBelFFTScalar
import java.net.URL
import java.text.DecimalFormat
import java.util.*

class FFTInfoController : Initializable {

    @FXML
    lateinit var statusLabel: Label

    @FXML
    lateinit var peakVLabel: Label

    @FXML
    lateinit var peakLabel: Label

    private val acs = WaveSyncBootApplication.applicationContext.getBean(AudioCaptureService::class.java)
    private val peakFormat = DecimalFormat("###.##")
    private val deciBelFFTScalar = DeciBelFFTScalar()
    private val localizationService: LocalizationService =
        WaveSyncBootApplication.applicationContext.getBean(LocalizationService::class.java)

    override fun initialize(location: URL?, resources: ResourceBundle?) {
        acs.peakFrequency.addListener { _, _, v -> Platform.runLater { peakLabel.text = peakFormat.format(v) + " Hz" } }
        acs.peakValue.addListener { _, _, v ->
            Platform.runLater {
                peakVLabel.text = peakFormat.format(deciBelFFTScalar.scaleRaw(v.toFloat())) + " dB"
            }
        }
        listOf(
            acs.captureRunning,
            acs.paused,
        ).forEach { it.addListener { _, _, _ -> Platform.runLater { updateStatusLabel() } } }

    }

    private fun updateStatusLabel() {
        if (acs.captureRunning.value && acs.paused.value) {
            statusLabel.text = localizationService.get("component.fftInfo.status.paused")
        } else if (acs.captureRunning.value) {
            statusLabel.text = localizationService.get("component.fftInfo.status.running")
        } else {
            statusLabel.text = localizationService.get("component.fftInfo.status.disconnected")
        }
    }

    fun compact(compact: Boolean) {
        if (compact) {
            statusLabel.visibleProperty().set(false)

        }
    }
}