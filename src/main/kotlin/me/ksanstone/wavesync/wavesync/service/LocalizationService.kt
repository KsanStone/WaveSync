package me.ksanstone.wavesync.wavesync.service

import jakarta.annotation.PostConstruct
import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleObjectProperty
import org.springframework.stereotype.Service
import java.text.MessageFormat
import java.text.NumberFormat
import java.util.*

@Service
class LocalizationService {

    val locales = listOf(Locale.ENGLISH)
    val currentLocaleProperty: ObjectProperty<Locale> = SimpleObjectProperty(Locale.ENGLISH)

    lateinit var numberFormat: NumberFormat

    @PostConstruct
    fun initialize() {
        currentLocaleProperty.addListener { _ ->
            numberFormat = NumberFormat.getNumberInstance(currentLocaleProperty.get())
        }
        numberFormat = NumberFormat.getNumberInstance(currentLocaleProperty.get())
    }

    fun getBundle(locale: Locale): ResourceBundle {
        return ResourceBundle.getBundle("bundles/messages", locale)
    }

    fun getDefault(): ResourceBundle {
        return getBundle(currentLocaleProperty.get())
    }

    fun format(key: String, vararg args: Any): String {
        return MessageFormat(getDefault().getString(key)).format(args)
    }

    fun get(key: String): String {
        return getDefault().getString(key)
    }

    fun formatNumber(number: Number, unit: String = ""): String {
        return numberFormat.format(number) + unit
    }

}