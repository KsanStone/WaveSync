package me.ksanstone.wavesync.wavesync.service

import jakarta.annotation.PostConstruct
import javafx.beans.property.*
import org.springframework.stereotype.Service
import java.util.prefs.Preferences

@Suppress("UNCHECKED_CAST")
@Service
class PreferenceService {

    private val properties = mutableListOf<Property<*>>()
    private lateinit var rootPreferences: Preferences

    @PostConstruct
    fun initialize() {
        rootPreferences = Preferences.userNodeForPackage(this.javaClass)
    }

    private fun getPreferences(clazz: Class<*>? = null, id: String = DEFAULT_ID): Preferences {
        if (clazz == null) return rootPreferences
        return Preferences.userNodeForPackage(clazz).node(clazz.simpleName).node("-$id-")
    }

    private fun doRegister(property: Property<Any?>, getter: () -> Any?, setter: (e: Any) -> Unit) {
        property.value = getter()
        property.addListener { _, _, v ->
            if (v != null)
                setter(v)
        }
        properties.add(property)
    }

    fun registerProperty(property: BooleanProperty, name: String, clazz: Class<*>? = null, id: String = DEFAULT_ID) {
        val preferences = getPreferences(clazz, id)
        doRegister(property as Property<Any?>,
            { preferences.getBoolean(name, property.get()) },
            { v -> preferences.putBoolean(name, v as Boolean) })
    }


    fun registerProperty(property: IntegerProperty, name: String, clazz: Class<*>? = null, id: String = DEFAULT_ID) {
        val preferences = getPreferences(clazz, id)
        doRegister(property as Property<Any?>,
            { preferences.getInt(name, property.get()) },
            { v -> preferences.putInt(name, v as Int) })
    }

    fun registerProperty(property: FloatProperty, name: String, clazz: Class<*>? = null, id: String = DEFAULT_ID) {
        val preferences = getPreferences(clazz, id)
        doRegister(property as Property<Any?>,
            { preferences.getFloat(name, property.get()) },
            { v -> preferences.putFloat(name, v as Float) })
    }

    fun registerProperty(property: StringProperty, name: String, clazz: Class<*>? = null, id: String = DEFAULT_ID) {
        val preferences = getPreferences(clazz, id)
        doRegister(property as Property<Any?>,
            { preferences.get(name, property.get()) },
            { v -> preferences.put(name, v as String) })
    }

    fun <E : Enum<E>> registerProperty(
        property: ObjectProperty<E>,
        name: String,
        enumClass: Class<E>,
        clazz: Class<*>? = null,
        id: String = DEFAULT_ID
    ) {
        val preferences = getPreferences(clazz, id)
        doRegister(property as Property<Any?>,
            {
                val pref = preferences.get(name, property.get()?.name)
                return@doRegister enumClass.enumConstants.firstOrNull { it.name == pref }
            },
            { v -> preferences.put(name, (v as Enum<*>).name) })
    }

    companion object {
        const val DEFAULT_ID = "main"
    }
}