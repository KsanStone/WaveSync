package me.ksanstone.wavesync.wavesync.gui.controller

import javafx.application.Platform
import javafx.beans.property.SimpleBooleanProperty
import javafx.fxml.FXML
import javafx.fxml.Initializable
import javafx.scene.control.CheckMenuItem
import javafx.scene.control.ChoiceBox
import javafx.scene.control.Label
import javafx.scene.control.SplitPane
import javafx.scene.layout.HBox
import me.ksanstone.wavesync.wavesync.WaveSyncBootApplication
import me.ksanstone.wavesync.wavesync.gui.component.info.FFTInfo
import me.ksanstone.wavesync.wavesync.gui.component.info.RuntimeInfo
import me.ksanstone.wavesync.wavesync.gui.component.layout.drag.DragLayout
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
    lateinit var runtimeInfoOnOff: CheckMenuItem

    @FXML
    lateinit var fftInfoOnOff: CheckMenuItem

    @FXML
    lateinit var barOnOff: CheckMenuItem

    @FXML
    lateinit var waveformOnOff: CheckMenuItem

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
    private var fftInfo: FFTInfo
    private var layoutService: LayoutStorageService
    private var globalLayoutService: GlobalLayoutService
    private var runtimeInfo: RuntimeInfo
    private lateinit var resources: ResourceBundle
    val infoShown = SimpleBooleanProperty(false)

    init {
        instance = this
        layoutService = WaveSyncBootApplication.applicationContext.getBean(LayoutStorageService::class.java)
        audioCaptureService = WaveSyncBootApplication.applicationContext.getBean(AudioCaptureService::class.java)
        recordingModeService = WaveSyncBootApplication.applicationContext.getBean(RecordingModeService::class.java)
        menuInitializer = WaveSyncBootApplication.applicationContext.getBean(MenuInitializer::class.java)
        localizationService = WaveSyncBootApplication.applicationContext.getBean(LocalizationService::class.java)
        preferenceService = WaveSyncBootApplication.applicationContext.getBean(PreferenceService::class.java)
        globalLayoutService = WaveSyncBootApplication.applicationContext.getBean(GlobalLayoutService::class.java)
        waveformVisualizer = WaveformVisualizer()
        barVisualizer = BarVisualizer()
        fftInfo = FFTInfo()
        runtimeInfo = RuntimeInfo()
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

    private fun initializeWindowControls(layout: DragLayout) {
        waveformOnOff.selectedProperty().set(layout.layoutRoot.queryComponentOfClassExists(WaveformVisualizer::class.java))
        barOnOff.selectedProperty().set(layout.layoutRoot.queryComponentOfClassExists(BarVisualizer::class.java))
        fftInfoOnOff.selectedProperty().set(layout.layoutRoot.queryComponentOfClassExists(FFTInfo::class.java))
        runtimeInfoOnOff.selectedProperty().set(layout.layoutRoot.queryComponentOfClassExists(RuntimeInfo::class.java))

        barOnOff.selectedProperty().addListener { _, _, v ->
            if (v) layout.addComponent(barVisualizer, LayoutStorageService.MAIN_BAR_VISUALIZER_ID)
            else layout.layoutRoot.removeComponentOfClass(BarVisualizer::class.java)
            layout.fullUpdate()
        }

        waveformOnOff.selectedProperty().addListener { _, _, v ->
            if (v) layout.addComponent(waveformVisualizer, LayoutStorageService.MAIN_WAVEFORM_VISUALIZER_ID)
            else layout.layoutRoot.removeComponentOfClass(WaveformVisualizer::class.java)
            layout.fullUpdate()
        }

        fftInfoOnOff.selectedProperty().addListener { _, _, v ->
            if (v) layout.addComponent(fftInfo, LayoutStorageService.MAIN_FFT_INFO_ID)
            else layout.layoutRoot.removeComponentOfClass(FFTInfo::class.java)
            layout.fullUpdate()
        }

        runtimeInfoOnOff.selectedProperty().addListener { _, _, v ->
            if (v) layout.addComponent(runtimeInfo, LayoutStorageService.MAIN_RUNTIME_INFO_ID)
            else layout.layoutRoot.removeComponentOfClass(RuntimeInfo::class.java)
            layout.fullUpdate()
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

        val layout = layoutService.getMainLayout(waveformVisualizer, barVisualizer, fftInfo, runtimeInfo)
        initializeWindowControls(layout)
        visualizerPane.items.add(layout)
        globalLayoutService.noAutoRemove.add(layout)

        val masterVolumeVisualizer = VolumeVisualizer()
        audioCaptureService.channelVolumes.listeners.add { store ->
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