package tech.notifly.utils

import kotlin.math.floor

object NotiflyTimerUtil {
    private val initialTimestampMillis = System.currentTimeMillis()
    private val start = System.nanoTime()

    fun getTimestampMicros(): Long {
        val elapsedNanos = System.nanoTime() - start
        return (initialTimestampMillis * 1000) + (elapsedNanos / 1000)
    }

    fun getTimestampMillis(): Long {
        return System.currentTimeMillis()
    }

    fun getTimestampSeconds(): Int {
        return floor(getTimestampMillis() / 1000.0).toInt()
    }
}
