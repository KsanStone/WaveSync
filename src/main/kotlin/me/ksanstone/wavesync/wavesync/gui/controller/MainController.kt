package me.ksanstone.wavesync.wavesync.gui.controller

import javafx.application.Platform
import javafx.fxml.FXML
import javafx.fxml.Initializable
import javafx.scene.control.ComboBox
import javafx.scene.control.Label
import me.ksanstone.wavesync.wavesync.WaveSyncBootApplication
import me.ksanstone.wavesync.wavesync.event.FXMLInitializeEvent
import me.ksanstone.wavesync.wavesync.gui.component.visualizer.BarVisualizer
import me.ksanstone.wavesync.wavesync.gui.initializer.MenuInitializer
import me.ksanstone.wavesync.wavesync.service.AudioCaptureService
import me.ksanstone.wavesync.wavesync.service.SupportedCaptureSource
import java.net.URL
import java.util.*
import java.util.concurrent.CompletableFuture

class MainController() : Initializable {

    @FXML
    lateinit var audioDeviceListComboBox: ComboBox<String>
    @FXML
    lateinit var visualizer: BarVisualizer
    @FXML
    lateinit var deviceInfoLabel: Label

    private val deviceList: MutableList<SupportedCaptureSource> = ArrayList()
    private var audioCaptureService: AudioCaptureService
    private var menuInitializer: MenuInitializer
    private var lastDeviceId: String? = null

    init {
        instance = this

        audioCaptureService = WaveSyncBootApplication.applicationContext.getBean(AudioCaptureService::class.java)
        menuInitializer = WaveSyncBootApplication.applicationContext.getBean(MenuInitializer::class.java)
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
                deviceInfoLabel.text = "No device"
            } else {
                deviceInfoLabel.text = source.getPropertyDescriptor(audioCaptureService.lowPass.get(), visualizer.cutoff.get())
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
            menuInitializer.showPopupMenu("layout/controlMenu.fxml", "Device options")
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
        WaveSyncBootApplication.applicationContext.publishEvent(FXMLInitializeEvent(this))

        refreshDeviceList()
        selectDefaultDevice()

        audioCaptureService.registerObserver(visualizer::handleFFT)
        audioCaptureService.lowPass.addListener { _ -> refreshInfoLabel() }
        visualizer.cutoff.addListener { _ -> refreshInfoLabel() }
    }

    companion object {
        lateinit var instance: MainController
    }
}