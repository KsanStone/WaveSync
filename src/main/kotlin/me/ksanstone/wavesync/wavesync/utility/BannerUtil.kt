package me.ksanstone.wavesync.wavesync.utility

import org.fusesource.jansi.Ansi
import org.springframework.util.PropertyPlaceholderHelper
import java.util.stream.Collectors

object BannerUtil {

    fun banner() {
        val helper = PropertyPlaceholderHelper("\${", "}")
        installAnsiColors()
        val banner = (javaClass.getResourceAsStream("/banner.txt") ?: return)
            .bufferedReader()
            .lines()
            .map { helper.replacePlaceholders(it, System::getProperty) }
            .collect(Collectors.joining(System.lineSeparator()))

        println(banner)
    }

    private fun installAnsiColors() {
        val colors = arrayOf("BLACK", "RED", "GREEN", "YELLOW", "BLUE", "MAGENTA", "CYAN", "WHITE")
        for (color in colors) {
            System.setProperty("AnsiColor.BRIGHT_$color", Ansi.ansi().fgBright(Ansi.Color.valueOf(color)).toString())
        }
        System.setProperty("AnsiColor.DEFAULT", Ansi.ansi().fgDefault().toString())
        System.setProperty("AnsiStyle.NORMAL", Ansi.ansi().a(Ansi.Attribute.RESET).toString())
    }

}