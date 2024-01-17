package me.ksanstone.wavesync.wavesync.gui.utility

import com.huskerdev.openglfx.canvas.GLCanvas
import javafx.scene.Node
import javafx.scene.canvas.Canvas
import javafx.scene.layout.BorderPane

class CanvasNode(val canvas: Canvas?, val glCanvas: GLCanvas?) {

    private val cumPain = BorderPane()

    private var w: Double = 0.0
    private var h: Double = 0.0

    init {
        if (glCanvas != null)
            cumPain.center = glCanvas
    }

    val isCanvas: Boolean
        get() = canvas != null

    val isOpenGL: Boolean
        get() = glCanvas != null

    val backingNode: Node
        get() = if (isCanvas) canvas!! else cumPain

    var width: Double
        get() = if(this.isCanvas) this.canvas!!.width else this.w
        set(value) = if(this.isCanvas) this.canvas!!.width = value else this.w = value

    var height: Double
        get() = if(this.isCanvas) this.canvas!!.height else this.h
        set(value) = if(this.isCanvas) this.canvas!!.height = value else this.h = value
}