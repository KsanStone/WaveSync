package me.ksanstone.wavesync.wavesync.service.downsampling

class UniformDownSampler<E : Number> : DownSampler<E>{

    @Suppress("UNCHECKED_CAST")
    override fun downSample(samples: List<E>, targetSize: Int): List<E> {
        if (samples.size <= targetSize) return samples

        val scaleDownFactor = samples.size.toDouble() / targetSize.toDouble()
        val out: Array<Any> = Array(targetSize) {}
        var current = 0.0

        for (i in 0 until targetSize) {
            out[i] = samples[current.toInt().coerceIn(0, samples.size - 1)]
            current += scaleDownFactor
        }

        return out.toList() as List<E>
    }

}