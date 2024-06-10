package me.ksanstone.wavesync.wavesync

import javafx.scene.paint.Color
import javafx.scene.paint.Stop
import me.ksanstone.wavesync.wavesync.gui.gradient.pure.GradientSerializer
import me.ksanstone.wavesync.wavesync.gui.gradient.pure.SLinearGradient
import me.ksanstone.wavesync.wavesync.gui.gradient.pure.SStartEndGradient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.jvm.optionals.getOrNull

class SGradientTests {

    private val color1 = Color.rgb(0x12, 0x34, 0x56)
    private val color2 = Color.rgb(0x65, 0x43, 0x21)
    private val color3 = Color.RED

    private val gradientSerializer = GradientSerializer()

    @Test
    fun testStartStopGradientSerialize() {
        val gradient = SStartEndGradient(color1, color2)
        val serialized = gradient.serialize()
        val deserialized = gradientSerializer.deserialize(serialized)

        assertEquals(gradient, deserialized.getOrNull())
    }

    @Test
    fun testLinearGradientSerialize() {
        val gradient = SLinearGradient(listOf(Stop(0.0, color1), Stop(0.5, color2), Stop(1.0, color3)))
        val serialized = gradient.serialize()
        val deserialized = gradientSerializer.deserialize(serialized)

        assertEquals(gradient, deserialized.getOrNull())
    }

}