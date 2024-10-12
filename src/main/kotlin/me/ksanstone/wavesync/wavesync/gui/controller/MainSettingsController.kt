package me.ksanstone.wavesync.wavesync.gui.controller

import atlantafx.base.controls.ToggleSwitch
import javafx.fxml.FXML
import javafx.fxml.Initializable
import javafx.scene.control.*
import javafx.scene.control.Alert.AlertType
import javafx.scene.image.Image
import javafx.stage.Stage
import javafx.util.Duration
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.DEFAULT_END_COLOR
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.DEFAULT_FFT_SIZE
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.DEFAULT_START_COLOR
import me.ksanstone.wavesync.wavesync.WaveSyncBootApplication
import me.ksanstone.wavesync.wavesync.service.AudioCaptureService
import me.ksanstone.wavesync.wavesync.service.GlobalColorService
import me.ksanstone.wavesync.wavesync.service.LocalizationService
import me.ksanstone.wavesync.wavesync.service.PreferenceService
import me.ksanstone.wavesync.wavesync.service.windowing.WindowFunctionType
import xt.audio.Enums.XtSystem
import java.net.URL
import java.util.*
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.round


class MainSettingsController : Initializable {

    @FXML
    lateinit var fftRateLabel: Label

    @FXML
    lateinit var fftRateSpinner: Spinner<Int>

    @FXML
    lateinit var useThemeColorToggleSwitch: ToggleSwitch

    @FXML
    lateinit var startColorPicker: ColorPicker

    @FXML
    lateinit var endColorPicker: ColorPicker

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
    private lateinit var preferenceService: PreferenceService
    private lateinit var globalColorService: GlobalColorService

    private fun changeAudioSystem() {
        audioCaptureService.usedAudioSystem.set(XtSystem.valueOf(audioServerChoiceBox.value))
        MainController.instance.refreshDeviceList()
    }

