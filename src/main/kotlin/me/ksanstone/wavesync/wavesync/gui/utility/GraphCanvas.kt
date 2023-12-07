package me.ksanstone.wavesync.wavesync.gui.utility

import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.collections.FXCollections
import javafx.collections.ListChangeListener
import javafx.collections.ObservableList
import javafx.geometry.Point2D
import javafx.geometry.Side
import javafx.scene.Node
import javafx.scene.canvas.Canvas
import javafx.scene.chart.NumberAxis
import javafx.scene.layout.HBox
import javafx.scene.layout.Pane
import javafx.scene.shape.Line
import javafx.scene.shape.LineTo
import javafx.scene.shape.MoveTo
import javafx.scene.shape.Path
import javafx.scene.text.Text
import java.lang.Double.isNaN
import java.text.DecimalFormat

class GraphCanvas(private val xAxis: NumberAxis, private val yAxis: NumberAxis, private val canvas: Canvas) : Pane() {

    companion object {
        const val TOOLTIP_OFFSET = 5.0
    }

    val tooltipContainer = HBox()
    val tooltipEnabled = SimpleBooleanProperty(false)
    val tooltipPosition = SimpleObjectProperty<Point2D>()

    val xAxisShown = SimpleBooleanProperty(true)
    val yAxisShown = SimpleBooleanProperty(true)

    val horizontalLinesVisible = SimpleBooleanProperty(true)
    val verticalLinesVisible = SimpleBooleanProperty(true)
    val highlightedVerticalLines: ObservableList<Double> = FXCollections.observableArrayList()
    val highlightedHorizontalLines: ObservableList<Double> = FXCollections.observableArrayList()

    private var horizontalGridLines: Path = Path()
    private var verticalGridLines: Path = Path()
    private var highlightedHorizontalGridLines: Path = Path()
    private var highlightedVerticalGridLines: Path = Path()
    private var tooltipCross: Path = Path()
    private var horizontalZeroLine: Line = Line()
    private var verticalZeroLine: Line = Line()
    private var horizontalZeroLineVisible = true
    private var verticalZeroLineVisible = true

