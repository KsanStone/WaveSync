package me.ksanstone.wavesync.wavesync.service.audio.backend.xt

import me.ksanstone.wavesync.wavesync.service.audio.backend.AudioSystem
import xt.audio.Enums

class XtAudioSystem(val system: Enums.XtSystem) : AudioSystem {
    override val name: String
        get() = system.name
}