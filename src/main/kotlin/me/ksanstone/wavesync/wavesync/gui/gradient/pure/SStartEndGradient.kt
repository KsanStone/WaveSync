package me.ksanstone.wavesync.wavesync.gui.gradient.pure

import javafx.scene.paint.Color
import java.util.*

/**
 * Represents a [SLinearGradient] with two stops, [Stop(0.0, start), Stop(1.0, end)].
 * The [SStartEndGradient] allows a more efficient gradient computation for this specific stop orientation
 */
class SStartEndGradient(
    val start: Color,
    val end: Color,
) : SGradient {

    override fun get(v: Float): Color {
        return start.interpolate(end, v.toDouble())
    }

    override fun serialize(): String {
        return "$TAG,${start.toHexString()},${end.toHexString()}"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SStartEndGradient

        if (start != other.start) return false
        if (end != other.end) return false

        return true
    }

    override fun hashCode(): Int {
        var result = start.hashCode()
        result = 31 * result + end.hashCode()
        return result
    }

    companion object : SGradientDeserializer {
        override val TAG = "2"

        override fun deserialize(from: String): Optional<SGradient> {
            if (from.matches(Regex("$TAG,#[0-9A-F]{6},#[0-9A-F]{6}"))) {
                val split = from.split(',')
                return Optional.of(SStartEndGradient(Color.web(split[1]), Color.web(split[2])))
            } else {
                return Optional.empty()
            }
        }
    }
}