    init {
        horizontalGridLines.styleClass.setAll("horizontal-grid-lines")
        verticalGridLines.styleClass.setAll("vertical-grid-lines")
        highlightedHorizontalGridLines.styleClass.setAll("horizontal-highlighted-grid-lines")
        highlightedVerticalGridLines.styleClass.setAll("vertical-highlighted-grid-lines")
        horizontalZeroLine.styleClass.setAll("horizontal-zero-line")
        verticalZeroLine.styleClass.setAll("vertical-zero-line")
        tooltipContainer.styleClass.setAll("tooltip")
        tooltipCross.styleClass.setAll("tooltip-cross")
        children.addAll(
            horizontalGridLines,
            verticalGridLines,
            horizontalZeroLine,
            verticalZeroLine,
            highlightedHorizontalGridLines,
            highlightedVerticalGridLines,
            tooltipCross,
            xAxis,
            yAxis,
            canvas,
            tooltipContainer
        )

        listOf(xAxisShown, yAxisShown, widthProperty(), heightProperty(), xAxis.heightProperty(), yAxis.widthProperty())
            .forEach { it.addListener { _ -> doLayout(); layoutTooltipCross() } }

        yAxis.side = Side.LEFT
        yAxis.animated = false
        yAxis.managedProperty().bind(yAxis.visibleProperty())
        yAxis.childrenUnmodifiable
            .addListener(ListChangeListener<Node?> { c: ListChangeListener.Change<out Node?> ->
                while (c.next()) {
                    if (!c.wasAdded()) continue
                    for (mark in c.addedSubList) {
                        if (mark !is Text) continue
                        val parsed = DecimalFormat("###,###.###").parse(mark.text).toDouble()
                        if (parsed == yAxis.lowerBound && !xAxisShown.get()) {
                            mark.text =
                                if (mark.text.contains("\n")) mark.text else mark.text + "\n"
                        } else if (parsed == yAxis.upperBound) {
                            mark.text = if (mark.text.contains("\n")) mark.text else "\n" + mark.text
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
                    if (!c.wasAdded()) continue
                    for (mark in c.addedSubList) {
                        if (mark !is Text) continue
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
            } as ListChangeListener<Node?>?)

        canvas.setOnMouseMoved {
            tooltipPosition.set(Point2D(it.x, it.y))
        }

        tooltipContainer.visibleProperty().bind(canvas.hoverProperty().and(tooltipEnabled))
        tooltipContainer.isMouseTransparent = true

        // the canvas is not always snapped to x=0, but it is snapped to the right edge, thus we use `this`.width
        tooltipContainer.layoutXProperty().bind(tooltipPosition.map
        { (canvas.localToParent(it).x).let { x -> if (x > this.width - TOOLTIP_OFFSET - tooltipContainer.width) (x - tooltipContainer.width - TOOLTIP_OFFSET).coerceAtLeast(0.0) else x + TOOLTIP_OFFSET } })

        // the canvas is always snapped to the top, so we use canvas.height, to make the tooltip not cover the x-axis
        tooltipContainer.layoutYProperty().bind(tooltipPosition.map
        { (canvas.localToParent(it).y).let { y -> if (y > canvas.height - TOOLTIP_OFFSET - tooltipContainer.height) (y - tooltipContainer.height - TOOLTIP_OFFSET).coerceAtLeast(0.0) else y + TOOLTIP_OFFSET } })

        listOf(
            tooltipContainer.visibleProperty(),
            tooltipPosition
        ).forEach { it.addListener { _ -> layoutTooltipCross() } }
        listOf(
            xAxis.tickMarks,
            yAxis.tickMarks
        ).forEach { it.addListener(ListChangeListener { c -> while (c.next()) { /* layout once updates have settled */ }; layoutGrid() }) }
        listOf(
            horizontalLinesVisible,
            verticalLinesVisible,
            xAxisShown,
            yAxisShown
        ).forEach { it.addListener { _ -> layoutGrid() } }


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

    private fun layoutTooltipCross() {
        tooltipCross.elements.clear()
        val p: Point2D? = tooltipPosition.get()
        if (!tooltipContainer.isVisible || p == null) return

        val axisTickOverlap = 5.5

        var s1 = canvas.localToParent(Point2D(0.0, p.y))
        var s2 = canvas.localToParent(Point2D(canvas.width, p.y))
        tooltipCross.elements.add(MoveTo(s1.x - (if(xAxisShown.get()) axisTickOverlap else 0.0), s1.y + 0.5))
        tooltipCross.elements.add(LineTo(s2.x, s2.y + 0.5))

        s1 = canvas.localToParent(Point2D(p.x, 0.0))
        s2 = canvas.localToParent(Point2D(p.x, canvas.height))
        tooltipCross.elements.add(MoveTo(s2.x + 0.5, s2.y + (if(xAxisShown.get()) axisTickOverlap else 0.0)))
        tooltipCross.elements.add(LineTo(s1.x + 0.5, s1.y))
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
        highlightedVerticalGridLines.elements.clear()
        if (verticalLinesVisible.get().and(xAxisShown.get())) {
            for (i in xTics.indices) {
                val tick = xTics[i]
                val x: Double = xAxis.getDisplayPosition(tick.value)
                if ((x != xAxisZero || !verticalZeroLineVisible) && x > 0 && x <= xAxisWidth && !highlightedVerticalLines.contains(
                        tick.value
                    )
                ) {
                    verticalGridLines.elements.add(MoveTo(left + x + 0.5, top))
                    verticalGridLines.elements.add(LineTo(left + x + 0.5, top + yAxisHeight))
                }
            }
            for (line in highlightedVerticalLines) {
                val x: Double = xAxis.getDisplayPosition(line)
                if (x > 0 && x <= xAxisWidth) {
                    highlightedVerticalGridLines.elements.add(MoveTo(left + x + 0.5, top))
                    highlightedVerticalGridLines.elements.add(LineTo(left + x + 0.5, top + yAxisHeight))
                }
            }
        }

        horizontalGridLines.elements.clear()
        highlightedHorizontalGridLines.elements.clear()
        if (horizontalLinesVisible.get().and(yAxisShown.get())) {
            for (i in yTics.indices) {
                val tick = yTics[i]
                val y: Double = yAxis.getDisplayPosition(tick.value)
                if ((y != yAxisZero || !horizontalZeroLineVisible) && y >= 0 && y < yAxisHeight && !highlightedHorizontalLines.contains(
                        tick.value
                    )
                ) {
                    horizontalGridLines.elements.add(MoveTo(left, top + y + 0.5))
                    horizontalGridLines.elements.add(LineTo(left + xAxisWidth, top + y + 0.5))
                }
            }
            for (line in highlightedHorizontalLines) {
                val y: Double = yAxis.getDisplayPosition(line)
                if (y >= 0 && y < yAxisHeight) {
                    highlightedHorizontalGridLines.elements.add(MoveTo(left, top + y + 0.5))
                    highlightedHorizontalGridLines.elements.add(LineTo(left + xAxisWidth, top + y + 0.5))
                }
            }
        }
    }
}