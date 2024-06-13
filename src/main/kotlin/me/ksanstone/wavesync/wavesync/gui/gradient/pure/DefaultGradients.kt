package me.ksanstone.wavesync.wavesync.gui.gradient.pure

import javafx.scene.paint.Color
import javafx.scene.paint.Stop

object DefaultGradients {
    val SPECTROGRAM = SLinearGradient(
        listOf(
            Stop(0.0, Color.BLACK),
            Stop(0.33, Color.PURPLE),
            Stop(0.66, Color.RED),
            Stop(0.87, Color.YELLOW),
            Stop(1.0, Color.WHITE)
        )
    )
}
