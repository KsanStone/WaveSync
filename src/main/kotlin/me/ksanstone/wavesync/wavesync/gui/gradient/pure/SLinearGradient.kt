package me.ksanstone.wavesync.wavesync.gui.gradient.pure

import javafx.scene.paint.Color
import javafx.scene.paint.Stop
import me.ksanstone.wavesync.wavesync.service.ProcessUtil.interpolateArgb
import java.util.*

class SLinearGradient(
    val stops: List<Stop>
) : SGradient {

    private val stopCache = FloatArray(stops.size * STOP_FACTOR)

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

        for (i in stops.indices) {
            stopCache[i * STOP_FACTOR] = stops[i].offset.toFloat()
            stopCache[i * STOP_FACTOR + A] = stops[i].color.opacity.toFloat()
            stopCache[i * STOP_FACTOR + R] = stops[i].color.red.toFloat()
            stopCache[i * STOP_FACTOR + G] = stops[i].color.green.toFloat()
            stopCache[i * STOP_FACTOR + B] = stops[i].color.blue.toFloat()
        }
    }

    override fun get(v: Float): Color {
        val value = v.coerceIn(0f, 1f)
        var startStop = stops[0]
        var endStop = stops[stops.size - 1]

        for (i in 1 until stops.size) {
            if (stops[i].offset >= value) {
                startStop = stops[i - 1]
                endStop = stops[i]
                break
            }
        }

        val ratio = (value - startStop.offset) / (endStop.offset - startStop.offset)
        return startStop.color.interpolate(endStop.color, ratio)
    }

    override fun argb(v: Float): Int {
        val value = v.coerceIn(0f, 1f)
        var startStop = 0
        var endStop = (stops.size - 1) * STOP_FACTOR

        for (i in 1 until stops.size) {
            if (stopCache[i * STOP_FACTOR] >= value) {
                startStop = (i - 1) * STOP_FACTOR
                endStop = i * STOP_FACTOR
                break
            }
        }

        val ratio = (value - stopCache[startStop]) / (stopCache[endStop] - stopCache[startStop])
        return interpolateArgb(
            stopCache[startStop + A], stopCache[startStop + R], stopCache[startStop + G], stopCache[startStop + B],
            stopCache[endStop + A], stopCache[endStop + R], stopCache[endStop + G], stopCache[endStop + B], ratio
        )
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
        const val A = 1
        const val R = 2
        const val G = 3
        const val B = 4
        const val STOP_FACTOR = 5

        override fun deserialize(from: String): Optional<SGradient> {
            if (from.matches(Regex("$TAG,-?\\d+(\\.\\d+)? #[0-9A-F]{6}(,-?\\d+(\\.\\d+)? #[0-9A-F]{6})+"))) {
                val split = from.split(',')
                return try {
                    Optional.of(SLinearGradient(ArrayList(split.stream().skip(1).map { deserializeStop(it) }.toList())))
                } catch (e: IllegalArgumentException) {
                    Optional.empty()
                }
            } else {
                return Optional.empty()
            }
        }
    }
}