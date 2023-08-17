package me.ksanstone.wavesync.wavesync

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.ConfigurableApplicationContext

@SpringBootApplication
class WaveSyncBootApplication(
  private val applicationContext: ConfigurableApplicationContext
) {

    init {
        WaveSyncBootApplication.applicationContext = applicationContext
    }

    companion object {
        lateinit var applicationContext: ConfigurableApplicationContext
    }
}