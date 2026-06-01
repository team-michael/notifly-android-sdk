package tech.notifly.sse

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import tech.notifly.utils.Logger
import java.net.URLEncoder
import kotlin.math.max
import kotlin.math.min

/**
 * SSE 연결의 트랜스포트 계층.
 * - 지수 백오프 재연결 (지터 포함)
 * - heartbeat watchdog (heartbeatTimeoutMs 동안 데이터 없으면 throw → 재연결)
 * - Last-Event-ID 헤더 자동 부착
 * - connect()/disconnect() 멱등. host app crash 유발 가능 패턴 (`!!`, `error()`, `require()` 등) 사용 금지.
 */
internal class SSEClient(
    private val projectId: String,
    private val notiflyUserId: String,
    private val deviceId: String?,
    private val baseUrl: String,
    private val tokenProvider: suspend () -> String,
    private val sdkVersionHeader: String,
    private val backoffScheduleMs: List<Long> = DEFAULT_BACKOFF_SCHEDULE_MS,
    private val heartbeatTimeoutMs: Long = DEFAULT_HEARTBEAT_TIMEOUT_MS,
    private val openStableThresholdMs: Long = DEFAULT_OPEN_STABLE_THRESHOLD_MS,
    private val provider: StreamLineProvider = DefaultStreamLineProvider(),
    private val jitterProvider: () -> Double = ::defaultJitter,
    private val nowProvider: () -> Long = System::currentTimeMillis,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
) {
    sealed class State {
        object Idle : State()

        object Connecting : State()

        object Open : State()

        data class Reconnecting(
            val attempt: Int,
        ) : State()

        object Stopped : State()
    }

    sealed class ConnectionError(
        message: String,
    ) : RuntimeException(message) {
        object InvalidResponse : ConnectionError("invalid response")

        data class HttpStatus(
            val code: Int,
        ) : ConnectionError("http status $code")

        object HeartbeatTimeout : ConnectionError("heartbeat timeout")
    }

    @Volatile var onState: ((State) -> Unit)? = null

    @Volatile var onMessage: ((String, String) -> Unit)? = null

    private val stateLock = Any()
    private var stateInternal: State = State.Idle
    private var lastEventIdInternal: String? = null
    private var connectJob: Job? = null
    private var lastDataAt: Long = nowProvider()
    private var lastOpenAt: Long? = null

    val state: State get() = synchronized(stateLock) { stateInternal }

    val lastEventId: String? get() = synchronized(stateLock) { lastEventIdInternal }

    fun connect() {
        var didStart = false
        synchronized(stateLock) {
            when (stateInternal) {
                State.Connecting, State.Open, is State.Reconnecting -> return
                else -> Unit
            }
            connectJob?.cancel()
            stateInternal = State.Connecting
            connectJob = scope.launch { runConnectionLoop() }
            didStart = true
        }
        if (didStart) {
            emitState(State.Connecting)
        }
    }

    fun disconnect() {
        var alreadyStopped = false
        var jobToCancel: Job? = null
        synchronized(stateLock) {
            alreadyStopped = stateInternal == State.Stopped
            jobToCancel = connectJob
            connectJob = null
            stateInternal = State.Stopped
            lastOpenAt = null
        }
        jobToCancel?.cancel()
        if (!alreadyStopped) {
            Logger.i("[sse] disconnected")
            emitState(State.Stopped)
        }
    }

    private suspend fun runConnectionLoop() {
        var attempt = 0
        while (!isStopped()) {
            try {
                runOneConnection(attempt)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Logger.w("[sse] connection error: ${e.message}")
            }
            if (isStopped()) break

            val openedAt = synchronized(stateLock) { lastOpenAt }
            if (openedAt != null && nowProvider() - openedAt >= openStableThresholdMs) {
                attempt = 0
            }
            attempt += 1
            transition(State.Reconnecting(attempt))
            delay(backoffDelayMs(attempt))
        }
    }

    private suspend fun runOneConnection(attempt: Int) {
        if (attempt > 0) transition(State.Connecting)
        setLastDataAt(nowProvider())

        val token = tokenProvider()
        val url = buildUrl()
        val headers = buildHeaders(token, lastEventId)
        val response = provider.open(url, headers)

        if (response.statusCode != HTTP_OK) {
            Logger.e("[sse] handshake failed: status=${response.statusCode} projectId=$projectId")
            runCatching { response.close() }
            throw ConnectionError.HttpStatus(response.statusCode)
        }
        val ct = (response.contentType ?: "").lowercase()
        if (!ct.startsWith("text/event-stream")) {
            Logger.e("[sse] invalid content-type: $ct projectId=$projectId")
            runCatching { response.close() }
            throw ConnectionError.InvalidResponse
        }

        transition(State.Open)
        Logger.i("[sse] connected")
        setLastDataAt(nowProvider())

        // 두 자식 (consume / watchdog) 중 먼저 끝난 쪽을 기다린 뒤 나머지 cancel.
        // coroutineScope 가 outer cancel/throw 시 자동 join 까지 보장.
        coroutineScope {
            val consumeJob = launch { consumeStream(response.lines) }
            val watchdogJob = launch { runHeartbeatWatchdog() }
            select<Unit> {
                consumeJob.onJoin { }
                watchdogJob.onJoin { }
            }
            consumeJob.cancel()
            watchdogJob.cancel()
        }
    }

    private suspend fun consumeStream(lines: Flow<String>) {
        val parser = SSELineParser()
        lines.collect { line ->
            setLastDataAt(nowProvider())
            val event = parser.feed(line) ?: return@collect
            event.id?.let { setLastEventId(it) }
            emitMessage(event.type, event.data)
        }
    }

    private suspend fun runHeartbeatWatchdog() {
        val checkInterval =
            max(
                min(heartbeatTimeoutMs / WATCHDOG_RESOLUTION_DIVISOR, WATCHDOG_MAX_INTERVAL_MS),
                WATCHDOG_MIN_INTERVAL_MS,
            )
        while (true) {
            delay(checkInterval)
            val elapsed = nowProvider() - getLastDataAt()
            if (elapsed > heartbeatTimeoutMs) {
                throw ConnectionError.HeartbeatTimeout
            }
        }
    }

    private fun isStopped(): Boolean = synchronized(stateLock) { stateInternal == State.Stopped }

    private fun transition(new: State) {
        var changed = false
        synchronized(stateLock) {
            if (stateInternal == State.Stopped) return
            if (stateInternal != new) {
                stateInternal = new
                changed = true
            }
            lastOpenAt = if (new is State.Open) nowProvider() else null
        }
        if (changed) {
            emitState(new)
        }
    }

    private fun emitState(s: State) {
        val cb = onState ?: return
        runCatching { cb(s) }.onFailure { Logger.e("[sse] onState callback threw", it) }
    }

    private fun emitMessage(
        type: String,
        data: String,
    ) {
        val cb = onMessage ?: return
        runCatching { cb(type, data) }.onFailure { Logger.e("[sse] onMessage callback threw", it) }
    }

    private fun setLastDataAt(t: Long) {
        synchronized(stateLock) { lastDataAt = t }
    }

    private fun getLastDataAt(): Long = synchronized(stateLock) { lastDataAt }

    private fun setLastEventId(id: String) {
        synchronized(stateLock) { lastEventIdInternal = id }
    }

    private fun backoffDelayMs(attempt: Int): Long {
        val idx = min(max(attempt - 1, 0), backoffScheduleMs.size - 1)
        val base = backoffScheduleMs[idx]
        return (base.toDouble() * jitterProvider()).toLong().coerceAtLeast(100)
    }

    private fun buildUrl(): String {
        val base = baseUrl.trimEnd('/')
        val path = "/projects/${urlSegment(projectId)}/users/${urlSegment(notiflyUserId)}/streams"
        val query =
            deviceId?.takeIf { it.isNotEmpty() }?.let { "?deviceId=${URLEncoder.encode(it, "UTF-8")}" } ?: ""
        return "$base$path$query"
    }

    private fun urlSegment(s: String): String =
        URLEncoder
            .encode(s, "UTF-8")
            .replace("+", "%20")
            .replace("%2F", "%2F")

    private fun buildHeaders(
        token: String,
        lastEventId: String?,
    ): Map<String, String> {
        val map = LinkedHashMap<String, String>()
        map["Authorization"] = "Bearer $token"
        map["x-notifly-sdk-version"] = sdkVersionHeader
        map["Accept"] = "text/event-stream"
        // 압축 해제 / proxy buffering 회피 — iOS 와 정합.
        map["Accept-Encoding"] = "identity"
        map["Cache-Control"] = "no-cache"
        if (!lastEventId.isNullOrEmpty()) {
            map["Last-Event-ID"] = lastEventId
        }
        return map
    }

    companion object {
        val DEFAULT_BACKOFF_SCHEDULE_MS = listOf(10_000L)
        const val DEFAULT_HEARTBEAT_TIMEOUT_MS: Long = 60_000L
        const val DEFAULT_OPEN_STABLE_THRESHOLD_MS: Long = 30_000L
        private const val HTTP_OK = 200
        private const val LINE_LOG_LIMIT = 120
        private const val WATCHDOG_RESOLUTION_DIVISOR = 4L
        private const val WATCHDOG_MAX_INTERVAL_MS = 5_000L
        private const val WATCHDOG_MIN_INTERVAL_MS = 50L

        private fun defaultJitter(): Double = Math.random()
    }
}
