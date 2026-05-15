package tech.notifly.sse

import org.json.JSONException
import org.json.JSONObject

/**
 * 서버가 보내는 SSE event 의 protocol 디코더.
 * iOS SDK 의 SSEMessage 와 정합.
 */
internal sealed class SSEMessage {
    object Connected : SSEMessage()

    object Sync : SSEMessage()

    data class Event(val name: String, val eventParams: JSONObject?) : SSEMessage()

    data class Shutdown(val reconnectInMs: Int) : SSEMessage()

    object TtlExpired : SSEMessage()

    data class Unknown(val rawType: String) : SSEMessage()

    data class Malformed(val rawType: String) : SSEMessage()

    companion object {
        fun decode(
            type: String,
            data: String,
        ): SSEMessage =
            when (type) {
                TYPE_CONNECTED -> Connected
                TYPE_SYNC -> Sync
                TYPE_SERVER_EVENT -> decodeServerEvent(data, type)
                TYPE_SHUTDOWN -> decodeShutdown(data)
                TYPE_TTL_EXPIRED -> TtlExpired
                else -> Unknown(type)
            }

        private fun decodeServerEvent(
            data: String,
            type: String,
        ): SSEMessage {
            val json = parseJson(data) ?: return Malformed(type)
            val name = json.optString("name", "")
            if (name.isEmpty()) return Malformed(type)
            val params = json.optJSONObject("eventParams")
            return Event(name, params)
        }

        private fun decodeShutdown(data: String): SSEMessage {
            val ms = parseJson(data)?.optInt("reconnectInMs", 0) ?: 0
            return Shutdown(maxOf(ms, 0))
        }

        private fun parseJson(data: String): JSONObject? =
            try {
                JSONObject(data)
            } catch (e: JSONException) {
                null
            }

        private const val TYPE_CONNECTED = "connected"
        private const val TYPE_SYNC = "sync"
        private const val TYPE_SERVER_EVENT = "server-event"
        private const val TYPE_SHUTDOWN = "shutdown"
        private const val TYPE_TTL_EXPIRED = "ttl-expired"
    }
}
