package me.ksanstone.wavesync.wavesync.gui.utility

import com.huskerdev.openglfx.canvas.GLCanvas
import javafx.scene.Node
import javafx.scene.canvas.Canvas

class CanvasNode(val canvas: Canvas?, val glCanvas: GLCanvas?) {

    val isCanvas: Boolean
        get() = canvas != null

    val isOpenGL: Boolean
        get() = glCanvas != null

    val backingNode: Node
        get() = if (isCanvas) canvas!! else glCanvas!!

    var width: Double
        get() = if(this.isCanvas) this.canvas!!.width else this.glCanvas!!.width
        set(value) = if(this.isCanvas) this.canvas!!.width = value else this.glCanvas!!.resize(width, this.glCanvas.height)

    var height: Double
        get() = if(this.isCanvas) this.canvas!!.height else this.glCanvas!!.height
        set(value) = if(this.isCanvas) this.canvas!!.height = value else this.glCanvas!!.resize(this.glCanvas.width, value)
}