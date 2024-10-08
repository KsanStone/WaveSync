package me.ksanstone.wavesync.wavesync.utility

abstract class ChannelStore<E> {

    abstract fun resize(channels: Int, channelSize: Int): ChannelStore<E>
    abstract fun label(vararg labels: ChannelLabel): ChannelStore<E>

    fun label(vararg labels: CommonChannel): ChannelStore<E> {
        return this.label(*(labels.map { it.label }.toTypedArray()))
    }

    val listeners: MutableList<ChannelStoreDataChangeListener<E>> = mutableListOf()

    fun fireDataChanged() {
        for (listener in listeners) {
            listener.call(this)
        }
    }

    abstract operator fun get(idx: Int): Channel<E>
    abstract fun channels(): Int

}

data class Channel<E>(
    var data: E,
    var label: ChannelLabel = ChannelLabel.UNDEFINED
)

enum class CommonChannel(val label: ChannelLabel) {
    MASTER(ChannelLabel("master", "M")),
    FRONT_LEFT(ChannelLabel("front left", "L")),
    FRONT_RIGHT(ChannelLabel("front right", "R")),
    FRONT_CENTER(ChannelLabel("front center", "C")),
    LOW_FREQUENCY(ChannelLabel("low frequency", "LF")),
    BACK_LEFT(ChannelLabel("back left", "BL")),
    BACK_RIGHT(ChannelLabel("back right", "BR")),
    FRONT_LEFT_OF_CENTER(ChannelLabel("front left of center", "FLC")),
    FRONT_RIGHT_OF_CENTER(ChannelLabel("front right of center", "FRC")),
    BACK_CENTER(ChannelLabel("back center", "BC")),
    SIDE_LEFT(ChannelLabel("side left", "SL")),
    SIDE_RIGHT(ChannelLabel("side right", "SR")),
    TOP_CENTER(ChannelLabel("top center", "TC")),
    TOP_FRONT_LEFT(ChannelLabel("top front left", "TFL")),
    TOP_FRONT_CENTER(ChannelLabel("top front center", "TFC")),
    TOP_FRONT_RIGHT(ChannelLabel("top front right", "TFR")),
    TOP_BACK_LEFT(ChannelLabel("top back left", "TBL")),
    TOP_BACK_CENTER(ChannelLabel("top back center", "TBC")),
    TOP_BACK_RIGHT(ChannelLabel("top back right", "TBR"));

    companion object {

        /**
         * Attempts to resolve a [CommonChannel] from the channel name
         */
        fun resolve(name: String): CommonChannel? {
            return when (name) {
                "SPEAKER_FRONT_LEFT" -> FRONT_LEFT
                "SPEAKER_FRONT_RIGHT" -> FRONT_RIGHT
                "SPEAKER_FRONT_CENTER" -> FRONT_CENTER
                "SPEAKER_LOW_FREQUENCY" -> LOW_FREQUENCY
                "SPEAKER_BACK_LEFT" -> BACK_LEFT
                "SPEAKER_BACK_RIGHT" -> BACK_RIGHT
                "SPEAKER_FRONT_LEFT_OF_CENTER" -> FRONT_LEFT_OF_CENTER
                "SPEAKER_FRONT_RIGHT_OF_CENTER" -> FRONT_RIGHT_OF_CENTER
                "SPEAKER_BACK_CENTER" -> BACK_CENTER
                "SPEAKER_SIDE_LEFT" -> SIDE_LEFT
                "SPEAKER_SIDE_RIGHT" -> SIDE_RIGHT
                "SPEAKER_TOP_CENTER" -> TOP_CENTER
                "SPEAKER_TOP_FRONT_LEFT" -> TOP_FRONT_LEFT
                "SPEAKER_TOP_FRONT_CENTER" -> TOP_FRONT_CENTER
                "SPEAKER_TOP_FRONT_RIGHT" -> TOP_FRONT_RIGHT
                "SPEAKER_TOP_BACK_LEFT" -> TOP_BACK_LEFT
                "SPEAKER_TOP_BACK_CENTER" -> TOP_BACK_CENTER
                "SPEAKER_TOP_BACK_RIGHT" -> TOP_BACK_RIGHT
                else -> null
            }
        }
    }
}

data class ChannelLabel(var label: String, var shortcut: String, var fullName: String? = null) {

    /**
     * Returns a clone of this label along with the full name, useful for extending the [CommonChannel]
     */
    fun withFullName(fullName: String): ChannelLabel {
        return ChannelLabel(label, shortcut, fullName)
    }

    companion object {

        /**
         * Attempts to resolve a [CommonChannel] from the channel name.
         * If unsuccessful, returns [ChannelLabel.UNDEFINED] .[withFullName][ChannelLabel.withFullName] (channelName)
         */
        fun resolve(channelName: String): ChannelLabel {
            return (CommonChannel.resolve(channelName)?.label ?: UNDEFINED)
                .withFullName(channelName)
        }

        val UNDEFINED = ChannelLabel("<none>", "?", "?")
    }
}

fun interface ChannelStoreDataChangeListener<E> {
    fun call(store: ChannelStore<E>)
}