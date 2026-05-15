package tech.notifly.sse

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import tech.notifly.utils.Logger

/**
 * SSE 메시지 라우터 + 라이프사이클 facade.
 * - SSE Connected/Sync/Event/Shutdown/TtlExpired 를 디코딩해 적절한 action 으로 routing.
 * - sync 디바운스 + in-flight 가드.
 * - 일정 시간 내에 .open 도달 못하면 fallback 모드 전이 (호스트 측에서 polling 이나 다른 경로로 동작 결정).
 */
internal class SSEController(
    private val sseClient: SSEClient,
    private val onSyncRequested: (completion: () -> Unit) -> Unit,
    private val onServerEventTriggered: (String, JSONObject?) -> Unit,
    private val syncDebounceMs: Long = DEFAULT_SYNC_DEBOUNCE_MS,
    private val fallbackAfterAttempts: Int = DEFAULT_FALLBACK_AFTER_ATTEMPTS,
    private val scheduler: (delayMs: Long, work: () -> Unit) -> Unit = ::defaultScheduler,
) {
    enum class Mode { SSE, FALLBACK }

    private val stateLock = Any()
    private var hasReachedOpen = false
    private var modeInternal: Mode = Mode.SSE
    private var syncInFlight = false
    private var pendingSyncDispatch = false
    private var generation: Long = 0L

    init {
        sseClient.onMessage = { type, data -> handleMessage(type, data) }
        sseClient.onState = { state -> handleStateChange(state) }
    }

    val mode: Mode get() = synchronized(stateLock) { modeInternal }

    fun start() {
        Logger.d("[sse] SSEController.start")
        sseClient.connect()
    }

    fun stop() {
        Logger.d("[sse] SSEController.stop")
        synchronized(stateLock) { generation += 1 }
        sseClient.disconnect()
    }

    fun reconnect(reason: String) {
        Logger.i("[sse] reconnect requested: $reason")
        sseClient.disconnect()
        sseClient.connect()
    }

    internal fun handleMessage(
        type: String,
        data: String,
    ) {
        Logger.d("[sse] message type=$type bytes=${data.length}")
        when (val message = SSEMessage.decode(type, data)) {
            is SSEMessage.Connected ->
                synchronized(stateLock) {
                    hasReachedOpen = true
                    modeInternal = Mode.SSE
                }
            is SSEMessage.Sync -> triggerSyncDebounced()
            is SSEMessage.Event -> onServerEventTriggered(message.name, message.eventParams)
            is SSEMessage.Shutdown -> handleShutdown(message.reconnectInMs)
            is SSEMessage.TtlExpired -> reconnect("ttl-expired")
            is SSEMessage.Unknown -> Logger.i("[sse] unknown type: ${message.rawType}")
            is SSEMessage.Malformed -> Logger.e("[sse] malformed type: ${message.rawType}")
        }
    }

    internal fun handleStateChange(state: SSEClient.State) {
        when (state) {
            is SSEClient.State.Open ->
                synchronized(stateLock) {
                    hasReachedOpen = true
                    modeInternal = Mode.SSE
                }
            is SSEClient.State.Reconnecting -> {
                val shouldFallback =
                    synchronized(stateLock) {
                        if (!hasReachedOpen &&
                            state.attempt >= fallbackAfterAttempts &&
                            modeInternal != Mode.FALLBACK
                        ) {
                            modeInternal = Mode.FALLBACK
                            true
                        } else {
                            false
                        }
                    }
                if (shouldFallback) {
                    Logger.i("[sse] entering fallback mode (attempt=${state.attempt})")
                    sseClient.disconnect()
                }
            }
            else -> Unit
        }
    }

    private fun triggerSyncDebounced() {
        val shouldDispatch =
            synchronized(stateLock) {
                if (pendingSyncDispatch || syncInFlight) {
                    false
                } else {
                    pendingSyncDispatch = true
                    syncInFlight = true
                    true
                }
            }
        if (!shouldDispatch) return
        onSyncRequested {
            synchronized(stateLock) { syncInFlight = false }
        }
        scheduler(syncDebounceMs) {
            synchronized(stateLock) { pendingSyncDispatch = false }
        }
    }

    private fun handleShutdown(reconnectInMs: Int) {
        Logger.i("[sse] shutdown, reconnect in ${reconnectInMs}ms")
        sseClient.disconnect()
        val scheduledGen = synchronized(stateLock) { generation }
        val delay = maxOf(reconnectInMs.toLong(), 0L)
        scheduler(delay) {
            val currentGen = synchronized(stateLock) { generation }
            if (currentGen == scheduledGen) sseClient.connect()
        }
    }

    companion object {
        const val DEFAULT_SYNC_DEBOUNCE_MS: Long = 500
        const val DEFAULT_FALLBACK_AFTER_ATTEMPTS: Int = 3

        private fun defaultScheduler(
            delayMs: Long,
            work: () -> Unit,
        ) {
            Handler(Looper.getMainLooper()).postDelayed(work, delayMs)
        }
    }
}