    override fun initialize(location: URL?, resources: ResourceBundle?) {
        audioCaptureService = WaveSyncBootApplication.applicationContext.getBean(AudioCaptureService::class.java)
        localizationService = WaveSyncBootApplication.applicationContext.getBean(LocalizationService::class.java)
        preferenceService = WaveSyncBootApplication.applicationContext.getBean(PreferenceService::class.java)
        globalColorService = WaveSyncBootApplication.applicationContext.getBean(GlobalColorService::class.java)

        startColorPicker.value = globalColorService.startColor.get()
        globalColorService.startColor.bind(startColorPicker.valueProperty())
        endColorPicker.value = globalColorService.endColor.get()
        globalColorService.endColor.bind(endColorPicker.valueProperty())
        useThemeColorToggleSwitch.selectedProperty().value = globalColorService.barUseCssColor.value
        globalColorService.barUseCssColor.bind(useThemeColorToggleSwitch.selectedProperty())
        startColorPicker.disableProperty().bind(useThemeColorToggleSwitch.selectedProperty())
        endColorPicker.disableProperty().bind(useThemeColorToggleSwitch.selectedProperty())

        audioServerChoiceBox.items.clear()
        audioServerChoiceBox.items.addAll(audioCaptureService.audioSystems.map { it.name })
        audioServerChoiceBox.value = audioCaptureService.usedAudioSystem.get()?.name
        audioServerChoiceBox.valueProperty().addListener { _ ->
            changeAudioSystem()
        }

        fftRateSpinner.valueFactory = SpinnerValueFactory.IntegerSpinnerValueFactory(
            10,
            512,
            audioCaptureService.fftRate.get(),
            1
        )
        audioCaptureService.fftRate.bind(fftRateSpinner.valueProperty())
        fftRateSpinner.valueProperty().addListener { _, _, _ -> updateFftRateInfoLabel() }

        windowingFunctionChoiceBox.items.addAll(WindowFunctionType.entries.map { it.displayName })
        windowingFunctionChoiceBox.value = audioCaptureService.usedWindowingFunction.value.displayName
        audioCaptureService.usedWindowingFunction.bind(
            windowingFunctionChoiceBox.valueProperty().map { WindowFunctionType.fromDisplayName(it) })

        fftSizeChoiceBox.items.clear()
        fftSizeChoiceBox.items.addAll(listOf(8, 9, 10, 11, 12, 13, 14, 15, 16).map { 2.0.pow(it.toDouble()).toInt() }
            .toList())
        fftSizeChoiceBox.value = audioCaptureService.fftSize.get()
        fftSizeChoiceBox.valueProperty().addListener { _ -> updateFftInfoLabel() }
        audioCaptureService.source.addListener { _ -> updateFftInfoLabel() }

        fftSizeChoiceBox.valueProperty().addListener { _, _, v ->
            if (v != audioCaptureService.fftSize.get()) {
                if (!applyFreqButton.styleClass.contains("accent")) applyFreqButton.styleClass.add("accent")
            } else {
                applyFreqButton.styleClass.remove("accent")
            }
        }

        debugToggleSwitch.selectedProperty().set(MainController.instance.infoShown.get())
        MainController.instance.infoShown.bind(debugToggleSwitch.selectedProperty())

        updateFftInfoLabel()
        updateFftRateInfoLabel()
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
            fftSizeChoiceBox.value = DEFAULT_FFT_SIZE
            applyFreqSettings()
        }

    }

    private fun updateFftRateInfoLabel() {
        if (audioCaptureService.source.get() != null) {
            val rate = fftRateSpinner.value
            val captureRate = audioCaptureService.source.get().rate
            val effectiveRate = max(rate, (captureRate.toDouble() / audioCaptureService.fftSize.get()).toInt())
            val frameMs = (1.0 / effectiveRate) * 1000
            val frameSamples = (1.0 / effectiveRate) * captureRate
            val bufferAmount = frameSamples / audioCaptureService.fftSize.get()

            fftRateLabel.text = localizationService.format(
                if (effectiveRate != rate) "dialog.deviceOptions.fft.effectiveRateInfo" else "dialog.deviceOptions.fft.rateInfo",
                effectiveRate,
                frameMs,
                frameSamples.toInt(),
                bufferAmount,
            )
        } else {
            fftInfoLabel.text = localizationService.get("dialog.deviceOptions.noDevice")
        }
    }

    private fun updateFftInfoLabel() {
        updateFftRateInfoLabel()
        if (audioCaptureService.source.get() != null) {
            val freq = audioCaptureService.source.get().getMinimumFrequency(fftSizeChoiceBox.value)
            var updateInterval = audioCaptureService.source.get().getUpdateInterval(fftSizeChoiceBox.value)
            updateInterval = Duration.millis(round(updateInterval.toMillis() * 10.0) / 10.0)
            fftInfoLabel.text = localizationService.format("dialog.deviceOptions.windowSizeInfo", freq, updateInterval)
        } else {
            fftInfoLabel.text = localizationService.get("dialog.deviceOptions.noDevice")
        }
    }

    fun applyFreqSettings() {
        applyFreqButton.styleClass.remove("accent")
        audioCaptureService.fftSize.set(fftSizeChoiceBox.value)
        audioCaptureService.restartCapture()
        updateFftRateInfoLabel()
    }

    fun purgeDataDialog() {
        val reset = ButtonType(localizationService.get("confirmation.yes"), ButtonBar.ButtonData.OK_DONE)
        val cancel = ButtonType(localizationService.get("confirmation.no"), ButtonBar.ButtonData.CANCEL_CLOSE)

        val alert = Alert(
            AlertType.CONFIRMATION,
            localizationService.get("confirmation.deviceOptions.purge"),
            reset,
            cancel
        )
        (alert.dialogPane.scene.window as Stage).icons.add(Image("icon.png"))

        if (alert.showAndWait().get() == reset) {
            fftSizeChoiceBox.value = DEFAULT_FFT_SIZE
            preferenceService.purgeAllData()
        }
    }

    fun resetColors() {
        startColorPicker.value = DEFAULT_START_COLOR
        endColorPicker.value = DEFAULT_END_COLOR
    }

}