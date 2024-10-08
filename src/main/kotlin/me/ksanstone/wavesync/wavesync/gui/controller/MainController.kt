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
    private var barVisualizers: List<BarVisualizer> =
        List(SUPPORTED_CHANNELS) { BarVisualizer().also { vis -> vis.channelProperty.value = it } }
    private var waveformVisualizers: List<WaveformVisualizer> =
        List(SUPPORTED_CHANNELS) { WaveformVisualizer().also { vis -> vis.channelProperty.value = it } }
    private var spectrogramVisualizer: SpectrogramVisualizer = SpectrogramVisualizer()
    private var extendedWaveformVisualizer: ExtendedWaveformVisualizer = ExtendedWaveformVisualizer()
    private var vectorScopeVisualizer: VectorScopeVisualizer = VectorScopeVisualizer()
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
                    barVisualizers[0].lowPass.get(),
                    barVisualizers[0].highPass.get(),
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
        val clazz: Class<*>, val node: Node, val id: String, val channelId: Int = 0, val menuName: String = "",
    ) {
        lateinit var check: CheckMenuItem
    }

    private fun setupComponent(layout: DragLayout, component: CompComponentToggle, nested: Boolean): MenuItem {
        val item = if (nested) {
            val label = CheckMenuItem()
            label.textProperty().bind(audioCaptureService.getChannelLabelProperty(component.channelId).map { it.label })
            label
        } else {
            CheckMenuItem(
                localizationService.get(
                    "nav.window.toggle." + component.id
                )
            )
        }
        component.check = item
        component.check.selectedProperty().set(globalLayoutService.queryComponentOfExists(component.id))
        component.check.selectedProperty().addListener { _, _, v ->
            if (v) layout.addComponent(component.node, component.id)
            else globalLayoutService.removeComponent(component.id)
            layout.fullUpdate()
        }
        return item
    }

    private fun initializeWindowControls(layout: DragLayout, list: List<List<CompComponentToggle>>) {
        list.forEach {
            if (it.size == 1) {
                componentToggles.items.add(setupComponent(layout, it[0], false))
            } else {
                val menu = Menu(it[0].menuName)
                for (child in it) {
                    menu.items.add(setupComponent(layout, child, true))
                }
                componentToggles.items.add(menu)
            }
        }

        globalLayoutService.layoutRemovalListeners.add(Consumer {
            it.layoutRoot.iterateComponents {
                list.forEach { predefined ->
                    predefined.forEach { component ->
                        if (component.node == it.node) component.check.selectedProperty().set(false)
                    }
                }
            }
        })
    }

    private fun registerComponents(cards: List<List<CompComponentToggle>>) {
        val compMap = cards.flatten().associate { it.id to it.node }
        layoutService.registerNodeFactory {
            compMap[it]
        }

        val framerate = WaveSyncBootApplication.applicationContext.getBean(WaveSyncBootApplication::class.java)
            .findHighestRefreshRate()

        cards.forEach { card ->
            card.forEach {
                if (it.node is AutoCanvas) {
                    it.node.registerPreferences(it.id, preferenceService)
                    it.node.info.bind(infoShown)
                    it.node.framerate.set(framerate)
                    it.node.initializeSettingMenu()
                    it.node.registerListeners(audioCaptureService)
                }
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
            listOf(CompComponentToggle(FFTInfo::class.java, fftInfo, MAIN_FFT_INFO_ID)),
            waveformVisualizers.mapIndexed { index, waveformVisualizer ->
                CompComponentToggle(
                    WaveformVisualizer::class.java,
                    waveformVisualizer,
                    "$MAIN_WAVEFORM_VISUALIZER_ID-Channel-${index}",
                    channelId = index,
                    menuName = localizationService.get("nav.window.toggle.$MAIN_WAVEFORM_VISUALIZER_ID")
                )
            },
            barVisualizers.mapIndexed { index, barVisualizer ->
                CompComponentToggle(
                    BarVisualizer::class.java,
                    barVisualizer,
                    "$MAIN_BAR_VISUALIZER_ID-Channel-${index}",
                    channelId = index,
                    menuName = localizationService.get("nav.window.toggle.$MAIN_BAR_VISUALIZER_ID")
                )
            },
            listOf(CompComponentToggle(RuntimeInfo::class.java, runtimeInfo, MAIN_RUNTIME_INFO_ID)),
            listOf(
                CompComponentToggle(
                    ExtendedWaveformVisualizer::class.java,
                    extendedWaveformVisualizer,
                    MAIN_EXTENDED_WAVEFORM_VISUALIZER_ID
                )
            ),
            listOf(CompComponentToggle(SpectrogramVisualizer::class.java, spectrogramVisualizer, MAIN_SPECTROGRAM_ID)),
            listOf(CompComponentToggle(VectorScopeVisualizer::class.java, vectorScopeVisualizer, MAIN_VECTORSCOPE_ID)),
        )
        registerComponents(list)

        audioCaptureService.fftSize.addListener { _ -> refreshInfoLabel() }
//        barVisualizer.highPass.addListener { _ -> refreshInfoLabel() }
//        barVisualizer.lowPass.addListener { _ -> refreshInfoLabel() }
// TODO bind them together, figureout what to do with this label

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
            .bind(list[0][0].check.selectedProperty().not().and(bottomBar.widthProperty().greaterThan(1000.0)))
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
        const val SUPPORTED_CHANNELS = 5
    }
}