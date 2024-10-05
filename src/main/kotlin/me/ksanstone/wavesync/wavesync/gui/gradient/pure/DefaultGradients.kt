package me.ksanstone.wavesync.wavesync.gui.gradient.pure

import javafx.scene.paint.Color
import javafx.scene.paint.Stop

enum class DefaultGradient(val gradient: SGradient) {
    SPECTROGRAM(
        SLinearGradient(
            listOf(
                Stop(0.0, Color.BLACK),
                Stop(0.17, Color.web("#010035")),
                Stop(0.33, Color.PURPLE),
                Stop(0.66, Color.RED),
                Stop(0.87, Color.YELLOW),
                Stop(1.0, Color.WHITE)
            )
        )
    ),
    NEON_SPECTROGRAM(
        SLinearGradient(
            listOf(
                Stop(0.0, Color.BLACK),
                Stop(0.142, Color.web("#300B93")),
                Stop(0.513, Color.web("#DB41E4")),
                Stop(0.773, Color.web("#00DFFF")),
                Stop(1.0, Color.web("#CDFFFF"))
            )
        )
    ),
    BLACK_AND_WHITE(
        SStartEndGradient(
            Color.BLACK,
            Color.WHITE
        )
    ),
    TRANSPARENT_WHITE(
        SStartEndGradient(
            Color.TRANSPARENT,
            Color.WHITE
        )
    ),
    DRACULA(
        SStartEndGradient(
            Color.web("#3c3366ff"),
            Color.web("#b5a6ffff")
        )
    ),
    NORD(
        SStartEndGradient(
            Color.web("#314359ff"),
            Color.web("#859fc0ff")
        )
    ),
    CUPERTINO(
        SStartEndGradient(
            Color.web("#043566ff"),
            Color.web("#54a9ffff")
        )
    ),
    PRIMER(
        SStartEndGradient(
            Color.web("#051d4dff"),
            Color.web("#58a6ffff")
        )
    ),
    DRACULA_DARK(
        SStartEndGradient(
            Color.web("#282a36ff"),
            Color.web("#b5a6ffff")
        )
    ),
    NORD_DARK(
        SStartEndGradient(
            Color.web("#2e3440ff"),
            Color.web("#859fc0ff")
        )
    ),
    CUPERTINO_DARK(
        SStartEndGradient(
            Color.web("#1c1c1eff"),
            Color.web("#54a9ffff")
        )
    ),
    PRIMER_DARK(
        SStartEndGradient(
            Color.web("#0d1117ff"),
            Color.web("#58a6ffff")
        )
    ),
}
