package me.ksanstone.wavesync.wavesync.gui.utility

import com.huskerdev.openglfx.canvas.GLCanvas
import com.huskerdev.openglfx.canvas.GLProfile
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
            glCanvas = GLCanvas(LWJGLExecutor.LWJGL_MODULE, profile = GLProfile.Core)
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
}