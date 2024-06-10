package me.ksanstone.wavesync.wavesync.gui.gradient.pure

import javafx.scene.paint.Color
import java.util.*

interface SGradient {

    operator fun get(v: Float): Color

    fun serialize(): String

}

interface SGradientDeserializer {
    val TAG: String

    fun deserialize(from: String): Optional<SGradient>
}