package me.ksanstone.wavesync.wavesync.utility

import java.util.*
import java.util.function.Consumer

class EventEmitter<I, T> {

    private val listenerMap: MutableMap<I, MutableList<Consumer<T>>> = Collections.synchronizedMap(mutableMapOf())

    fun register(index: I, observer: Consumer<T>) {
        if (listenerMap.containsKey(index)) {
            listenerMap[index]!!.add(observer)
        } else {
            listenerMap[index] = mutableListOf(observer)
        }
    }

    fun publish(index: I, event: T) {
        listenerMap[index]?.forEach {
            it.accept(event)
        }
    }

    val indices: Set<I>
        get() = listenerMap.keys
}