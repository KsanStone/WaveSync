package me.ksanstone.wavesync.wavesync.service

import jakarta.annotation.PostConstruct
import javafx.beans.property.SimpleStringProperty
import javafx.beans.property.StringProperty
import javafx.geometry.Orientation
import javafx.scene.Node
import me.ksanstone.wavesync.wavesync.gui.component.layout.drag.DragLayout
import me.ksanstone.wavesync.wavesync.gui.component.layout.drag.data.DragLayoutLeaf
import me.ksanstone.wavesync.wavesync.gui.component.layout.drag.data.DragLayoutNode
import me.ksanstone.wavesync.wavesync.gui.component.visualizer.BarVisualizer
import me.ksanstone.wavesync.wavesync.gui.component.visualizer.WaveformVisualizer
import org.springframework.stereotype.Service

@Service
class LayoutService(
    private val layoutSerializerService: DragLayoutSerializerService,
    private val preferenceService: PreferenceService
) {

    private val mainLayout: StringProperty = SimpleStringProperty("")

    @PostConstruct
    fun init() {
        preferenceService.registerProperty(mainLayout, "layout", this::class.java, "main")
    }

    fun constructDefaultLayout(wVis: WaveformVisualizer, bVis: BarVisualizer): DragLayoutNode {
        return DragLayoutNode(
            orientation = Orientation.VERTICAL,
            parent = null,
            children = mutableListOf(
                DragLayoutLeaf(component = bVis, id = "barVisualizer"),
                DragLayoutLeaf(component = wVis, id = "waveformVisualizer")
            ),
            dividerLocations = mutableListOf(0.5),
            id = "",
            dividers = mutableListOf()
        )
    }

    fun getMainLayout(wVis: WaveformVisualizer, bVis: BarVisualizer): DragLayout {
        val node = try {
            layoutSerializerService.deserialize(mainLayout.get()) {
                mapOf<String, Node>("waveformVisualizer" to wVis, "barVisualizer" to bVis)[it]
            }
        } catch (e: Exception) {
            println(e)
            constructDefaultLayout(wVis, bVis)
        }
        val layout = DragLayout()
        layout.load(node)
        layout.addLayoutChangeListener {
            mainLayout.set(layoutSerializerService.serialize(it))
        }
        return layout
    }

}