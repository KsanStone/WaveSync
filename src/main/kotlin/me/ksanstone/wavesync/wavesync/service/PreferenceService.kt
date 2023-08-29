package me.ksanstone.wavesync.wavesync.service

import jakarta.annotation.PostConstruct
import javafx.beans.property.FloatProperty
import javafx.beans.property.IntegerProperty
import javafx.beans.property.Property
import javafx.beans.property.StringProperty
import org.springframework.stereotype.Service
import java.util.prefs.Preferences

@Service
class PreferenceService {

    private val properties = mutableListOf<Property<*>>()
    private lateinit var preferences: Preferences

    @PostConstruct
    fun initialize() {
        preferences = Preferences.userNodeForPackage(this.javaClass)
    }

    fun registerProperty(property: IntegerProperty, name: String) {
        property.value = preferences.getInt(name, property.get())
        property.addListener { _ ->
            preferences.putInt(name, property.get())
        }
        properties.add(property)
    }

    fun registerProperty(property: FloatProperty, name: String) {
        property.value = preferences.getFloat(name, property.get())
        property.addListener { _ ->
            preferences.putFloat(name, property.get())
        }
        properties.add(property)
    }

    fun registerProperty(property: StringProperty, name: String) {
        property.value = preferences.get(name, property.get())
        property.addListener { _ ->
            preferences.put(name, property.get())
        }
        properties.add(property)
    }
}