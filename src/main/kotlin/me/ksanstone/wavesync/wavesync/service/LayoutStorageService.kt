package me.ksanstone.wavesync.wavesync.service

import jakarta.annotation.PostConstruct
import javafx.collections.FXCollections
import javafx.collections.MapChangeListener
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
    lateinit var nodeFactory: DragLayoutSerializerService.NodeFactory

    val storedLayouts = FXCollections.observableHashMap<String, String>()
    var activeLayoutSetId: String = "layout"
    val activeLayouts: MutableList<AppLayout> = mutableListOf()

    @PostConstruct
    fun init() {
        val preferences = preferenceService.getPreferences(this::class.java).also {
            it.keys().forEach { child -> it[child, null]?.apply { storedLayouts[child] = this } }
        }

        storedLayouts.addListener(MapChangeListener {
            if (it.wasRemoved()) {
                logger.info("--: ${it.key}")
                preferences.remove(it.key)
            }
            if (it.wasAdded()) {
                logger.info("++: ${it.key}")
                preferences.put(it.key, it.valueAdded)
            }
        })
        logger.debug("Fetched ${storedLayouts.size} layouts")
    }

    fun registerNodeFactory(
        factory: DragLayoutSerializerService.NodeFactory
    ) {
        this.nodeFactory = factory
    }

    fun loadLayouts(id: String = activeLayoutSetId) {
        try {
            activeLayouts.clear()
            logger.debug("Loading layout: $id")
            val layoutData = layoutSerializerService.deserializeFull(storedLayouts[id]!!, nodeFactory)
            layoutData.forEach {
                activeLayouts.add(
                    AppLayout(it.first, it.second, createLayout(it.third.toLeaf())).apply {
                        this.layout.addLayoutChangeListener { save() }
                    }
                )
            }
            activeLayoutSetId = id
        } catch (e: Exception) {
            if (e.message != "Input cannot be empty")
                logger.warn("Layout load fail", e)
            logger.debug("Creating a default layout")
            val newMain = getMainLayout()
            save()
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
        return activeLayouts.stream().filter { it.id == "main" }.findFirst().orElseGet {
            val node = constructDefaultLayout(
                nodeFactory.createNode("$MAIN_WAVEFORM_VISUALIZER_ID-Channel-0")?.node as WaveformVisualizer,
                nodeFactory.createNode("$MAIN_BAR_VISUALIZER_ID-Channel-0")?.node as BarVisualizer
            )
            val layout = DragLayout()
            layout.load(node)
            layout.layoutRoot.simplify()
            AppLayout("main", null, layout).also { al ->
                layout.addLayoutChangeListener { layoutChanged() }
                activeLayouts.add(al)
            }
        }.layout
    }

    fun constructSideLayout(cutNode: DragLayoutLeaf, windowId: String): DragLayout {
        val newLayout = createLayout(cutNode)
        AppLayout(windowId, windowId, newLayout).also { al ->
            newLayout.addLayoutChangeListener { layoutChanged() }
            activeLayouts.add(al)
        }
        save()
        return newLayout
    }

    fun destructLayout(layout: DragLayout) {
        activeLayouts.removeIf { it.layout == layout }
        save()
    }

    fun getLayout(stage: Stage): DragLayout? {
        val id = stageSizingService.findId(stage) ?: return null
        return activeLayouts.find { it.id == id }?.layout
    }

    fun destructLayout(windowId: String): DragLayout? {
        var ret: DragLayout? = null
        activeLayouts.removeIf {
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
        save() // save to the default, presets are presets
    }

    fun save(targetId: String = "layout") {
        logger.trace("Saving layout")
        storedLayouts[targetId] = layoutSerializerService.serializeFull(activeLayouts)
        activeLayoutSetId = targetId
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