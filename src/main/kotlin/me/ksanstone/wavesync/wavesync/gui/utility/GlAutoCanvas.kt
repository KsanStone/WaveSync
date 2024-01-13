package me.ksanstone.wavesync.wavesync.gui.utility

import com.huskerdev.openglfx.canvas.GLCanvas
import com.huskerdev.openglfx.canvas.GLCanvasAnimator
import com.huskerdev.openglfx.canvas.GLProfile
import com.huskerdev.openglfx.canvas.events.GLDisposeEvent
import com.huskerdev.openglfx.canvas.events.GLInitializeEvent
import com.huskerdev.openglfx.canvas.events.GLRenderEvent
import com.huskerdev.openglfx.canvas.events.GLReshapeEvent
import com.huskerdev.openglfx.lwjgl.LWJGLExecutor

abstract class GlAutoCanvas : AutoCanvas(waitForGlCanvas = true) {

    init {
        glCanvas = createGlCanvas()
        glCanvas!!.animator = GLCanvasAnimator(fps = 60.0)
        init()
    }

    private fun createGlCanvas(): GLCanvas {
        val canvas = GLCanvas(LWJGLExecutor.LWJGL_MODULE, profile = GLProfile.Core)
        canvas.addOnInitEvent(this::initialize)
        canvas.addOnRenderEvent(this::render)
        canvas.addOnReshapeEvent(this::reshape)
        canvas.addOnDisposeEvent(this::dispose)
        return canvas
    }

    abstract fun initialize(event: GLInitializeEvent)
    abstract fun render(event: GLRenderEvent)
    abstract fun reshape(event: GLReshapeEvent)
    abstract fun dispose(event: GLDisposeEvent)

}