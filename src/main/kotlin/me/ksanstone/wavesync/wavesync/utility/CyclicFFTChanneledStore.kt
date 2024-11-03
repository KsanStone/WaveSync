package me.ksanstone.wavesync.wavesync.utility

class CyclicFFTChanneledStore : ChannelStore<RollingBuffer<Float>>() {

    private var channelData: Array<Channel<RollingBuffer<Float>>> = arrayOf()

    @Suppress("UNCHECKED_CAST")
    override fun resize(channels: Int, channelSize: Int): ChannelStore<RollingBuffer<Float>> {
        val newData = arrayOfNulls<Channel<RollingBuffer<Float>>>(channels)
        for (i in 0 until channels) {
            newData[i] = Channel(RollingBuffer(channelSize) { -0.0f })
        }
        channelData = newData as Array<Channel<RollingBuffer<Float>>>
        return this
    }

    override fun label(vararg labels: ChannelLabel): ChannelStore<RollingBuffer<Float>> {
        if (labels.size > channelData.size) throw IllegalArgumentException("Too many labels :O")
        for ((i, channelLabel) in labels.withIndex())
            channelData[i].label = channelLabel
        return this
    }

    override operator fun get(idx: Int): Channel<RollingBuffer<Float>> {
        return channelData[idx]
    }

    override fun channels(): Int {
        return channelData.size
    }

}