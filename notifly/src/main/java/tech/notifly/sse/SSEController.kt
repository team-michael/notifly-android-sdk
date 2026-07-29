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
    private val canConnect: () -> Boolean = { true },
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
        connectIfForeground()
    }

    fun stop() {
        synchronized(stateLock) { generation += 1 }
        sseClient.disconnect()
    }

    /**
     * Transport 만 disconnect 한다. controller / sseClient 인스턴스는 유지되어
     * lastEventId 가 보존되고, start() 호출로 같은 클라이언트가 다시 connect 한다.
     * BG 진입 같이 일시 중단 후 곧 같은 user 로 재개되는 경우에 사용.
     */
    fun pause() {
        sseClient.disconnect()
    }

    fun reconnect(reason: String) {
        sseClient.disconnect()
        connectIfForeground()
    }

    internal fun handleMessage(
        type: String,
        data: String,
    ) {
        when (val message = SSEMessage.decode(type, data)) {
            is SSEMessage.Connected ->
                synchronized(stateLock) {
                    hasReachedOpen = true
                    modeInternal = Mode.SSE
                }
            is SSEMessage.Sync -> {
                Logger.i("[sse] sync received")
                triggerSyncDebounced()
            }
            is SSEMessage.Event -> {
                onServerEventTriggered(message.name, message.eventParams)
            }
            is SSEMessage.Shutdown -> handleShutdown(message.reconnectInMs)
            is SSEMessage.TtlExpired -> reconnect("ttl-expired")
            is SSEMessage.Unknown -> Unit
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
        sseClient.disconnect()
        val scheduledGen = synchronized(stateLock) { generation }
        val delay = maxOf(reconnectInMs.toLong(), 0L)
        scheduler(delay) {
            val currentGen = synchronized(stateLock) { generation }
            if (currentGen == scheduledGen) connectIfForeground()
        }
    }

    private fun connectIfForeground() {
        if (canConnect()) sseClient.connect()
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
