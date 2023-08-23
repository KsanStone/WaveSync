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
    private lateinit var audioCaptureService: AudioCaptureService
    private lateinit var menuInitializer: MenuInitializer

    init {
        instance = this
    }

    @FXML
    fun audioDevicePicker() {
        val source = deviceList.find { it.name == audioDeviceListComboBox.value} ?: return
        CompletableFuture.runAsync {
            audioCaptureService.changeSource(source)
            refreshInfoLabel()
        }.exceptionally {
            it.printStackTrace()
            null
        }
    }

    private fun refreshInfoLabel() {
        Platform.runLater {
            val source = audioCaptureService.source.get()
            if (source == null) {
                println("NO DEVICE")
                deviceInfoLabel.text = "No device"
            } else {
                deviceInfoLabel.text = source.getPropertyDescriptor(audioCaptureService.lowpass.get(), visualizer.cutoff.get())
            }
        }
    }

    @FXML
    fun refreshDeviceList() {
        audioCaptureService.stopCapture()
        deviceList.clear()
        deviceList.addAll(AudioCaptureService.findSupportedSources())
        audioDeviceListComboBox.items.clear()
        audioDeviceListComboBox.items.addAll(deviceList.map { it.name })

        refreshInfoLabel()
    }

    @FXML
    fun showOptionMenu() {
        Platform.runLater {
            menuInitializer.showPopupMenu("layout/controlMenu.fxml", "Device options")
        }
    }

    @FXML
    override fun initialize(location: URL?, resources: ResourceBundle?) {
        deviceList.clear()
        deviceList.addAll(AudioCaptureService.findSupportedSources())
        audioDeviceListComboBox.items.clear()
        audioDeviceListComboBox.items.addAll(deviceList.map { it.name })
        WaveSyncBootApplication.applicationContext.publishEvent(FXMLInitializeEvent(this))

        audioCaptureService = WaveSyncBootApplication.applicationContext.getBean(AudioCaptureService::class.java)
        menuInitializer = WaveSyncBootApplication.applicationContext.getBean(MenuInitializer::class.java)
        audioCaptureService.registerObserver(visualizer::handleFFT)

        audioCaptureService.lowpass.addListener { _ -> refreshInfoLabel() }
        visualizer.cutoff.addListener { _ -> refreshInfoLabel() }
    }

    companion object {
        lateinit var instance: MainController
    }
}