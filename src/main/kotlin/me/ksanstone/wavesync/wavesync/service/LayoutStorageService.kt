package me.ksanstone.wavesync.wavesync.service

import jakarta.annotation.PostConstruct
import javafx.beans.property.SimpleStringProperty
import javafx.beans.property.StringProperty
import javafx.collections.FXCollections
import javafx.geometry.Orientation
import javafx.stage.Stage
import me.ksanstone.wavesync.wavesync.gui.component.layout.drag.DragLayout
import me.ksanstone.wavesync.wavesync.gui.component.layout.drag.data.DragLayoutLeaf
import me.ksanstone.wavesync.wavesync.gui.component.layout.drag.data.DragLayoutNode
import me.ksanstone.wavesync.wavesync.gui.component.visualizer.BarVisualizer
import me.ksanstone.wavesync.wavesync.gui.component.visualizer.WaveformVisualizer
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
open class LayoutStorageService(
    private val layoutSerializerService: DragLayoutSerializerService,
    private val preferenceService: PreferenceService,
    private val stageSizingService: StageSizingService
) {

    private val logger = LoggerFactory.getLogger(this.javaClass)
    private val layoutStorageProperty: StringProperty = SimpleStringProperty("")

    lateinit var nodeFactory: DragLayoutSerializerService.NodeFactory

    val layouts: MutableList<AppLayout> = mutableListOf()

    @PostConstruct
    fun init() {
        preferenceService.registerProperty(layoutStorageProperty, "layout", this::class.java, "main")
    }

    fun registerNodeFactory(
        factory: DragLayoutSerializerService.NodeFactory
    ) {
        this.nodeFactory = factory
    }

    fun loadLayouts() {
        try {
            val layoutData = layoutSerializerService.deserializeFull(layoutStorageProperty.get(), nodeFactory)
            layoutData.forEach {
                layouts.add(
                    AppLayout(it.first, it.second, createLayout(it.third.toLeaf())).apply {
                        this.layout.addLayoutChangeListener { save() }
                    }
                )
            }
        } catch (e: Exception) {
            if (e.message != "Input cannot be empty")
                logger.warn("Layout load fail", e)
        }
    }

    private fun constructDefaultLayout(wVis: WaveformVisualizer, bVis: BarVisualizer): DragLayoutNode {
        return DragLayoutNode(
            orientation = Orientation.VERTICAL,
            parent = null,
            children = FXCollections.observableList(
                mutableListOf(
                    DragLayoutLeaf(component = bVis, id = "$MAIN_BAR_VISUALIZER_ID-Channel-0"),
                    DragLayoutLeaf(component = wVis, id = "$MAIN_WAVEFORM_VISUALIZER_ID-Channel-0")
                )
            ),
            dividerLocations = FXCollections.observableArrayList(0.5),
            id = "",
            dividers = mutableListOf()
        )
    }

    fun getMainLayout(): DragLayout {
        return layouts.stream().filter { it.id == "main" }.findFirst().orElseGet {
            val node = constructDefaultLayout(
                nodeFactory.createNode("$MAIN_WAVEFORM_VISUALIZER_ID-Channel-0")?.node as WaveformVisualizer,
                nodeFactory.createNode("$MAIN_BAR_VISUALIZER_ID-Channel-0")?.node as BarVisualizer
            )
            val layout = DragLayout()
            layout.load(node)
            layout.layoutRoot.simplify()
            AppLayout("main", null, layout).also { al ->
                layout.addLayoutChangeListener { layoutChanged() }
                layouts.add(al)
            }
        }.layout
    }

    fun constructSideLayout(cutNode: DragLayoutLeaf, windowId: String): DragLayout {
        val newLayout = createLayout(cutNode)
        AppLayout(windowId, windowId, newLayout).also { al ->
            newLayout.addLayoutChangeListener { layoutChanged() }
            layouts.add(al)
        }
        save()
        return newLayout
    }

    fun destructLayout(layout: DragLayout) {
        layouts.removeIf { it.layout == layout }
        save()
    }

    fun getLayout(stage: Stage): DragLayout? {
        val id = stageSizingService.findId(stage) ?: return null
        return layouts.find { it.id == id }?.layout
    }

    fun destructLayout(windowId: String): DragLayout? {
        var ret: DragLayout? = null
        layouts.removeIf {
            if (it.windowId == windowId) {
                ret = it.layout; return@removeIf true
            }
            return@removeIf false
        }
        save()
        return ret
    }

    private fun createLayout(node: DragLayoutLeaf): DragLayout {
        val layout = DragLayout()
        layout.layoutRoot.children.add(node)
        layout.fullUpdate()
        return layout
    }

    private fun layoutChanged() {
        save()
    }

    private fun save() {
        logger.info("Saving layout")
        layoutStorageProperty.set(layoutSerializerService.serializeFull(layouts))
    }

    companion object {
        const val MAIN_BAR_VISUALIZER_ID = "barVisualizer"
        const val MAIN_WAVEFORM_VISUALIZER_ID = "waveformVisualizer"
        const val MAIN_EXTENDED_WAVEFORM_VISUALIZER_ID = "extendedWaveformVisualizer"
        const val MAIN_FFT_INFO_ID = "fftInfo"
        const val MAIN_RUNTIME_INFO_ID = "runtimeInfo"
        const val MAIN_SPECTROGRAM_ID = "spectrogram"
        const val MAIN_VECTORSCOPE_ID = "vectorscope"
    }
}

data class AppLayout(
    val id: String,
    val windowId: String?,
    val layout: DragLayout
)