package me.ksanstone.wavesync.wavesync.service.downsampling

interface DownSampler<E : Number> {

    fun downSample(samples: List<E>, targetSize: Int): List<E>

}