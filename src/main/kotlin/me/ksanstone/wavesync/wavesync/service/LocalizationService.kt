package me.ksanstone.wavesync.wavesync.service

import jakarta.annotation.PostConstruct
import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleObjectProperty
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.Resource
import org.springframework.stereotype.Service
import java.text.MessageFormat
import java.text.NumberFormat
import java.util.*


@Service
class LocalizationService {

    val logger: Logger = LoggerFactory.getLogger(javaClass)

    @Value("\${:classpath:bundles/*.properties}")
    lateinit var localeResources: Array<Resource>
    private lateinit var locales: List<Locale>
    lateinit var numberFormat: NumberFormat
    private val currentLocaleProperty: ObjectProperty<Locale> = SimpleObjectProperty(Locale.ENGLISH)

    @PostConstruct
    fun initialize() {
        locales =
            localeResources.map { Locale.forLanguageTag(localeRegex.matchEntire(it.filename!!)!!.groups["code"]!!.value) }
        logger.info("Locales detected: $locales")

        currentLocaleProperty.set(locales[0])

        currentLocaleProperty.addListener { _ ->
            numberFormat = NumberFormat.getNumberInstance(currentLocaleProperty.get())
        }
        numberFormat = NumberFormat.getNumberInstance(currentLocaleProperty.get())
    }

    private fun getBundle(locale: Locale): ResourceBundle {
        return ResourceBundle.getBundle("bundles/messages", locale)
    }

    fun getDefault(): ResourceBundle {
        return getBundle(currentLocaleProperty.get())
    }

    fun format(key: String, vararg args: Any): String {
        if (key.isDirectKey()) return key.extractDirectMessage()
        return MessageFormat(getDefault().getString(key)).format(args)
    }

    fun get(key: String): String {
        if (key.isDirectKey()) return key.extractDirectMessage()
        return getDefault().getString(key)
    }

    fun formatNumber(number: Number, unit: String = ""): String {
        return numberFormat.format(number) + unit
    }

    private val localeRegex = Regex("^messages_(?<code>[a-zA-Z#_]{1,50})\\.properties$")
}