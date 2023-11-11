package me.ksanstone.wavesync.wavesync.gui.utility

import javafx.beans.property.SimpleBooleanProperty
import javafx.collections.ListChangeListener
import javafx.geometry.Side
import javafx.scene.Node
import javafx.scene.canvas.Canvas
import javafx.scene.chart.NumberAxis
import javafx.scene.layout.Pane
import javafx.scene.shape.Line
import javafx.scene.shape.LineTo
import javafx.scene.shape.MoveTo
import javafx.scene.shape.Path
import javafx.scene.text.Text
import java.lang.Double.isNaN
import java.text.DecimalFormat

class GraphCanvas(private val xAxis: NumberAxis, private val yAxis: NumberAxis, private val canvas: Canvas) : Pane() {

    val xAxisShown = SimpleBooleanProperty(true)
    val yAxisShown = SimpleBooleanProperty(true)

    val horizontalLinesVisible = SimpleBooleanProperty(true)
    val verticalLinesVisible = SimpleBooleanProperty(true)

    private var horizontalGridLines: Path = Path()
    private var verticalGridLines: Path = Path()
    private var horizontalZeroLine: Line = Line()
    private var verticalZeroLine: Line = Line()
    private var horizontalZeroLineVisible = true
    private var verticalZeroLineVisible = true

    init {
        listOf(xAxisShown, yAxisShown, widthProperty(), heightProperty())
            .forEach { it.addListener { _ -> doLayout() } }
        horizontalGridLines.styleClass.setAll("horizontal-grid-lines")
        verticalGridLines.styleClass.setAll("vertical-grid-lines")
        horizontalZeroLine.styleClass.setAll("horizontal-zero-line")
        verticalZeroLine.styleClass.setAll("vertical-zero-line")
        children.addAll(horizontalGridLines, verticalGridLines, horizontalZeroLine, verticalZeroLine, xAxis, yAxis, canvas)

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

        listOf(xAxis.tickMarks, yAxis.tickMarks).forEach { it.addListener(ListChangeListener { c -> while(c.next()); layoutGrid() }) }
        listOf(horizontalLinesVisible, verticalLinesVisible, xAxisShown, yAxisShown).forEach { it.addListener { _ -> layoutGrid() }}


        minWidth = 1.0
        minHeight = 1.0
        maxWidth = Double.MAX_VALUE
        maxHeight = Double.MAX_VALUE
        stylesheets.add("/styles/axis-fix.css")
    }

    private fun doLayout() {
        val leftPad = if (yAxisShown.get()) yAxis.prefWidth(-1.0) else 0.0
        val bottomPad = if (xAxisShown.get()) xAxis.prefHeight(-1.0) else 0.0

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

        layoutGrid()
    }

    private fun layoutGrid() {
        val leftPad = if (yAxisShown.get()) yAxis.prefWidth(-1.0) else 0.0
        createGrid(left = leftPad, xAxisWidth = canvas.width, yAxisHeight = canvas.height)
    }

    private fun createGrid(left: Double, top: Double = 0.0, xAxisWidth: Double, yAxisHeight: Double) {
        val xTics = xAxis.tickMarks
        val xAxisZero = xAxis.zeroPosition
        val yTics = yAxis.tickMarks
        val yAxisZero = yAxis.zeroPosition

        // position vertical and horizontal zero lines
        if (isNaN(xAxisZero) || !verticalZeroLineVisible || !verticalLinesVisible.get().and(xAxisShown.get())) {
            verticalZeroLine.isVisible = false
        } else {
            verticalZeroLine.startX = left + xAxisZero + 0.5
            verticalZeroLine.startY = top
            verticalZeroLine.endX = left + xAxisZero + 0.5
            verticalZeroLine.endY = top + yAxisHeight
            verticalZeroLine.isVisible = true
        }
        if (isNaN(yAxisZero) || !horizontalZeroLineVisible || !horizontalLinesVisible.get().and(yAxisShown.get())) {
            horizontalZeroLine.isVisible = false
        } else {
            horizontalZeroLine.startX = left
            horizontalZeroLine.startY = top + yAxisZero + 0.5
            horizontalZeroLine.endX = left + xAxisWidth
            horizontalZeroLine.endY = top + yAxisZero + 0.5
            horizontalZeroLine.isVisible = true
        }

        verticalGridLines.elements.clear()
        if (verticalLinesVisible.get().and(xAxisShown.get())) {
            for (i in xTics.indices) {
                val tick = xTics[i]
                val x: Double = xAxis.getDisplayPosition(tick.value)
                if ((x != xAxisZero || !verticalZeroLineVisible) && x > 0 && x <= xAxisWidth) {
                    verticalGridLines.elements.add(MoveTo(left + x + 0.5, top))
                    verticalGridLines.elements.add(LineTo(left + x + 0.5, top + yAxisHeight))
                }
            }
        }

        horizontalGridLines.elements.clear()
        if (horizontalLinesVisible.get().and(yAxisShown.get())) {
            for (i in yTics.indices) {
                val tick = yTics[i]
                val y: Double = yAxis.getDisplayPosition(tick.value)
                if ((y != yAxisZero || !horizontalZeroLineVisible) && y >= 0 && y < yAxisHeight) {
                    horizontalGridLines.elements.add(MoveTo(left, top + y + 0.5))
                    horizontalGridLines.elements.add(LineTo(left + xAxisWidth, top + y + 0.5))
                }
            }
        }
    }
}