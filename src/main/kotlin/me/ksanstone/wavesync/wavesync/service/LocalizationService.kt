package me.ksanstone.wavesync.wavesync.service

import jakarta.annotation.PostConstruct
import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleObjectProperty
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.MessageFormat
import java.text.NumberFormat
import java.util.*


@Service
class LocalizationService {

    val logger: Logger = LoggerFactory.getLogger(javaClass)

    lateinit var locales: List<Locale>
    val currentLocaleProperty: ObjectProperty<Locale> = SimpleObjectProperty(Locale.ENGLISH)

    lateinit var numberFormat: NumberFormat

    @PostConstruct
    fun initialize() {
        val detectedMessages = findLocales()
        locales = detectedMessages.map { Locale.of(localeRegex.matchEntire(it)!!.groups["code"]!!.value) }
        logger.info("Locales detected: $locales")

        currentLocaleProperty.set(locales[0])

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

    private val localeRegex = Regex("^messages_(?<code>[a-zA-Z#_]{1,50})\\.properties$")

    fun findLocales(): List<String> {
        val detectedLocales: MutableList<String> = mutableListOf()

        javaClass.classLoader.getResourceAsStream("bundles").use { `in` ->
            if (`in` == null) return detectedLocales
            BufferedReader(InputStreamReader(`in`)).use { br ->
                var resource: String? = null
                while (br.readLine()?.also{ resource = it } != null) {
                    detectedLocales.add(resource!!)
                }
            }
        }

        return detectedLocales.filter { localeRegex.matches(it) }
    }
}