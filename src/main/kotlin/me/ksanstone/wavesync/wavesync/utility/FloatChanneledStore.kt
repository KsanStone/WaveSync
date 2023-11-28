package me.ksanstone.wavesync.wavesync.utility

class FloatChanneledStore : ChannelStore<FloatArray>() {

    private lateinit var channelData: Array<Channel<FloatArray>>

    override fun resize(channels: Int, channelSize: Int): FloatChanneledStore {
        val newData = arrayOfNulls<Channel<FloatArray>>(channels)
        for (i in 0 until channels) {
            newData[i] = Channel(FloatArray(channelSize))
        }
        channelData = newData as Array<Channel<FloatArray>>
        return this
    }

    override fun label(vararg labels: String): ChannelStore<FloatArray> {
        if (labels.size > channelData.size) throw IllegalArgumentException("Too many labels :O")
        for ((i, channelLabel) in labels.withIndex())
            channelData[i].label = channelLabel
        return this
    }

    override operator fun get(idx: Int): Channel<FloatArray> {
        return channelData[0]
    }

    override fun channels(): Int {
        return channelData.size
    }

}