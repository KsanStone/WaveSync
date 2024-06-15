package me.ksanstone.wavesync.wavesync.gui.controller

import javafx.application.Platform
import javafx.beans.property.SimpleBooleanProperty
import javafx.fxml.FXML
import javafx.fxml.Initializable
import javafx.scene.Node
import javafx.scene.control.CheckMenuItem
import javafx.scene.control.ChoiceBox
import javafx.scene.control.Label
import javafx.scene.control.SplitPane
import javafx.scene.layout.HBox
import me.ksanstone.wavesync.wavesync.WaveSyncBootApplication
import me.ksanstone.wavesync.wavesync.gui.component.info.FFTInfo
import me.ksanstone.wavesync.wavesync.gui.component.info.RuntimeInfo
import me.ksanstone.wavesync.wavesync.gui.component.layout.drag.DragLayout
import me.ksanstone.wavesync.wavesync.gui.component.visualizer.*
import me.ksanstone.wavesync.wavesync.gui.initializer.MenuInitializer
import me.ksanstone.wavesync.wavesync.service.*
import java.net.URL
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer

class MainController : Initializable {

    @FXML
    lateinit var extendedWaveformVisualizerOnOff: CheckMenuItem

    @FXML
    lateinit var spectrogramVisualizerOnOff: CheckMenuItem

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
    private var spectrogramVisualizer: SpectrogramVisualizer
    private var extendedWaveformVisualizer: ExtendedWaveformVisualizer
    private var fftInfo: FFTInfo
    private var layoutService: LayoutStorageService
    private var globalLayoutService: GlobalLayoutService
    private var runtimeInfo: RuntimeInfo
    private val isRefreshing = AtomicBoolean(false)
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
        spectrogramVisualizer = SpectrogramVisualizer()
        extendedWaveformVisualizer = ExtendedWaveformVisualizer()
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
                    barVisualizer.highPass.get(),
                    localizationService.numberFormat
                )
            }
        }
    }

    @FXML
    fun refreshDeviceList(): CompletableFuture<Void> =
        CompletableFuture.runAsync {
            if (!isRefreshing.compareAndSet(false, true)) return@runAsync
            audioCaptureService.stopCapture()
            val supported = audioCaptureService.findSupportedSources()
            deviceList.clear()
            deviceList.addAll(supported)
            Platform.runLater {
                audioDeviceListComboBox.items.clear()
                audioDeviceListComboBox.items.addAll(deviceList.map { it.name })
            }
            refreshInfoLabel()

            if (lastDeviceId != null) {
                val similarDevice =
                    audioCaptureService.findSimilarAudioSource(lastDeviceId!!, deviceList) ?: run {
                        isRefreshing.set(false)
                        return@runAsync
                    }

                setByName(similarDevice.name)
            }
            isRefreshing.set(false)
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

    data class CompComponentToggle(
        val check: CheckMenuItem, val clazz: Class<*>, val node: Node, val id: String
    )

    private fun initializeWindowControls(layout: DragLayout) {
        val list = listOf(
            CompComponentToggle(
                waveformOnOff,
                WaveformVisualizer::class.java,
                waveformVisualizer,
                LayoutStorageService.MAIN_WAVEFORM_VISUALIZER_ID
            ),
            CompComponentToggle(
                barOnOff,
                BarVisualizer::class.java,
                barVisualizer,
                LayoutStorageService.MAIN_BAR_VISUALIZER_ID
            ),
            CompComponentToggle(fftInfoOnOff, FFTInfo::class.java, fftInfo, LayoutStorageService.MAIN_FFT_INFO_ID),
            CompComponentToggle(
                runtimeInfoOnOff,
                RuntimeInfo::class.java,
                runtimeInfo,
                LayoutStorageService.MAIN_RUNTIME_INFO_ID
            ),
            CompComponentToggle(
                extendedWaveformVisualizerOnOff,
                ExtendedWaveformVisualizer::class.java,
                extendedWaveformVisualizer,
                LayoutStorageService.MAIN_EXTENDED_WAVEFORM_VISUALIZER_ID
            ),
            CompComponentToggle(
                spectrogramVisualizerOnOff,
                SpectrogramVisualizer::class.java,
                spectrogramVisualizer,
                LayoutStorageService.MAIN_SPECTROGRAM_ID
            ),
        )

        list.forEach {
            it.check.selectedProperty().set(globalLayoutService.queryComponentOfClassExists(it.clazz))
        }

        list.forEach {
            it.check.selectedProperty().addListener { _, _, v ->
                if (v) layout.addComponent(it.node, it.id)
                else layout.layoutRoot.removeComponentOfClass(it.clazz)
                layout.fullUpdate()
            }
        }

        globalLayoutService.layoutRemovalListeners.add(Consumer {
            it.layoutRoot.iterateComponents {
                list.forEach { predefined ->
                    if (predefined.node == it.node) predefined.check.selectedProperty().set(false)
                }
            }
        })
    }

    @FXML
    override fun initialize(location: URL?, resources: ResourceBundle?) {
        this.resources = resources!!

        CompletableFuture.runAsync {
            audioCaptureService.initLatch.await()
        }.thenRun { refreshDeviceList().thenRun { selectDefaultDevice() } }

        audioCaptureService.registerFFTObserver(barVisualizer::handleFFT)
        audioCaptureService.registerFFTObserver(spectrogramVisualizer::handleFFT)
        audioCaptureService.registerSampleObserver(waveformVisualizer::handleSamples)
        audioCaptureService.registerSampleObserver(extendedWaveformVisualizer::handleSamples)

        audioCaptureService.fftSize.addListener { _ -> refreshInfoLabel() }
        barVisualizer.highPass.addListener { _ -> refreshInfoLabel() }
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

        layoutService.createDefaultNodeFactory(
            waveformVisualizer,
            barVisualizer,
            fftInfo,
            runtimeInfo,
            extendedWaveformVisualizer,
            spectrogramVisualizer
        )

        barVisualizer.registerPreferences("main", preferenceService)
        barVisualizer.initializeSettingMenu()
        waveformVisualizer.registerPreferences("main", preferenceService)
        waveformVisualizer.initializeSettingMenu()
        extendedWaveformVisualizer.registerPreferences("main", preferenceService)
        extendedWaveformVisualizer.initializeSettingMenu()
        spectrogramVisualizer.registerPreferences("main", preferenceService)
        spectrogramVisualizer.initializeSettingMenu()
        preferenceService.registerProperty(infoShown, "graphInfoShown", this.javaClass)

        layoutService.loadLayouts()
        val layout = layoutService.getMainLayout()
        initializeWindowControls(layout)
        visualizerPane.items.add(layout)
        globalLayoutService.noAutoRemove.add(layout)
        globalLayoutService.loadLayouts()

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