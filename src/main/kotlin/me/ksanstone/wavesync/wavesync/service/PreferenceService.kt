package me.ksanstone.wavesync.wavesync.service

import jakarta.annotation.PostConstruct
import javafx.beans.property.*
import javafx.scene.paint.Color
import javafx.util.Duration
import me.ksanstone.wavesync.wavesync.gui.gradient.pure.GradientSerializer
import me.ksanstone.wavesync.wavesync.gui.gradient.pure.SGradient
import org.springframework.stereotype.Service
import java.util.prefs.Preferences
import kotlin.jvm.optionals.getOrElse

@Suppress("UNCHECKED_CAST")
@Service
class PreferenceService(
    private val gradientSerializer: GradientSerializer
) {

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

    fun <E : Any> registerProperty(
        property: ObjectProperty<E>, name: String, clazz: Class<*>? = null, id: String = DEFAULT_ID,
        serializer: (obj: E) -> String, deserializer: (str: String) -> E
    ) {
        val preferences = getPreferences(clazz, id)
        doRegister(property as Property<Any?>,
            { deserializer(preferences.get(name, serializer(property.get()))) },
            { v -> preferences.put(name, serializer(v as E)) })
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

    fun registerProperty(property: LongProperty, name: String, clazz: Class<*>? = null, id: String = DEFAULT_ID) {
        val preferences = getPreferences(clazz, id)
        doRegister(property as Property<Any?>,
            { preferences.getLong(name, property.get()) },
            { v -> preferences.putLong(name, v as Long) })
    }

    fun registerProperty(property: FloatProperty, name: String, clazz: Class<*>? = null, id: String = DEFAULT_ID) {
        val preferences = getPreferences(clazz, id)
        doRegister(property as Property<Any?>,
            { preferences.getFloat(name, property.get()) },
            { v -> preferences.putFloat(name, v as Float) })
    }

    fun registerProperty(property: DoubleProperty, name: String, clazz: Class<*>? = null, id: String = DEFAULT_ID) {
        val preferences = getPreferences(clazz, id)
        doRegister(property as Property<Any?>,
            { preferences.getDouble(name, property.get()) },
            { v -> preferences.putDouble(name, v as Double) })
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

    fun registerDurationProperty(
        property: ObjectProperty<Duration>,
        name: String,
        clazz: Class<*>? = null,
        id: String = DEFAULT_ID
    ) {
        registerProperty(
            property,
            name,
            clazz,
            id,
            { e -> e.toMillis().toString() },
            { str -> Duration.millis(str.toDouble()) })
    }

    fun registerColorProperty(
        property: ObjectProperty<Color>,
        name: String,
        clazz: Class<*>? = null,
        id: String = DEFAULT_ID
    ) {
        val preferences = getPreferences(clazz, id)
        doRegister(property as Property<Any?>,
            { intToColor(preferences.getInt(name, colorToInt(property.get()))) },
            { v -> preferences.putInt(name, colorToInt(v as Color)) })
    }

    fun registerSGradientProperty(
        property: ObjectProperty<SGradient>,
        name: String,
        clazz: Class<*>? = null,
        id: String = DEFAULT_ID
    ) {
        val preferences = getPreferences(clazz, id)
        doRegister(property as Property<Any?>,
            {
                gradientSerializer.deserialize(preferences.get(name, gradientSerializer.serialize(property.get())))
                    .getOrElse { gradientSerializer.serialize(property.get()) }
            },
            { v -> preferences.put(name, gradientSerializer.serialize(v as SGradient)) })
    }

    private fun colorToInt(c: Color): Int {
        val r = Math.round(c.red * 255).toInt()
        val g = Math.round(c.green * 255).toInt()
        val b = Math.round(c.blue * 255).toInt()
        return r shl 16 or (g shl 8) or b
    }

    private fun intToColor(value: Int): Color {
        val r = value ushr 16 and 0xFF
        val g = value ushr 8 and 0xFF
        val b = value and 0xFF
        return Color.rgb(r, g, b)
    }

    fun unregisterObjectTree(clazz: Class<*>? = null, id: String = DEFAULT_ID) {
        getPreferences(clazz, id).removeNode()
    }

    fun purgeAllData() {
        rootPreferences.removeNode()
    }

    companion object {
        const val DEFAULT_ID = "main"
    }
}