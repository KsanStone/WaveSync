package me.ksanstone.wavesync.wavesync.gui.utility

import com.huskerdev.openglfx.canvas.GLCanvas
import com.huskerdev.openglfx.canvas.GLProfile
import com.huskerdev.openglfx.internal.GLInteropType
import com.huskerdev.openglfx.lwjgl.LWJGLExecutor
import javafx.beans.property.ReadOnlyBooleanProperty
import javafx.event.EventHandler
import javafx.geometry.Point2D
import javafx.scene.Node
import javafx.scene.canvas.Canvas
import javafx.scene.canvas.GraphicsContext
import javafx.scene.input.MouseEvent
import org.slf4j.LoggerFactory
import kotlin.time.measureTime

class CanvasContainer(private val useGL: Boolean) {

    private val logger = LoggerFactory.getLogger(CanvasContainer::class.java)

    private lateinit var canvas: Canvas
    private lateinit var glCanvas: GLCanvas

    val node: Node
        get() = if (useGL) glCanvas else canvas

    var height: Double
        get() = if (useGL) glCanvas.height else canvas.height
        set(value) = if (useGL) glCanvas.prefHeight = value else canvas.height = value

    var width: Double
        get() = if (useGL) glCanvas.width else canvas.width
        set(value) = if (useGL) glCanvas.prefWidth = value else canvas.width = value

    val graphicsContext2D: GraphicsContext
        get() = canvas.graphicsContext2D

    init {
        if (useGL) {
            createGlCanvas()
        } else {
            canvas = Canvas()
        }
    }

    fun setOnMouseMoved(
        value: EventHandler<in MouseEvent>
    ) {
        node.onMouseMoved = value
    }

    fun hoverProperty(): ReadOnlyBooleanProperty {
        return node.hoverProperty()
    }

    fun localToParent(p: Point2D): Point2D {
        return node.localToParent(p)
    }

    fun resizeRelocate(leftPad: Double, d: Double, width: Double, height: Double) {
        node.resizeRelocate(leftPad, d, width, height)
    }

    fun repaintGl() {
        if (!disposed && initialized)
            glCanvas.repaint()
    }

    private var disposed: Boolean = false
    private var initialized: Boolean = false

    /**
     * Returns the old canvas to be disposed
     */
    fun updateUsedState(state: Boolean): Pair<GLCanvas?, Boolean> {
        if (!state && useGL && !disposed && initialized) { // Free up resources for background canvases
            disposed = true
            initialized = false
            return glCanvas to false
        } else if (state && useGL && disposed && !initialized) { // Create new canvas instance, old one was disposed
            createGlCanvas()
            disposed = false
            return null to true
        }
        return null to false
    }

    private fun createGlCanvas() {
        logger.debug("Supported Interop: {}, Took: {}", GLInteropType.supported, measureTime {
            glCanvas = GLCanvas(LWJGLExecutor.LWJGL_MODULE, profile = GLProfile.Core)
            glCanvas.addOnInitEvent { initialized = true }
        })
    }
}