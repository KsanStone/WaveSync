package me.ksanstone.wavesync.wavesync.service

fun String.toDirectKey(): String {
    return "%%$this"
}

fun String.isDirectKey(): Boolean {
    return this.startsWith("%%")
}

fun String.extractDirectMessage(): String {
    return this.substring(2)
}