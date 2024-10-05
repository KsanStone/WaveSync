package me.ksanstone.wavesync.wavesync.utility

class FloatChanneledStore : ChannelStore<FloatArray>() {

    private var channelData: Array<Channel<FloatArray>> = arrayOf()
    private var channelSizeHints: Array<Int> = arrayOf()

    @Suppress("UNCHECKED_CAST")
    override fun resize(channels: Int, channelSize: Int): FloatChanneledStore {
        val newData = arrayOfNulls<Channel<FloatArray>>(channels)
        channelSizeHints = Array(channels) { 0 }
        for (i in 0 until channels) {
            newData[i] = Channel(FloatArray(channelSize))
        }
        channelData = newData as Array<Channel<FloatArray>>
        return this
    }

    fun label(vararg labels: String): ChannelStore<FloatArray> {
        if (labels.size > channelData.size) throw IllegalArgumentException("Too many labels :O")
        for ((i, channelLabel) in labels.withIndex())
            channelData[i].label = ChannelLabel.resolve(channelLabel)
        return this
    }

    override fun label(vararg labels: ChannelLabel): ChannelStore<FloatArray> {
        if (labels.size > channelData.size) throw IllegalArgumentException("Too many labels :O")
        for ((i, channelLabel) in labels.withIndex())
            channelData[i].label = channelLabel
        return this
    }

    override operator fun get(idx: Int): Channel<FloatArray> {
        return channelData[idx]
    }

    fun setSizeHint(idx: Int, hint: Int) {
        channelSizeHints[idx] = hint
    }

    fun sizeHint(idx: Int): Int {
        return channelSizeHints[idx]
    }

    override fun channels(): Int {
        return channelData.size
    }

}