package me.ksanstone.wavesync.wavesync.gui.gradient.pure

import javafx.scene.paint.Color
import javafx.scene.paint.Stop
import java.util.*

class SLinearGradient(
    var stops: List<Stop>
) : SGradient {

    init {
        if (stops.size < 2)
            throw IllegalArgumentException("Must have at least 2 stops")

        var last = stops[0].offset
        if (last.coerceIn(0.0, 1.0) != last) throw IllegalArgumentException("Stop offset out of range")

        for (stop in stops.drop(1)) {
            if (stop.offset <= last) throw IllegalArgumentException("Stop offset out of order")
            last = stop.offset
            if (last.coerceIn(0.0, 1.0) != last) throw IllegalArgumentException("Stop offset out of range")
        }
    }

    override fun get(v: Float): Color {
        val value = v.coerceIn(0f, 1f)
        val (startStop, endStop) = findSurroundingStops(value)

        val ratio = (value - startStop.offset) / (endStop.offset - startStop.offset)
        return startStop.color.interpolate(endStop.color, ratio)
    }

    private fun findSurroundingStops(value: Float): Pair<Stop, Stop> {
        var startStop = stops.first()
        var endStop = stops.last()

        for (i in 1 until stops.size) {
            if (stops[i].offset >= value) {
                startStop = stops[i - 1]
                endStop = stops[i]
                break
            }
        }

        return Pair(startStop, endStop)
    }


    override fun serialize(): String {
        return "$TAG," + stops.joinToString(",") { it.serialize() }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SLinearGradient

        return stops == other.stops
    }

    override fun hashCode(): Int {
        return stops.hashCode()
    }

    companion object : SGradientDeserializer {
        override val TAG = "L"

        override fun deserialize(from: String): Optional<SGradient> {
            if (from.matches(Regex("$TAG,-?\\d+(\\.\\d+)? #[0-9A-F]{6}(,-?\\d+(\\.\\d+)? #[0-9A-F]{6})+"))) {
                val split = from.split(',')
                return try {
                    Optional.of(SLinearGradient(split.stream().skip(1).map { deserializeStop(it) }.toList()))
                } catch (e: IllegalArgumentException) {
                    Optional.empty()
                }
            } else {
                return Optional.empty()
            }
        }
    }
}