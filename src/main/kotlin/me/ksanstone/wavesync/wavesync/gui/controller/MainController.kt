package me.ksanstone.wavesync.wavesync.gui.controller

import javafx.application.Platform
import javafx.fxml.FXML
import javafx.fxml.Initializable
import javafx.scene.control.ChoiceBox
import javafx.scene.control.Label
import me.ksanstone.wavesync.wavesync.WaveSyncBootApplication
import me.ksanstone.wavesync.wavesync.event.FXMLInitializeEvent
import me.ksanstone.wavesync.wavesync.gui.component.visualizer.BarVisualizer
import me.ksanstone.wavesync.wavesync.gui.initializer.MenuInitializer
import me.ksanstone.wavesync.wavesync.service.AudioCaptureService
import me.ksanstone.wavesync.wavesync.service.LocalizationService
import me.ksanstone.wavesync.wavesync.service.PreferenceService
import me.ksanstone.wavesync.wavesync.service.SupportedCaptureSource
import java.net.URL
import java.util.*
import java.util.concurrent.CompletableFuture

class MainController() : Initializable {

    @FXML
    lateinit var audioDeviceListComboBox: ChoiceBox<String>
    @FXML
    lateinit var visualizer: BarVisualizer
    @FXML
    lateinit var deviceInfoLabel: Label

    private val deviceList: MutableList<SupportedCaptureSource> = ArrayList()
    private var audioCaptureService: AudioCaptureService
    private var localizationService: LocalizationService
    private var menuInitializer: MenuInitializer
    private var preferenceService: PreferenceService
    private var lastDeviceId: String? = null
    private lateinit var resources: ResourceBundle

    init {
        instance = this

        audioCaptureService = WaveSyncBootApplication.applicationContext.getBean(AudioCaptureService::class.java)
        menuInitializer = WaveSyncBootApplication.applicationContext.getBean(MenuInitializer::class.java)
        localizationService = WaveSyncBootApplication.applicationContext.getBean(LocalizationService::class.java)
        preferenceService = WaveSyncBootApplication.applicationContext.getBean(PreferenceService::class.java)
    }

    @FXML
    fun audioDevicePicker() {
        setByName(audioDeviceListComboBox.value ?: "")
    }

    private fun setByName(name: String) {
        val source = deviceList.find { it.name == name} ?: return
        CompletableFuture.runAsync {
            lastDeviceId = source.id
            audioCaptureService.changeSource(source)
            refreshInfoLabel()
            Platform.runLater {
                audioDeviceListComboBox.value = name
            }
        }.exceptionally {
            it.printStackTrace()
            null
        }
    }

    private fun refreshInfoLabel() {
        Platform.runLater {
            val source = audioCaptureService.source.get()
            if (source == null) {
                deviceInfoLabel.text = localizationService.get("dialog.deviceOptions.noDevice")
            } else {
                deviceInfoLabel.text = source.getPropertyDescriptor(audioCaptureService.fftSize.get(), visualizer.lowPass.get(), visualizer.cutoff.get())
            }
        }
    }

    @FXML
    fun refreshDeviceList() {
        audioCaptureService.stopCapture()
        deviceList.clear()
        deviceList.addAll(audioCaptureService.findSupportedSources())
        audioDeviceListComboBox.items.clear()
        audioDeviceListComboBox.items.addAll(deviceList.map { it.name })
        refreshInfoLabel()

        if (lastDeviceId != null) {
            val similarDevice = audioCaptureService.findSimilarAudioSource(lastDeviceId!!, deviceList)?: return

            setByName(similarDevice.name)
        }
    }

    @FXML
    fun showOptionMenu() {
        Platform.runLater {
            menuInitializer.showPopupMenu("layout/controlMenu.fxml", resources.getString("dialog.deviceOptions.title"))
        }
    }

    private fun selectDefaultDevice() {
        val default = audioCaptureService.findDefaultAudioSource(deviceList)
        if (default != null) {
            setByName(default.name)
        }
    }

    @FXML
    override fun initialize(location: URL?, resources: ResourceBundle?) {
        this.resources = resources!!
        WaveSyncBootApplication.applicationContext.publishEvent(FXMLInitializeEvent(this))

        refreshDeviceList()
        selectDefaultDevice()

        audioCaptureService.registerObserver(visualizer::handleFFT)
        audioCaptureService.fftSize.addListener { _ -> refreshInfoLabel() }
        visualizer.cutoff.addListener { _ -> refreshInfoLabel() }
        visualizer.lowPass.addListener { _ -> refreshInfoLabel() }
        visualizer.framerate.set(WaveSyncBootApplication.applicationContext.getBean(WaveSyncBootApplication::class.java).findHighestRefreshRate())

        visualizer.registerPreferences("mainBarVisualizer", preferenceService)
    }

    companion object {
        lateinit var instance: MainController
    }
}