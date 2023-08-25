package me.ksanstone.wavesync.wavesync.service

import atlantafx.base.theme.*
import jakarta.annotation.PostConstruct
import javafx.application.Application
import javafx.beans.property.SimpleStringProperty
import javafx.beans.property.StringProperty
import org.springframework.stereotype.Service

@Service
class ThemeService {

    val themes = listOf(CupertinoDark(), CupertinoLight(), NordDark(), NordLight(), PrimerDark(), PrimerLight(), Dracula())
        .map {return@map Pair(it.name, it)}.toMap()
    
    private var current: String = ""
    final val selectedTheme: StringProperty = SimpleStringProperty("")

    @PostConstruct
    fun initialize() {
        selectedTheme.addListener { _ ->
            val theme = themes[selectedTheme.value]
            if (theme == null) {
                selectedTheme.value = current
            } else if (theme.name != current){
                Application.setUserAgentStylesheet(theme.userAgentStylesheet)
                current = theme.name
            }
        }
    }
    
    fun applyTheme(name: String) {
        themes[name] ?: throw IllegalArgumentException("Invalid theme name $name")
        selectedTheme.set(name)
    }
}