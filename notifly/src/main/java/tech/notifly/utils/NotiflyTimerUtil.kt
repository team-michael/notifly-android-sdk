package tech.notifly.utils

import kotlin.math.floor

object NotiflyTimerUtil {
    private var lastTimestampMillis: Long = 0

    @Volatile
    private var sequenceCounter = 0

    fun getTimestampMicros(): Long {
        val epochTimestamp = System.currentTimeMillis()
        if (epochTimestamp == lastTimestampMillis) {
            sequenceCounter++
        } else {
            lastTimestampMillis = epochTimestamp
            sequenceCounter = 0
        }

        if (sequenceCounter >= 1000) {
            Logger.v("Timestamp counter overflow. Millisecond timestamp may be larger than actual time.")
        }
        return lastTimestampMillis * 1000 + sequenceCounter
    }

    fun getTimestampMillis(): Long {
        return System.currentTimeMillis()
    }

    fun getTimestampSeconds(): Int {
        return floor(getTimestampMillis() / 1000.0).toInt()
    }
}
