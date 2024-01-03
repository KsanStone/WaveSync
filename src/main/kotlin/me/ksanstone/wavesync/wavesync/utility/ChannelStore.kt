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
    val data: E,
    var label: ChannelLabel = ChannelLabel.UNDEFINED
)

enum class CommonChannel(val label: ChannelLabel) {
    MASTER(ChannelLabel("master", "A")),
    LEFT(ChannelLabel("left", "L")),
    RIGHT(ChannelLabel("right", "R"));

    companion object {

        /**
         * Attempts to resolve a [CommonChannel] from the channel name
         */
        fun resolve(name: String): CommonChannel? {
            // TODO
            return when(name) {
                "SPEAKER_FRONT_LEFT" -> LEFT
                "SPEAKER_FRONT_RIGHT" -> RIGHT
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