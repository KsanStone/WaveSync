package me.ksanstone.wavesync.wavesync.gui.component.util

import javafx.animation.KeyFrame
import javafx.animation.KeyValue
import javafx.animation.Timeline
import javafx.beans.property.DoubleProperty
import javafx.beans.property.SimpleDoubleProperty
import javafx.scene.chart.ValueAxis
import javafx.util.Duration
import java.text.NumberFormat
import kotlin.math.log10
import kotlin.math.pow

/**
 * A logarithmic axis implementation for JavaFX 2 charts<br></br>
 * <br></br>
 *
 * @author Kevin Senechal
 */
class LogarithmicAxis(lowerBound: Double, upperBound: Double) : ValueAxis<Number>(lowerBound, upperBound) {
    private val lowerRangeTimeline = Timeline()
    private val upperRangeTimeline = Timeline()

    private val logUpperBound: DoubleProperty = SimpleDoubleProperty()
    private val logLowerBound: DoubleProperty = SimpleDoubleProperty()

    init {
        try {
            validateBounds(lowerBound, upperBound)
            bindLogBoundsToDefaultBounds()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Bind our logarithmic bounds with the superclass bounds, consider base 10 logarithmic scale.
     */
    private fun bindLogBoundsToDefaultBounds() {
        logLowerBound.bind(lowerBoundProperty().map { log(it.toDouble()) })
        logUpperBound.bind(upperBoundProperty().map { log(it.toDouble()) })
    }

    /**
     * Validate the bounds by throwing an exception if the values are not conformed to the mathematics log interval:
     * [0,Double.MAX_VALUE]
     *
     */
    @Throws(Exception::class)
    private fun validateBounds(lowerBound: Double, upperBound: Double) {
        if (lowerBound < 0 || upperBound < 0 || lowerBound > upperBound) {
            throw Exception(
                "The logarithmic range should be include to [0,Double.MAX_VALUE] and the lowerBound should be less than the upperBound"
            )
        }
    }

    override fun calculateMinorTickMarks(): List<Number> {
        val range: Array<Number> = range
        val minorTickMarksPositions: MutableList<Number> = ArrayList()
        val upperBound = range[1]
        val logUpperBound = log(upperBound.toDouble())
        val minorTickMarkCount = minorTickCount

        var i = 0.0
        while (i <= logUpperBound) {
            var j = 0.0
            while (j <= 9) {
                val value: Double = j * 10.0.pow(i)
                minorTickMarksPositions.add(value)
                j += (1.0 / minorTickMarkCount)
            }
            i += 1.0
        }
        return minorTickMarksPositions
    }

    @Suppress("UNCHECKED_CAST")
    override fun calculateTickValues(length: Double, range: Any): List<Number> {
        val tickPositions: MutableList<Number> = ArrayList()
        val upperBound = (range as Array<Number>)[1]
        val logUpperBound = log(upperBound.toDouble())

        var i = 0.0
        while (i <= logUpperBound) {
            for (j in 1..9) {
                val value: Double = j * 10.0.pow(i)
                tickPositions.add(value)
            }
            i += 1.0
        }
        return tickPositions
    }

    override fun getRange(): Array<Number> {
        return arrayOf(lowerBoundProperty().get(), upperBoundProperty().get())
    }

    override fun getTickMarkLabel(value: Number): String {
        val formatter = NumberFormat.getInstance()
        formatter.maximumIntegerDigits = 6
        formatter.minimumIntegerDigits = 1
        return formatter.format(value)
    }

    /**
     * {@inheritDoc}
     */
    @Suppress("UNCHECKED_CAST")
    override fun setRange(range: Any, animate: Boolean) {
        val lowerBound = (range as Array<Number>)[0]
        val upperBound = range[1]
        try {
            validateBounds(lowerBound.toDouble(), upperBound.toDouble())
        } catch (e: Exception) {
            e.printStackTrace()
        }
        if (animate) {
            try {
                lowerRangeTimeline.keyFrames.clear()
                upperRangeTimeline.keyFrames.clear()

                lowerRangeTimeline.keyFrames
                    .addAll(
                        KeyFrame(
                            Duration.ZERO, KeyValue(
                                lowerBoundProperty(), lowerBoundProperty()
                                    .get()
                            )
                        ),
                        KeyFrame(
                            Duration(ANIMATION_TIME), KeyValue(
                                lowerBoundProperty(),
                                lowerBound.toDouble()
                            )
                        )
                    )

                upperRangeTimeline.keyFrames
                    .addAll(
                        KeyFrame(
                            Duration.ZERO, KeyValue(
                                upperBoundProperty(), upperBoundProperty()
                                    .get()
                            )
                        ),
                        KeyFrame(
                            Duration(ANIMATION_TIME), KeyValue(
                                upperBoundProperty(),
                                upperBound.toDouble()
                            )
                        )
                    )
                lowerRangeTimeline.play()
                upperRangeTimeline.play()
            } catch (e: Exception) {
                lowerBoundProperty().set(lowerBound.toDouble())
                upperBoundProperty().set(upperBound.toDouble())
            }
        }
        lowerBoundProperty().set(lowerBound.toDouble())
        upperBoundProperty().set(upperBound.toDouble())
    }

    override fun getValueForDisplay(displayPosition: Double): Number {
        val delta = logUpperBound.get() - logLowerBound.get()
        return if (side.isVertical) {
            10.0.pow((((displayPosition - height) / -height) * delta) + logLowerBound.get())
        } else {
            10.0.pow((((displayPosition / width) * delta) + logLowerBound.get()))
        }
    }

    override fun getDisplayPosition(value: Number): Double {
        val delta = logUpperBound.get() - logLowerBound.get()
        val deltaV = log(value.toDouble()) - logLowerBound.get()
        return if (side.isVertical) {
            (1.0 - ((deltaV) / delta)) * height
        } else {
            (deltaV) / delta * width
        }
    }

    private fun log(num: Double): Double {
        if (num == 0.0) return 0.0
        return log10(num)
    }

    companion object {
        /**
         * The time of animation in ms
         */
        private const val ANIMATION_TIME = 200.0
    }
}