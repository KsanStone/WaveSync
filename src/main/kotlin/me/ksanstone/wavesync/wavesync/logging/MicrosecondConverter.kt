package me.ksanstone.wavesync.wavesync.logging

import ch.qos.logback.classic.pattern.ClassicConverter
import ch.qos.logback.classic.spi.ILoggingEvent
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MicrosecondConverter : ClassicConverter() {

    override fun convert(event: ILoggingEvent): String {
        val now = Instant.now()
        return FORMATTER.format(now)
    }

    companion object {
        private val FORMATTER: DateTimeFormatter = DateTimeFormatter
            .ofPattern("dd/MM HH:mm:ss.SSSSSS")
            .withZone(ZoneId.systemDefault())
    }
}
