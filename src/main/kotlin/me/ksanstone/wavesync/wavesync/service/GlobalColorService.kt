package me.ksanstone.wavesync.wavesync.service

import jakarta.annotation.PostConstruct
import javafx.beans.property.BooleanProperty
import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.scene.paint.Color
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.DEFAULT_END_COLOR
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.DEFAULT_START_COLOR
import me.ksanstone.wavesync.wavesync.ApplicationSettingDefaults.DEFAULT_USE_CSS_COLOR
import org.springframework.stereotype.Service

@Service
class GlobalColorService(
    val preferenceService: PreferenceService
) {

    val startColor: ObjectProperty<Color> = SimpleObjectProperty(DEFAULT_START_COLOR)
    val endColor: ObjectProperty<Color> = SimpleObjectProperty(DEFAULT_END_COLOR)
    val barUseCssColor: BooleanProperty = SimpleBooleanProperty(DEFAULT_USE_CSS_COLOR)

    @PostConstruct
    fun initializeProps() {
        preferenceService.registerColorProperty(startColor, "startColor", this.javaClass)
        preferenceService.registerColorProperty(endColor, "endColor", this.javaClass)
        preferenceService.registerProperty(barUseCssColor, "barUseCssColor", this.javaClass)
    }

}