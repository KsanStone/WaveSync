package me.ksanstone.wavesync.wavesync.gui.gradient.pure

import javafx.scene.paint.Color
import me.ksanstone.wavesync.wavesync.service.ProcessUtil.packArgb
import java.util.*

interface SGradient {

    operator fun get(v: Float): Color

    fun argb(v: Float): Int

    fun serialize(): String

}

interface SGradientDeserializer {
    val TAG: String

    fun deserialize(from: String): Optional<SGradient>
}

fun interpolateArgb(start: Color, end: Color, v: Float): Int {
    return packArgb(
        ((start.opacity + (end.opacity - start.opacity) * v) * 255).toInt(),
        ((start.red + (end.red - start.red) * v) * 255).toInt(),
        ((start.green + (end.green - start.green) * v) * 255).toInt(),
        ((start.blue + (end.blue - start.blue) * v) * 255).toInt(),
    )
}