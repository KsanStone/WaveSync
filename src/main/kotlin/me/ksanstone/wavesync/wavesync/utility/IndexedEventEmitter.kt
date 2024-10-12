package me.ksanstone.wavesync.wavesync.utility

import java.lang.ref.WeakReference
import java.util.*
import java.util.function.Consumer

class EventEmitter<T : Any> : IndexedEventEmitter<Class<out T>, T>() {
    fun publish(event: T) {
        publishFor(event.javaClass, event)
    }

    /**
     * Bubble all events to parent.
     */
    @Synchronized
    fun bubbleTo(emitter: EventEmitter<T>) {
        val emitterReference = WeakReference(emitter)
        var lambda: Consumer<T>? = null
        lambda = Consumer {
            val eventEmitter: EventEmitter<T>? = emitterReference.get()
            if (eventEmitter != null) {
                eventEmitter.publish(it)
            } else {
                off(lambda!!)
            }
        }
        on(lambda)
    }
}

open class IndexedEventEmitter<I, T : Any> {

    protected val listenerMap: MutableMap<I?, MutableList<Consumer<out T>>> =
        Collections.synchronizedMap(mutableMapOf())

    @Synchronized
    fun on(index: I?, observer: Consumer<out T>) {
        if (listenerMap.containsKey(index)) {
            listenerMap[index]!!.add(observer)
        } else {
            listenerMap[index] = mutableListOf(observer)
        }
    }

    @Synchronized
    fun on(observer: Consumer<T>) {
        on(null, observer)
    }

    @Synchronized
    fun off(observer: Consumer<T>) {
        for ((k, v) in listenerMap) {
            if (v.remove(observer)) {
                if (v.size == 0) {
                    listenerMap.remove(k)
                    return
                }
                return
            }
        }
    }

    @Synchronized
    @Suppress("UNCHECKED_CAST")
    fun publishFor(index: I, event: T) {
        listenerMap[index]?.forEach { (it as Consumer<in T>).accept(event) }
        listenerMap[null]?.forEach { (it as Consumer<in T>).accept(event) }
    }

    @Synchronized
    fun forEachIndex(lambda: (index: I) -> Unit) {
        indices.forEach { if (it != null) lambda(it) }
    }

    val indices: Set<I?>
        get() = listenerMap.keys
}