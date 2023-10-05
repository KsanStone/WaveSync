package me.ksanstone.wavesync.wavesync.service

import jakarta.annotation.PostConstruct
import javafx.beans.property.*
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

    fun registerProperty(property: BooleanProperty, name: String) {
        property.value = preferences.getBoolean(name, property.get())
        property.addListener { _ ->
            preferences.putBoolean(name, property.get())
        }
        properties.add(property)
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

    fun <E : Enum<E>> registerProperty(property: ObjectProperty<E>, name: String, enumClass: Class<E>) {
        property.value = enumClass.enumConstants.firstOrNull { it.name == preferences.get(name, property.get()?.name) }
        property.addListener { _ ->
            preferences.put(name, property.get().name)
        }
        properties.add(property)
    }
}