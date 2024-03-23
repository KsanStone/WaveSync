package me.ksanstone.wavesync.wavesync.service

import jakarta.annotation.PostConstruct
import javafx.application.Platform
import javafx.collections.ObservableList
import javafx.event.EventType
import javafx.geometry.Bounds
import javafx.geometry.Point2D
import javafx.scene.Node
import javafx.scene.Scene
import javafx.scene.control.SplitPane
import javafx.scene.input.DragEvent
import javafx.scene.layout.Pane
import javafx.stage.Stage
import javafx.stage.Window
import me.ksanstone.wavesync.wavesync.gui.component.layout.drag.DragLayout
import me.ksanstone.wavesync.wavesync.gui.component.layout.drag.data.DragLayoutNode
import me.ksanstone.wavesync.wavesync.gui.initializer.AutoDisposalMode
import me.ksanstone.wavesync.wavesync.gui.initializer.WaveSyncStageInitializer
import org.springframework.stereotype.Service
import java.util.*

@Service
class GlobalLayoutService(
    private val waveSyncStageInitializer: WaveSyncStageInitializer,
    private val layoutStorageService: LayoutStorageService,
    private val stageSizingService: StageSizingService
) {

    private lateinit var windowList: ObservableList<Window>
    var currentTransaction: NodeTransaction? = null
    var noAutoRemove = mutableSetOf<DragLayout>()
    private val stageMap = mutableMapOf<DragLayout, Stage>()

    @PostConstruct
    fun initialize() {
        windowList = Window.getWindows()
    }

    private fun getScreenBounds(node: Node): Bounds {
        val localBounds = node.boundsInLocal
        return node.localToScreen(localBounds)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Node> getNodesOfClass(root: Node, `class`: Class<T>): List<T> {
        val nodes = mutableListOf<T>()
        if (root.javaClass == `class`) nodes.add(root as T)
        if (root is SplitPane) {
            root.items.forEach {
                nodes.addAll(getNodesOfClass(it, `class`))
            }
        } else if (root is Pane) {
            root.children.forEach {
                nodes.addAll(getNodesOfClass(it, `class`))
            }
        }
        return nodes
    }

    fun <T : Node> getNodes(`class`: Class<T>): List<T> {
        val nodes = mutableListOf<T>()
        windowList.forEach { nodes.addAll(getNodesOfClass(it.scene.root, `class`)) }
        return nodes
    }

    fun <T : Node> getBoundedNodes(`class`: Class<T>): List<Pair<Bounds, T>> {
        return getNodes(`class`).map { getScreenBounds(it) to it }
    }

    fun <T : Node> getNode(`class`: Class<T>, p: Point2D): Pair<Bounds, T>? {
        return getBoundedNodes(`class`).find { it.first.contains(p) }
    }

    fun startTransaction(nodeId: String, origin: DragLayout) {
        this.currentTransaction = NodeTransaction(nodeId, origin, null)
    }

    private fun fakeDragEvent(type: EventType<DragEvent>, p: Point2D, it: DragLayout, root: DragLayoutNode? = null) {
        when (type) {
            DragEvent.DRAG_EXITED -> it.dragExited()
            DragEvent.DRAG_OVER -> it.dragOver(
                it.screenToLocal(p),
                DragLayout.encodeNodeId(currentTransaction!!.nodeId)
            )

            DragEvent.DRAG_ENTERED -> {}
            DragEvent.DRAG_DROPPED -> it.dragDropped(root!!, currentTransaction!!.nodeId, it.screenToLocal(p))
        }
    }

    @Synchronized
    fun updateTransaction(p: Point2D) {
        if (currentTransaction == null) return
        val node = getNode(DragLayout::class.java, p)
        if (node == null) {
            currentTransaction!!.lastSeenLayout?.let {
                fakeDragEvent(DragEvent.DRAG_EXITED, p, it)
            }
            currentTransaction!!.lastSeenLayout = null
        } else {
            if (currentTransaction!!.lastSeenLayout == node.second) {
                fakeDragEvent(DragEvent.DRAG_OVER, p, node.second)
            } else {
                currentTransaction!!.lastSeenLayout?.let { fakeDragEvent(DragEvent.DRAG_EXITED, p, it) }
                fakeDragEvent(DragEvent.DRAG_ENTERED, p, node.second)
                currentTransaction!!.lastSeenLayout = node.second
            }
        }
    }

    @Synchronized
    fun finishTransaction(p: Point2D) {
        if (currentTransaction == null) return
        val targetNode = getNode(DragLayout::class.java, p)?.second

        if (targetNode == null) {
            val cutNode = currentTransaction!!.origin.layoutRoot.cutComponentLeaf(currentTransaction!!.nodeId) ?: return
            val id = UUID.randomUUID().toString()
            val newLayout = layoutStorageService.constructSideLayout(cutNode, id)

            val stage =
                waveSyncStageInitializer.createGeneralPurposeAppFrame(id, AutoDisposalMode.USER)
                { layoutStorageService.destructLayout(newLayout) }
            stageMap[newLayout] = stage
            val scene = Scene(newLayout)
            stage.scene = scene
            stage.width = cutNode.component!!.boundsInLocal.width
            stage.height = cutNode.component!!.boundsInLocal.height
            stage.x = p.x - stage.width / 2
            stage.y = p.y - stage.height / 2
            stage.show()
        } else {
            fakeDragEvent(
                DragEvent.DRAG_DROPPED, p, targetNode, currentTransaction!!.origin
                    .layoutRoot
            )
            fakeDragEvent(DragEvent.DRAG_EXITED, p, targetNode)
            targetNode.fullUpdate()
        }
        currentTransaction!!.origin.fullUpdate()
        if (currentTransaction!!.origin.layoutRoot.isEmpty() && !noAutoRemove.contains(currentTransaction!!.origin)) {
            stageMap.remove(currentTransaction!!.origin)?.let {
                it.close()
                val id = stageSizingService.findId(it) ?: return
                stageSizingService.unregisterStage(id)
                layoutStorageService.destructLayout(id)
            }
        }
        currentTransaction = null
    }

    fun loadLayouts() {
        Platform.runLater {
            layoutStorageService.layouts.stream().filter { it.windowId != null }.forEach(this::reOpenSideLayout)
        }
    }

    protected fun reOpenSideLayout(appLayout: AppLayout) {
        val stage = waveSyncStageInitializer.createGeneralPurposeAppFrame(
            appLayout.windowId!!,
            AutoDisposalMode.USER
        ) { layoutStorageService.destructLayout(appLayout.layout) }
        stage.scene = Scene(appLayout.layout)
        stage.show()
    }

    data class NodeTransaction(
        val nodeId: String,
        val origin: DragLayout,
        var lastSeenLayout: DragLayout?
    )

}