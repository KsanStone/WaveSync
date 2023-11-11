package me.ksanstone.wavesync.wavesync.service

import atlantafx.base.theme.*
import jakarta.annotation.PostConstruct
import javafx.application.Application
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleStringProperty
import javafx.beans.property.StringProperty
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.THEME
import org.springframework.stereotype.Service

@Service
class ThemeService(
    private val preferenceService: PreferenceService
) {

    final val themes =
        listOf(CupertinoDark(), CupertinoLight(), NordDark(), NordLight(), PrimerDark(), PrimerLight(), Dracula())
            .map { return@map Pair(it.name, it) }.toMap()
    final var themePairs: List<Pair<Theme, Theme?>>
    final val isDark = SimpleBooleanProperty(true)

    init {
        val pairs = mutableListOf<Pair<Theme, Theme?>>()
        themes.forEach {
            if (it.value.isDarkMode) {
                pairs.add(
                    it.value to if (it.value.name.contains("Dark")) themes[it.value.name.replace(
                        "Dark",
                        "Light"
                    )] else null
                )
            }
        }
        themePairs = pairs
    }

    private var current: String = ""
    final val selectedTheme: StringProperty = SimpleStringProperty(THEME)

    @PostConstruct
    fun initialize() {
        selectedTheme.addListener { _ ->
            val theme = themes[selectedTheme.value]
            if (theme == null) {
                selectedTheme.value = current
            } else if (theme.name != current) {
                Application.setUserAgentStylesheet(theme.userAgentStylesheet)
                current = theme.name
                isDark.set(theme.isDarkMode)
            }
        }
        preferenceService.registerProperty(selectedTheme, "theme", this.javaClass)
    }

    fun applyTheme(name: String) {
        themes[name] ?: throw IllegalArgumentException("Invalid theme name $name")
        selectedTheme.set(name)
    }

    fun applyCurrent() {
        Application.setUserAgentStylesheet(themes[selectedTheme.value]!!.userAgentStylesheet)
    }
}