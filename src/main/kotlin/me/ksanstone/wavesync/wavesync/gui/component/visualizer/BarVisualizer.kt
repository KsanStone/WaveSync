package me.ksanstone.wavesync.wavesync.gui.component.visualizer

import javafx.beans.value.ObservableValue
import javafx.scene.canvas.Canvas
import javafx.scene.layout.AnchorPane
import javafx.scene.layout.Background
import javafx.scene.layout.BackgroundFill
import javafx.scene.paint.Color
import java.util.*
import kotlin.math.abs
import kotlin.math.floor

class BarVisualizer : AnchorPane() {

    lateinit var canvas: Canvas

    init {
        heightProperty().addListener { _: ObservableValue<out Number?>?, _: Number?, _: Number? -> draw() }
        widthProperty().addListener { _: ObservableValue<out Number?>?, _: Number?, _: Number? -> draw() }
        Timer().scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                try {
                    draw()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }, 500, 100) //TODO shutdown on finalize

        background = Background(BackgroundFill(Color.AZURE, null, null))
        canvas = Canvas()
        AnchorPane.setBottomAnchor(canvas, 0.0)
        AnchorPane.setLeftAnchor(canvas, 0.0)
        AnchorPane.setRightAnchor(canvas, 0.0)
        AnchorPane.setTopAnchor(canvas, 0.0)
        canvas.widthProperty().bind(widthProperty())
        canvas.heightProperty().bind(heightProperty())
        children.add(canvas)
    }

    private var buffer = FloatArray(512)

    fun handleFFT(array: FloatArray, rate: Int) {
        buffer = array
        println("FFT${array.min()} ${array.max()}")
    }

    fun draw() {
        val gc = canvas.getGraphicsContext2D()
        gc.fill = Color.rgb(33, 33, 33)
        gc.fillRect(0.0, 0.0, width, height)

        val gap = 0
        val bufferLength = buffer.size
        val targetBarWidth = 10
        val step = Math.round(targetBarWidth / (width / bufferLength)).toInt().coerceAtLeast(1)
        val totalBars = floor(bufferLength.toDouble() / step)
        val barWidth = (width - (totalBars - 1) * gap) / totalBars
        val barHeightScalar = height

        gc.fill = Color.LIGHTPINK
        var x = 0.0
        var y = 0.0

        for (i in 0 until bufferLength) {
            y = kotlin.math.max(buffer[i].toDouble(), y.toDouble())
            if (i % step != 0) continue

            val barHeight = y * barHeightScalar
            gc.fillRect(x, height - barHeight, barWidth + 1, barHeight)
            x += barWidth + gap
            y = 0.0
        }

    }
}