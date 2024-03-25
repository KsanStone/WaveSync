package me.ksanstone.wavesync.wavesync.gui.controller

import atlantafx.base.controls.ToggleSwitch
import javafx.fxml.FXML
import javafx.fxml.Initializable
import javafx.scene.control.*
import javafx.scene.control.Alert.AlertType
import javafx.scene.image.Image
import javafx.stage.Stage
import javafx.util.Duration
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.FFT_SIZE
import me.ksanstone.wavesync.wavesync.WaveSyncBootApplication
import me.ksanstone.wavesync.wavesync.service.AudioCaptureService
import me.ksanstone.wavesync.wavesync.service.LocalizationService
import me.ksanstone.wavesync.wavesync.service.windowing.WindowFunctionType
import xt.audio.Enums.XtSystem
import java.net.URL
import java.util.*
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.roundToInt


class VisualizerOptionsController : Initializable {

    lateinit var fftUpsampleChoiceBox: ChoiceBox<String>

    @FXML
    lateinit var windowingFunctionChoiceBox: ChoiceBox<String>

    @FXML
    lateinit var debugToggleSwitch: ToggleSwitch


    @FXML
    lateinit var audioServerChoiceBox: ChoiceBox<String>

    @FXML
    lateinit var applyFreqButton: Button


    @FXML
    lateinit var fftInfoLabel: Label

    @FXML
    lateinit var fftSizeChoiceBox: ChoiceBox<Int>


    private lateinit var audioCaptureService: AudioCaptureService
    private lateinit var localizationService: LocalizationService

    private fun changeAudioSystem() {
        audioCaptureService.usedAudioSystem.set(XtSystem.valueOf(audioServerChoiceBox.value))
        MainController.instance.refreshDeviceList()
    }

    override fun initialize(location: URL?, resources: ResourceBundle?) {
        audioCaptureService = WaveSyncBootApplication.applicationContext.getBean(AudioCaptureService::class.java)
        localizationService = WaveSyncBootApplication.applicationContext.getBean(LocalizationService::class.java)

        audioServerChoiceBox.items.clear()
        audioServerChoiceBox.items.addAll(audioCaptureService.audioSystems.map { it.name })
        audioServerChoiceBox.value = audioCaptureService.usedAudioSystem.get()?.name
        audioServerChoiceBox.valueProperty().addListener { _ ->
            changeAudioSystem()
        }

        windowingFunctionChoiceBox.items.addAll(WindowFunctionType.entries.map { it.displayName })
        windowingFunctionChoiceBox.value = audioCaptureService.usedWindowingFunction.value.displayName
        audioCaptureService.usedWindowingFunction.bind(
            windowingFunctionChoiceBox.valueProperty().map { WindowFunctionType.fromDisplayName(it) })

        fftSizeChoiceBox.items.clear()
        fftSizeChoiceBox.items.addAll(listOf(8, 9, 10, 11, 12, 13, 14, 15, 16).map { 2.0.pow(it.toDouble()).toInt() }
            .toList())
        fftSizeChoiceBox.value = audioCaptureService.fftSize.get()
        fftUpsampleChoiceBox.items.addAll(listOf(1, 2, 4, 8, 16, 32).map { "${it}x" })
        fftUpsampleChoiceBox.value = "${audioCaptureService.fftUpsample.get()}x"
        fftSizeChoiceBox.valueProperty().addListener { _ -> updateFftInfoLabel() }
        audioCaptureService.source.addListener { _ -> updateFftInfoLabel() }

        fftSizeChoiceBox.valueProperty().addListener { _, _, v ->
            if (v != audioCaptureService.fftSize.get()) {
                if (!applyFreqButton.styleClass.contains("accent")) applyFreqButton.styleClass.add("accent")
            } else {
                applyFreqButton.styleClass.remove("accent")
            }
        }
        fftUpsampleChoiceBox.valueProperty().addListener { _, _, v ->
            audioCaptureService.fftUpsample.set(v.replace("x", "").toInt())
            updateFftInfoLabel()
        }

        debugToggleSwitch.selectedProperty().set(MainController.instance.infoShown.get())
        MainController.instance.infoShown.bind(debugToggleSwitch.selectedProperty())

        updateFftInfoLabel()
    }

    fun showResetToDefaultsDialog() {
        val reset = ButtonType(localizationService.get("confirmation.yes"), ButtonBar.ButtonData.OK_DONE)
        val cancel = ButtonType(localizationService.get("confirmation.no"), ButtonBar.ButtonData.CANCEL_CLOSE)

        val alert = Alert(
            AlertType.CONFIRMATION,
            localizationService.get("confirmation.deviceOptions.defaults"),
            reset,
            cancel
        )
        (alert.dialogPane.scene.window as Stage).icons.add(Image("icon.png"))

        if (alert.showAndWait().get() == reset) {
            fftSizeChoiceBox.value = FFT_SIZE
            applyFreqSettings()
        }

    }

    private fun updateFftInfoLabel() {
        if (audioCaptureService.source.get() != null) {
            val freq = audioCaptureService.source.get().getMinimumFrequency(fftSizeChoiceBox.value)
            var updateInterval = audioCaptureService.source.get().getUpdateInterval(fftSizeChoiceBox.value)
            updateInterval = Duration.millis(round(updateInterval.toMillis() * 10.0) / 10.0)
            if (audioCaptureService.fftUpsample.get() <= 1) {
                fftInfoLabel.text =
                    localizationService.format("dialog.deviceOptions.windowSizeInfo", freq, updateInterval)
            } else {
                val upsampledDuration = updateInterval.divide(audioCaptureService.fftUpsample.get().toDouble())
                val upsampledHertz = 1.0 / upsampledDuration.toSeconds()
                fftInfoLabel.text =
                    localizationService.format(
                        "dialog.deviceOptions.upSampledWindowSizeInfo",
                        freq,
                        updateInterval,
                        upsampledDuration,
                        upsampledHertz.roundToInt()
                    )
            }
        } else {
            fftInfoLabel.text = localizationService.get("dialog.deviceOptions.noDevice")
        }
    }

    fun applyFreqSettings() {
        audioCaptureService.fftSize.set(fftSizeChoiceBox.value)
        applyFreqButton.styleClass.remove("accent")
        audioCaptureService.restartCapture()
    }

}