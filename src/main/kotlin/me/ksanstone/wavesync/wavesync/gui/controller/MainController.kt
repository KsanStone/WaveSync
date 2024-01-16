package me.ksanstone.wavesync.wavesync.gui.controller

import javafx.application.Platform
import javafx.beans.property.SimpleBooleanProperty
import javafx.fxml.FXML
import javafx.fxml.Initializable
import javafx.scene.control.ChoiceBox
import javafx.scene.control.Label
import javafx.scene.control.SplitPane
import javafx.scene.layout.HBox
import me.ksanstone.wavesync.wavesync.WaveSyncBootApplication
import me.ksanstone.wavesync.wavesync.gui.component.visualizer.BarVisualizer
import me.ksanstone.wavesync.wavesync.gui.component.visualizer.VolumeVisualizer
import me.ksanstone.wavesync.wavesync.gui.component.visualizer.WaveformVisualizer
import me.ksanstone.wavesync.wavesync.gui.initializer.MenuInitializer
import me.ksanstone.wavesync.wavesync.service.*
import java.net.URL
import java.util.*
import java.util.concurrent.CompletableFuture

class MainController : Initializable {

    @FXML
    lateinit var bottomBar: HBox

    @FXML
    lateinit var audioDeviceListComboBox: ChoiceBox<String>

    @FXML
    lateinit var visualizerPane: SplitPane

    @FXML
    lateinit var deviceInfoLabel: Label

    private val deviceList: MutableList<SupportedCaptureSource> = ArrayList()
    private var audioCaptureService: AudioCaptureService
    private var recordingModeService: RecordingModeService
    private var localizationService: LocalizationService
    private var menuInitializer: MenuInitializer
    private var preferenceService: PreferenceService
    private var lastDeviceId: String? = null
    private var barVisualizer: BarVisualizer
    private var waveformVisualizer: WaveformVisualizer
    private lateinit var resources: ResourceBundle
    val infoShown = SimpleBooleanProperty(false)

    init {
        instance = this

        audioCaptureService = WaveSyncBootApplication.applicationContext.getBean(AudioCaptureService::class.java)
        recordingModeService = WaveSyncBootApplication.applicationContext.getBean(RecordingModeService::class.java)
        menuInitializer = WaveSyncBootApplication.applicationContext.getBean(MenuInitializer::class.java)
        localizationService = WaveSyncBootApplication.applicationContext.getBean(LocalizationService::class.java)
        preferenceService = WaveSyncBootApplication.applicationContext.getBean(PreferenceService::class.java)
        waveformVisualizer = WaveformVisualizer()
        barVisualizer = BarVisualizer()
    }

    @FXML
    fun audioDevicePicker() {
        setByName(audioDeviceListComboBox.value ?: "")
    }

    private fun setByName(name: String) {
        val source = deviceList.find { it.name == name } ?: return
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
                deviceInfoLabel.text = source.getPropertyDescriptor(
                    audioCaptureService.fftSize.get(),
                    barVisualizer.lowPass.get(),
                    barVisualizer.cutoff.get(),
                    localizationService.numberFormat
                )
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
            val similarDevice = audioCaptureService.findSimilarAudioSource(lastDeviceId!!, deviceList) ?: return

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

        refreshDeviceList()
        selectDefaultDevice()

        audioCaptureService.registerFFTObserver(barVisualizer::handleFFT)
        audioCaptureService.registerSampleObserver(waveformVisualizer::handleSamples)

        audioCaptureService.fftSize.addListener { _ -> refreshInfoLabel() }
        barVisualizer.cutoff.addListener { _ -> refreshInfoLabel() }
        barVisualizer.lowPass.addListener { _ -> refreshInfoLabel() }
        barVisualizer.framerate.set(
            WaveSyncBootApplication.applicationContext.getBean(WaveSyncBootApplication::class.java)
                .findHighestRefreshRate()
        )
        barVisualizer.info.bind(infoShown)
        waveformVisualizer.framerate.set(
            WaveSyncBootApplication.applicationContext.getBean(WaveSyncBootApplication::class.java)
                .findHighestRefreshRate()
        )
        waveformVisualizer.info.bind(infoShown)

        barVisualizer.registerPreferences("main", preferenceService)
        barVisualizer.initializeSettingMenu()
        waveformVisualizer.registerPreferences("main", preferenceService)
        waveformVisualizer.initializeSettingMenu()
        preferenceService.registerProperty(infoShown, "graphInfoShown", this.javaClass)

        visualizerPane.items.add(barVisualizer)
        visualizerPane.items.add(waveformVisualizer)

        val masterVolumeVisualizer = VolumeVisualizer()
        audioCaptureService.channelVolumes.listeners.add {store ->
            masterVolumeVisualizer.values = (1 until store.channels()).map { store[it].data[0].toDouble() }
            masterVolumeVisualizer.labels = (1 until store.channels()).map { store[it].label }
        }
        bottomBar.children.add(masterVolumeVisualizer)
        bottomBar.visibleProperty().bind(recordingModeService.recordingMode.not())
        bottomBar.managedProperty().bind(bottomBar.visibleProperty())
    }

    companion object {
        lateinit var instance: MainController
    }
}