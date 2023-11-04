package me.ksanstone.wavesync.wavesync.gui.utility

import javafx.beans.property.SimpleBooleanProperty
import javafx.collections.ListChangeListener
import javafx.geometry.Side
import javafx.scene.Node
import javafx.scene.canvas.Canvas
import javafx.scene.chart.NumberAxis
import javafx.scene.layout.Pane
import javafx.scene.text.Text
import java.text.DecimalFormat

class GraphCanvas(private val xAxis: NumberAxis, private val yAxis: NumberAxis, private val canvas: Canvas) : Pane() {

    val xAxisShown = SimpleBooleanProperty(true)
    val yAxisShown = SimpleBooleanProperty(true)

    init {
        listOf(xAxisShown, yAxisShown, widthProperty(), heightProperty())
            .forEach { it.addListener { _ -> doLayout() } }
        children.addAll(xAxis, yAxis, canvas)

        yAxis.side = Side.LEFT
        yAxis.animated = false
        yAxis.managedProperty().bind(yAxis.visibleProperty())
        yAxis.childrenUnmodifiable
            .addListener(ListChangeListener<Node?> { c: ListChangeListener.Change<out Node?> ->
                while (c.next()) {
                    if (c.wasAdded()) {
                        for (mark in c.addedSubList) {
                            if (mark is Text) {
                                val parsed = DecimalFormat("###,###.###").parse(mark.text).toDouble()
                                if (parsed == yAxis.lowerBound && !xAxisShown.get()) {
                                    mark.text =
                                        if (mark.text.contains("\n")) mark.text else mark.text + "\n"
                                } else if (parsed == yAxis.upperBound) {
                                    mark.text = if (mark.text.contains("\n")) mark.text else "\n" + mark.text
                                }
                            }
                        }
                    }
                }
            } as ListChangeListener<Node?>?)

        xAxis.side = Side.BOTTOM
        xAxis.animated = false
        xAxis.managedProperty().bind(xAxis.visibleProperty())
        xAxis.childrenUnmodifiable
            .addListener(ListChangeListener<Node?> { c: ListChangeListener.Change<out Node?> ->
                while (c.next()) {
                    if (c.wasAdded()) {
                        for (mark in c.addedSubList) {
                            if (mark is Text) {
                                val parsed = DecimalFormat("###,###.###").parse(mark.text).toDouble()
                                if (parsed == xAxis.lowerBound && !yAxisShown.get()) {
                                    mark.text =
                                        if (mark.text.contains(" ")) mark.text else " ".repeat(mark.text.length * 2) + mark.text
                                } else if (parsed == xAxis.upperBound) {
                                    mark.text =
                                        if (mark.text.contains(" ")) mark.text else mark.text + " ".repeat(mark.text.length * 2)
                                }
                            }
                        }
                    }
                }
            } as ListChangeListener<Node?>?)

        minWidth = 1.0
        minHeight = 1.0
        maxWidth = Double.MAX_VALUE
        maxHeight = Double.MAX_VALUE
        stylesheets.add("/styles/axis-fix.css")
    }

    private fun doLayout() {
        val leftPad = if (yAxisShown.get()) { yAxis.prefWidth(-1.0) } else { 0.0 }
        val bottomPad = if (xAxisShown.get()) { xAxis.prefHeight(-1.0) } else { 0.0 }

        yAxis.isVisible = yAxisShown.get()
        xAxis.isVisible = xAxisShown.get()

        if (yAxisShown.get()) {
            yAxis.resizeRelocate(0.0, 0.0, leftPad, height - bottomPad)
            yAxis.minHeight = height - bottomPad - 1
        }
        if (xAxisShown.get()) {
            xAxis.resizeRelocate(leftPad, height - bottomPad, width - leftPad, bottomPad)
            xAxis.minWidth = width - leftPad - 1
        }

        canvas.width = width - leftPad
        canvas.height = height - bottomPad
        canvas.resizeRelocate(leftPad, 0.0, canvas.width, canvas.height)
    }

}