package tech.notifly.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Collections

open class EventProducer<T> {
    val hasSubscribers: Boolean
        get() = subscribers.any()

    private val subscribers: MutableList<T> = Collections.synchronizedList(mutableListOf())

    fun subscribe(handler: T) {
        synchronized(subscribers) {
            subscribers.add(handler)
        }
    }

    fun unsubscribe(handler: T) {
        synchronized(subscribers) {
            subscribers.remove(handler)
        }
    }

    fun subscribeAll(from: EventProducer<T>) {
        synchronized(subscribers) {
            for (s in from.subscribers) {
                subscribe(s)
            }
        }
    }

    fun fire(callback: (T) -> Unit) {
        val localList = subscribers.toList()
        for (s in localList) {
            callback(s)
        }
    }

    fun fireOnMain(callback: (T) -> Unit) {
        suspendifyOnMain {
            val localList = subscribers.toList()
            for (s in localList) {
                callback(s)
            }
        }
    }

    suspend fun suspendingFire(callback: suspend (T) -> Unit) {
        val localList = subscribers.toList()
        for (s in localList) {
            callback(s)
        }
    }

    suspend fun suspendingFireOnMain(callback: suspend (T) -> Unit) {
        withContext(Dispatchers.Main) {
            val localList = subscribers.toList()
            for (s in localList) {
                callback(s)
            }
        }
    }
}