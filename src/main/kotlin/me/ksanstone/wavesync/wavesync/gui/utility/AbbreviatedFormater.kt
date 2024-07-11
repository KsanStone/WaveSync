package me.ksanstone.wavesync.wavesync.gui.utility

import java.text.DecimalFormat
import java.text.FieldPosition
import java.text.NumberFormat
import java.text.ParsePosition

class AbbreviatedFormatter : NumberFormat() {

    private val numFormat = DecimalFormat("###,###.##")

    override fun format(number: Double, toAppendTo: StringBuffer?, pos: FieldPosition?): StringBuffer {
        val formattedNumber = if (number < 1000) {
            numFormat.format(number)
        } else {
            "${numFormat.format(number / 1000).toInt()}k"
        }
        return toAppendTo?.append(formattedNumber) ?: StringBuffer(formattedNumber)
    }

    override fun format(number: Long, toAppendTo: StringBuffer?, pos: FieldPosition?): StringBuffer {
        val formattedNumber = if (number < 1000) {
            numFormat.format(number)
        } else {
            "${numFormat.format(number / 1000)}k"
        }
        return toAppendTo?.append(formattedNumber) ?: StringBuffer(formattedNumber)
    }

    override fun parse(source: String?, parsePosition: ParsePosition?): Number {
        source?.let {
            val processed = source.replace(Regex("[.]"), "")
            if (processed.endsWith("k")) {
                val numPart = processed.substring(0, processed.length - 1)
                try {
                    val number = numPart.toDouble() * 1000
                    parsePosition?.index = processed.length
                    return number
                } catch (e: NumberFormatException) {
                    parsePosition?.errorIndex = 0
                    return 0
                }
            } else {
                try {
                    val number = processed.toDouble()
                    parsePosition?.index = processed.length
                    return number
                } catch (e: NumberFormatException) {
                    parsePosition?.errorIndex = 0
                    return 0
                }
            }
        }
        parsePosition?.errorIndex = 0
        return 0
    }
}