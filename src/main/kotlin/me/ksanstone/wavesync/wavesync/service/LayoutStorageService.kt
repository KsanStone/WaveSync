package me.ksanstone.wavesync.wavesync.service

import jakarta.annotation.PostConstruct
import javafx.beans.property.SimpleStringProperty
import javafx.beans.property.StringProperty
import javafx.collections.FXCollections
import javafx.geometry.Orientation
import javafx.scene.Node
import me.ksanstone.wavesync.wavesync.gui.component.info.FFTInfo
import me.ksanstone.wavesync.wavesync.gui.component.info.RuntimeInfo
import me.ksanstone.wavesync.wavesync.gui.component.layout.drag.DragLayout
import me.ksanstone.wavesync.wavesync.gui.component.layout.drag.data.DragLayoutLeaf
import me.ksanstone.wavesync.wavesync.gui.component.layout.drag.data.DragLayoutNode
import me.ksanstone.wavesync.wavesync.gui.component.visualizer.BarVisualizer
import me.ksanstone.wavesync.wavesync.gui.component.visualizer.WaveformVisualizer
import org.springframework.stereotype.Service

@Service
class LayoutStorageService(
    private val layoutSerializerService: DragLayoutSerializerService,
    private val preferenceService: PreferenceService
) {

    private val layoutStorageProperty: StringProperty = SimpleStringProperty("")
    private val layouts: MutableList<AppLayout> = mutableListOf()
    private lateinit var nodeFactory: DragLayoutSerializerService.NodeFactory

    @PostConstruct
    fun init() {
        preferenceService.registerProperty(layoutStorageProperty, "layout", this::class.java, "main")
    }

    fun createDefaultNodeFactory(
        wVis: WaveformVisualizer,
        bVis: BarVisualizer,
        fftInfo: FFTInfo,
        runtimeInfo: RuntimeInfo
    ) {
        this.nodeFactory =
            DragLayoutSerializerService.NodeFactory {
                mapOf<String, Node>(
                    MAIN_WAVEFORM_VISUALIZER_ID to wVis,
                    MAIN_BAR_VISUALIZER_ID to bVis,
                    MAIN_FFT_INFO_ID to fftInfo,
                    MAIN_RUNTIME_INFO_ID to runtimeInfo
                )[it]
            }
    }

    fun constructDefaultLayout(wVis: WaveformVisualizer, bVis: BarVisualizer): DragLayoutNode {
        return DragLayoutNode(
            orientation = Orientation.VERTICAL,
            parent = null,
            children = FXCollections.observableList(
                mutableListOf(
                    DragLayoutLeaf(component = bVis, id = MAIN_BAR_VISUALIZER_ID),
                    DragLayoutLeaf(component = wVis, id = MAIN_WAVEFORM_VISUALIZER_ID)
                )
            ),
            dividerLocations = mutableListOf(0.5),
            id = "",
            dividers = mutableListOf()
        )
    }

    fun getMainLayout(): DragLayout {
        val node = try {
            layoutSerializerService.deserialize(layoutStorageProperty.get(), nodeFactory)
        } catch (e: Exception) {
            println(e)
            constructDefaultLayout(
                nodeFactory.createNode(MAIN_WAVEFORM_VISUALIZER_ID) as WaveformVisualizer, nodeFactory.createNode(
                    MAIN_BAR_VISUALIZER_ID) as BarVisualizer
            )
        }
        val layout = DragLayout()
        layout.load(node)
        layout.addLayoutChangeListener {
            layoutStorageProperty.set(layoutSerializerService.serialize(it))
        }
        layout.layoutRoot.simplify()
        return layout
    }

    fun constructSideLayout(cutNode: DragLayoutLeaf): DragLayout {
        val newLayout = DragLayout()
        newLayout.layoutRoot.children.add(cutNode)
        newLayout.fullUpdate()
        return newLayout
    }

    companion object {
        const val MAIN_BAR_VISUALIZER_ID = "barVisualizer"
        const val MAIN_WAVEFORM_VISUALIZER_ID = "waveformVisualizer"
        const val MAIN_FFT_INFO_ID = "fftInfo"
        const val MAIN_RUNTIME_INFO_ID = "runtimeInfo"
    }
}

data class AppLayout(
    val id: String,
    val windowId: String,
    val layout: DragLayout
)