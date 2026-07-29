package tech.notifly.sse

import io.mockk.mockk
import io.mockk.verify
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class SSEControllerTest {
    private val client = mockk<SSEClient>(relaxed = true)

    private fun controller(
        onSyncRequested: (completion: () -> Unit) -> Unit = { it() },
        onServerEventTriggered: (String, JSONObject?) -> Unit = { _, _ -> },
        scheduler: (Long, () -> Unit) -> Unit = ImmediateScheduler::invoke,
        fallbackAfterAttempts: Int = 3,
        runIfConnectionAllowed: ((() -> Unit) -> Unit) = { connect -> connect() },
    ): SSEController =
        SSEController(
            sseClient = client,
            onSyncRequested = onSyncRequested,
            onServerEventTriggered = onServerEventTriggered,
            scheduler = scheduler,
            fallbackAfterAttempts = fallbackAfterAttempts,
            runIfConnectionAllowed = runIfConnectionAllowed,
        )

    // foreground 연결 가드

    @Test
    fun start_doesNotConnect_whenConnectionIsNotAllowed() {
        val c = controller(runIfConnectionAllowed = {})

        c.start()

        verify(exactly = 0) { client.connect() }
    }

    @Test
    fun ttlExpiredMessage_doesNotReconnect_whenConnectionIsNotAllowed() {
        val c = controller(runIfConnectionAllowed = {})

        c.handleMessage("ttl-expired", "{}")

        verify { client.disconnect() }
        verify(exactly = 0) { client.connect() }
    }

    @Test
    fun shutdownMessage_doesNotReconnect_whenConnectionIsNotAllowed() {
        var work: (() -> Unit)? = null
        val c =
            controller(
                scheduler = { _, scheduledWork -> work = scheduledWork },
                runIfConnectionAllowed = {},
            )

        c.handleMessage("shutdown", "{\"reconnectInMs\":100}")
        work?.invoke()

        verify { client.disconnect() }
        verify(exactly = 0) { client.connect() }
    }

    // 메시지 디스패치

    @Test
    fun connectedMessage_setsModeToSse() {
        val c = controller()
        c.handleMessage("connected", "{}")
        assertEquals(SSEController.Mode.SSE, c.mode)
    }

    @Test
    fun syncMessage_triggersOnSyncRequested() {
        var called = 0
        val c =
            controller(onSyncRequested = { completion ->
                called++
                completion()
            })
        c.handleMessage("sync", "{}")
        assertEquals(1, called)
    }

    @Test
    fun serverEventMessage_invokesEventCallbackWithNameAndParams() {
        var name: String? = null
        var params: JSONObject? = null
        val c =
            controller(onServerEventTriggered = { n, p ->
                name = n
                params = p
            })
        c.handleMessage("server-event", "{\"name\":\"order_completed\",\"eventParams\":{\"x\":1}}")
        assertEquals("order_completed", name)
        assertEquals(1, params?.optInt("x"))
    }

    @Test
    fun ttlExpiredMessage_triggersReconnect() {
        val c = controller()
        c.handleMessage("ttl-expired", "{}")
        verify { client.disconnect() }
        verify { client.connect() }
    }

    @Test
    fun shutdownMessage_disconnectsAndSchedulesReconnect() {
        var capturedDelay: Long? = null
        var capturedWork: (() -> Unit)? = null
        val c =
            controller(scheduler = { d, w ->
                capturedDelay = d
                capturedWork = w
            })
        c.handleMessage("shutdown", "{\"reconnectInMs\":1500}")
        verify { client.disconnect() }
        assertEquals(1500L, capturedDelay)
        capturedWork?.invoke()
        verify { client.connect() }
    }

    // sync 디바운스 / in-flight 가드

    @Test
    fun syncMessage_doesNotDispatch_whileInFlight() {
        var called = 0
        val c =
            controller(
                onSyncRequested = { _ ->
                    called++
                },
            )
        c.handleMessage("sync", "{}")
        c.handleMessage("sync", "{}")
        c.handleMessage("sync", "{}")
        assertEquals(1, called)
    }

    @Test
    fun syncMessage_doesNotDispatch_whilePendingDebounce() {
        var called = 0
        val c =
            controller(
                onSyncRequested = { completion ->
                    called++
                    completion()
                },
                scheduler = { _, _ -> },
            )
        c.handleMessage("sync", "{}")
        c.handleMessage("sync", "{}")
        assertEquals(1, called)
    }

    @Test
    fun syncMessage_redispatchedAfterDebounceEnds() {
        var called = 0
        var pendingWork: (() -> Unit)? = null
        val c =
            controller(
                onSyncRequested = { completion ->
                    called++
                    completion()
                },
                scheduler = { _, w -> pendingWork = w },
            )
        c.handleMessage("sync", "{}")
        pendingWork?.invoke() // debounce 해제
        c.handleMessage("sync", "{}")
        assertEquals(2, called)
    }

    // fallback 전이

    @Test
    fun reconnecting_attempt3_withoutEverReachingOpen_entersFallback() {
        val c = controller()
        c.handleStateChange(SSEClient.State.Reconnecting(attempt = 3))
        assertEquals(SSEController.Mode.FALLBACK, c.mode)
        verify { client.disconnect() }
    }

    @Test
    fun reconnecting_attempt2_doesNotEnterFallback() {
        val c = controller()
        c.handleStateChange(SSEClient.State.Reconnecting(attempt = 2))
        assertEquals(SSEController.Mode.SSE, c.mode)
    }

    @Test
    fun reconnecting_afterReachedOpen_doesNotEnterFallback() {
        val c = controller()
        c.handleStateChange(SSEClient.State.Open)
        c.handleStateChange(SSEClient.State.Reconnecting(attempt = 5))
        assertEquals(SSEController.Mode.SSE, c.mode)
    }

    @Test
    fun open_setsModeToSseAndHasReachedOpen() {
        val c = controller()
        c.handleStateChange(SSEClient.State.Open)
        assertEquals(SSEController.Mode.SSE, c.mode)
    }

    // shutdown 의 generation token 으로 stale reconnect 무효화

    @Test
    fun shutdown_scheduledReconnect_dropsIfStopCalledBefore() {
        var work: (() -> Unit)? = null
        val c = controller(scheduler = { _, w -> work = w })
        c.handleMessage("shutdown", "{\"reconnectInMs\":100}")
        c.stop() // generation 증가
        work?.invoke()
        // stop 직후 client.disconnect() 1회 + shutdown 1회 호출 가능 → connect 는 stale 이라 호출되면 안 됨.
        verify(exactly = 0) { client.connect() }
    }
}

private object ImmediateScheduler {
    fun invoke(
        @Suppress("UNUSED_PARAMETER") delay: Long,
        work: () -> Unit,
    ) {
        work()
    }
}
