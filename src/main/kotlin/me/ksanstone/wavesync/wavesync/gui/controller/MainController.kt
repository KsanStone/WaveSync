package me.ksanstone.wavesync.wavesync.gui.controller

import javafx.application.Platform
import javafx.beans.property.SimpleBooleanProperty
import javafx.fxml.FXML
import javafx.fxml.Initializable
import javafx.scene.Node
import javafx.scene.control.*
import javafx.scene.layout.HBox
import me.ksanstone.wavesync.wavesync.WaveSyncBootApplication
import me.ksanstone.wavesync.wavesync.gui.component.info.FFTInfo
import me.ksanstone.wavesync.wavesync.gui.component.info.RuntimeInfo
import me.ksanstone.wavesync.wavesync.gui.component.layout.drag.DragLayout
import me.ksanstone.wavesync.wavesync.gui.component.visualizer.*
import me.ksanstone.wavesync.wavesync.gui.initializer.MenuInitializer
import me.ksanstone.wavesync.wavesync.gui.utility.AutoCanvas
import me.ksanstone.wavesync.wavesync.service.*
import me.ksanstone.wavesync.wavesync.service.LayoutStorageService.Companion.MAIN_BAR_VISUALIZER_ID
import me.ksanstone.wavesync.wavesync.service.LayoutStorageService.Companion.MAIN_EXTENDED_WAVEFORM_VISUALIZER_ID
import me.ksanstone.wavesync.wavesync.service.LayoutStorageService.Companion.MAIN_FFT_INFO_ID
import me.ksanstone.wavesync.wavesync.service.LayoutStorageService.Companion.MAIN_RUNTIME_INFO_ID
import me.ksanstone.wavesync.wavesync.service.LayoutStorageService.Companion.MAIN_SPECTROGRAM_ID
import me.ksanstone.wavesync.wavesync.service.LayoutStorageService.Companion.MAIN_VECTORSCOPE_ID
import me.ksanstone.wavesync.wavesync.service.LayoutStorageService.Companion.MAIN_WAVEFORM_VISUALIZER_ID
import java.net.URL
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer

class MainController : Initializable {

    @FXML
    lateinit var componentToggles: MenuButton

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
    private var barVisualizer: BarVisualizer = BarVisualizer()
    private var waveformVisualizer: WaveformVisualizer = WaveformVisualizer()
    private var spectrogramVisualizer: SpectrogramVisualizer = SpectrogramVisualizer()
    private var extendedWaveformVisualizer: ExtendedWaveformVisualizer = ExtendedWaveformVisualizer()
    private var vectorscopeVisualizer: VectorScopeVisualizer = VectorScopeVisualizer()
    private var fftInfo: FFTInfo = FFTInfo()
    private var runtimeInfo: RuntimeInfo = RuntimeInfo()
    private var layoutService: LayoutStorageService
    private var globalLayoutService: GlobalLayoutService
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
    fun refreshDeviceList(): CompletableFuture<Void> = CompletableFuture.runAsync {
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
            val similarDevice = audioCaptureService.findSimilarAudioSource(lastDeviceId!!, deviceList) ?: run {
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
        val clazz: Class<*>, val node: Node, val id: String
    ) {
        lateinit var check: CheckMenuItem
    }

    private fun initializeWindowControls(layout: DragLayout, list: List<CompComponentToggle>) {

        list.forEach {
            val item = CheckMenuItem(localizationService.get("nav.window.toggle." + it.id))
            componentToggles.items.add(item)
            it.check = item
        }

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

    fun registerComponents(cards: List<CompComponentToggle>) {
        val compMap = cards.associate { it.id to it.node }
        layoutService.registerNodeFactory {
            compMap[it]
        }

        val framerate = WaveSyncBootApplication.applicationContext.getBean(WaveSyncBootApplication::class.java)
            .findHighestRefreshRate()

        cards.forEach {
            if (it.node is AutoCanvas) {
                it.node.registerPreferences("main", preferenceService)
                it.node.info.bind(infoShown)
                it.node.framerate.set(framerate)
                it.node.initializeSettingMenu()
            }
        }

    }

    @FXML
    override fun initialize(location: URL?, resources: ResourceBundle?) {
        this.resources = resources!!

        CompletableFuture.runAsync {
            audioCaptureService.initLatch.await()
        }.thenRun { refreshDeviceList().thenRun { selectDefaultDevice() } }

        val list = listOf(
            CompComponentToggle(FFTInfo::class.java, fftInfo, MAIN_FFT_INFO_ID),
            CompComponentToggle(WaveformVisualizer::class.java, waveformVisualizer, MAIN_WAVEFORM_VISUALIZER_ID),
            CompComponentToggle(BarVisualizer::class.java, barVisualizer, MAIN_BAR_VISUALIZER_ID),
            CompComponentToggle(RuntimeInfo::class.java, runtimeInfo, MAIN_RUNTIME_INFO_ID),
            CompComponentToggle(ExtendedWaveformVisualizer::class.java, extendedWaveformVisualizer, MAIN_EXTENDED_WAVEFORM_VISUALIZER_ID),
            CompComponentToggle(SpectrogramVisualizer::class.java, spectrogramVisualizer, MAIN_SPECTROGRAM_ID),
            CompComponentToggle(VectorScopeVisualizer::class.java, vectorscopeVisualizer, MAIN_VECTORSCOPE_ID),
        )
        registerComponents(list)

        audioCaptureService.registerFFTObserver(barVisualizer::handleFFT)
        audioCaptureService.registerFFTObserver(spectrogramVisualizer::handleFFT)
        audioCaptureService.registerSampleObserver(waveformVisualizer::handleSamples)
        audioCaptureService.registerSampleObserver(extendedWaveformVisualizer::handleSamples)

        audioCaptureService.fftSize.addListener { _ -> refreshInfoLabel() }
        barVisualizer.highPass.addListener { _ -> refreshInfoLabel() }
        barVisualizer.lowPass.addListener { _ -> refreshInfoLabel() }

        preferenceService.registerProperty(infoShown, "graphInfoShown", this.javaClass)

        layoutService.loadLayouts()
        val layout = layoutService.getMainLayout()
        initializeWindowControls(layout, list)
        visualizerPane.items.add(layout)
        globalLayoutService.noAutoRemove.add(layout)
        globalLayoutService.loadLayouts()

        val masterVolumeVisualizer = VolumeVisualizer()
        audioCaptureService.channelVolumes.listeners.add { store ->
            masterVolumeVisualizer.values = (1 until store.channels()).map { store[it].data[0].toDouble() }
            masterVolumeVisualizer.labels = (1 until store.channels()).map { store[it].label }
        }

        val quickInfo = FFTInfo(true)
        quickInfo.visibleProperty()
            .bind(list[0].check.selectedProperty().not().and(bottomBar.widthProperty().greaterThan(1000.0)))
        quickInfo.managedProperty().bind(quickInfo.visibleProperty())
        quickInfo.maxHeight = 20.0
        quickInfo.maxWidth = 100.0
        bottomBar.children.add(quickInfo)
        bottomBar.children.add(masterVolumeVisualizer)
        bottomBar.visibleProperty().bind(recordingModeService.recordingMode.not())
        bottomBar.managedProperty().bind(bottomBar.visibleProperty())
    }

    companion object {
        lateinit var instance: MainController
    }
}