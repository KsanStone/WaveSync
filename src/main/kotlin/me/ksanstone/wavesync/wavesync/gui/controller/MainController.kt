package me.ksanstone.wavesync.wavesync.gui.controller

import javafx.application.Platform
import javafx.event.ActionEvent
import javafx.fxml.FXML
import javafx.fxml.Initializable
import javafx.scene.control.ComboBox
import javafx.scene.control.Label
import me.ksanstone.wavesync.wavesync.WaveSyncBootApplication
import me.ksanstone.wavesync.wavesync.event.FXMLInitializeEvent
import me.ksanstone.wavesync.wavesync.gui.component.visualizer.BarVisualizer
import me.ksanstone.wavesync.wavesync.service.AudioCaptureService
import me.ksanstone.wavesync.wavesync.service.SupportedCaptureSource
import java.net.URL
import java.util.*
import java.util.concurrent.CompletableFuture

class MainController() : Initializable  {

    @FXML
    var audioDeviceListComboBox: ComboBox<String>? = null
    @FXML
    var visualizer: BarVisualizer? = null
    @FXML
    var deviceInfoLabel: Label? = null;

    private val deviceList: MutableList<SupportedCaptureSource> = ArrayList()
    private lateinit var audioCaptureService: AudioCaptureService

    @FXML
    fun audioDevicePicker(event: ActionEvent) {
        val source = deviceList.find { it.name == audioDeviceListComboBox!!.value} ?: return
        CompletableFuture.runAsync {
            audioCaptureService.stopCapture()
            audioCaptureService.startCapture(source)
            Platform.runLater {
                deviceInfoLabel!!.text = source.getDescriptor()
            }
        }.exceptionally {
            it.printStackTrace()
            null
        }
    }

    @FXML
    override fun initialize(location: URL?, resources: ResourceBundle?) {
        deviceList.clear()
        deviceList.addAll(AudioCaptureService.findSupportedSources())
        audioDeviceListComboBox!!.items.addAll(deviceList.map { it.name })
        WaveSyncBootApplication.applicationContext.publishEvent(FXMLInitializeEvent(this))
        audioCaptureService = WaveSyncBootApplication.applicationContext.getBean(AudioCaptureService::class.java)
        audioCaptureService.registerObserver(visualizer!!::handleFFT)
    }

}