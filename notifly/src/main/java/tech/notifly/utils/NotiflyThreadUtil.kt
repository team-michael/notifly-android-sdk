package tech.notifly.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.concurrent.thread

fun suspendifyBlocking(block: suspend () -> Unit) {
    runBlocking {
        block()
    }
}

fun suspendifyOnMain(block: suspend () -> Unit) {
    thread {
        try {
            runBlocking {
                withContext(Dispatchers.Main) {
                    block()
                }
            }
        } catch (e: Exception) {
            Logger.e("Exception on thread with switch to main", e)
        }
    }
}

fun suspendifyOnThread(
    priority: Int = -1,
    block: suspend () -> Unit,
) {
    thread(priority = priority) {
        try {
            runBlocking {
                block()
            }
        } catch (e: Exception) {
            Logger.e("Exception on thread", e)
        }
    }
}

fun suspendifyOnThread(
    name: String,
    priority: Int = -1,
    block: suspend () -> Unit,
) {
    thread(name = name, priority = priority) {
        try {
            runBlocking {
                block()
            }
        } catch (e: Exception) {
            Logger.e("Exception on thread '$name'", e)
        }
    }
}
