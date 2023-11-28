package me.ksanstone.wavesync.wavesync.utility

abstract class ChannelStore<E> {

    abstract fun resize(channels: Int, channelSize: Int): ChannelStore<E>
    abstract fun label(vararg labels: String): ChannelStore<E>

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
    var label: String = "<none>"
)

enum class CommonChannel(val label: String) {
    MASTER("master"),
    LEFT("left"),
    RIGHT("right")
}

fun interface ChannelStoreDataChangeListener<E> {
    fun call(store: ChannelStore<E>)
}