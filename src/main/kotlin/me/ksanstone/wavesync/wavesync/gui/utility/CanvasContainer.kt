package me.ksanstone.wavesync.wavesync.gui.utility

import com.huskerdev.grapl.gl.GLProfile
import com.huskerdev.openglfx.canvas.GLCanvas
import com.huskerdev.openglfx.lwjgl.LWJGLExecutor
import javafx.beans.property.ReadOnlyBooleanProperty
import javafx.event.EventHandler
import javafx.geometry.Point2D
import javafx.scene.Node
import javafx.scene.canvas.Canvas
import javafx.scene.canvas.GraphicsContext
import javafx.scene.input.MouseEvent

class CanvasContainer(private val useGL: Boolean) {

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
        glCanvas.repaint()
    }

    private var disposed: Boolean = false
    private var initialized: Boolean = false

    /**
     * Returns true if the canvas needs to be replaced
     */
    fun updateUsedState(state: Boolean): Boolean {
        if (!state && useGL && !disposed && initialized) { // Free up resources for background canvases
            glCanvas.dispose()
            disposed = true
            initialized = false
        } else if (state && useGL && disposed && !initialized) { // Create new canvas instance, old one was disposed
            createGlCanvas()
            disposed = false
            return true
        }
        return false
    }

    private fun createGlCanvas() {
        glCanvas = GLCanvas(LWJGLExecutor.LWJGL_MODULE, profile = GLProfile.CORE, swapBuffers = 1)
        glCanvas.addOnInitEvent { initialized = true }
    }
}