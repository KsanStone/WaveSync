package me.ksanstone.wavesync.wavesync.gui.gradient.pure

import javafx.scene.paint.Color
import javafx.scene.paint.Stop
import org.springframework.stereotype.Service
import java.util.*

@Service
class GradientSerializer {

    fun serialize(gradient: SGradient): String {
        return gradient.serialize()
    }

    fun deserialize(string: String): Optional<SGradient> {
        if (string.isEmpty()) return Optional.empty()
        when (string[0].toString()) {
            SStartEndGradient.TAG -> return SStartEndGradient.deserialize(string)
            SLinearGradient.TAG -> return SLinearGradient.deserialize(string)
        }
        return Optional.empty()
    }

    fun fromStops(stops: List<Stop>): Optional<SGradient> {
        return if (stops.size == 2 && stops[0].offset == 0.0 && stops[1].offset == 1.0) {
            Optional.of(SStartEndGradient(stops[0].color, stops[1].color))
        } else if (stops.size >= 2) {
            Optional.of(SLinearGradient(stops))
        } else {
            Optional.empty()
        }
    }

    fun toStops(gradient: SGradient): List<Stop> {
        return when (gradient) {
            is SLinearGradient -> gradient.stops
            is SStartEndGradient -> listOf(Stop(0.0, gradient.start), Stop(1.0, gradient.end))
            else -> listOf()
        }
    }
}

fun Color.toHexString(): String {
    return "#" + Integer.toHexString(this.hashCode()).padStart(8, '0').substring(0, 6).uppercase(Locale.getDefault())
}

fun Color.toAHexString(): String {
    return "#" + toString().substring(2)
}

fun Stop.serialize(): String {
    return this.offset.toString() + " " + this.color.toHexString()
}

fun deserializeStop(from: String): Stop {
    val split = from.split(' ')
    return Stop(split[0].toDouble(), Color.web(split[1]))
}