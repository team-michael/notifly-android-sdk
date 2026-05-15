package tech.notifly.sse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SSEMessageTest {
    @Test
    fun connected_decodesToConnected() {
        assertTrue(SSEMessage.decode("connected", "{}") is SSEMessage.Connected)
    }

    @Test
    fun sync_decodesToSync() {
        assertTrue(SSEMessage.decode("sync", "{}") is SSEMessage.Sync)
    }

    @Test
    fun ttlExpired_decodesToTtlExpired() {
        assertTrue(SSEMessage.decode("ttl-expired", "{}") is SSEMessage.TtlExpired)
    }

    @Test
    fun unknownType_decodesToUnknown() {
        val m = SSEMessage.decode("foo-bar", "{}")
        assertTrue(m is SSEMessage.Unknown)
        assertEquals("foo-bar", (m as SSEMessage.Unknown).rawType)
    }

    // server-event

    @Test
    fun serverEvent_withNameAndParams_decodesToEvent() {
        val m = SSEMessage.decode("server-event", "{\"name\":\"order_completed\",\"eventParams\":{\"x\":1}}")
        assertTrue(m is SSEMessage.Event)
        val e = m as SSEMessage.Event
        assertEquals("order_completed", e.name)
        assertNotNull(e.eventParams)
        assertEquals(1, e.eventParams?.optInt("x"))
    }

    @Test
    fun serverEvent_withNameButNoParams_decodesToEventWithNullParams() {
        val m = SSEMessage.decode("server-event", "{\"name\":\"order_completed\"}")
        assertTrue(m is SSEMessage.Event)
        val e = m as SSEMessage.Event
        assertEquals("order_completed", e.name)
        assertNull(e.eventParams)
    }

    @Test
    fun serverEvent_withoutName_decodesToMalformed() {
        val m = SSEMessage.decode("server-event", "{\"eventParams\":{}}")
        assertTrue(m is SSEMessage.Malformed)
    }

    @Test
    fun serverEvent_withInvalidJson_decodesToMalformed() {
        val m = SSEMessage.decode("server-event", "not json")
        assertTrue(m is SSEMessage.Malformed)
    }

    // shutdown

    @Test
    fun shutdown_withReconnectInMs_decodes() {
        val m = SSEMessage.decode("shutdown", "{\"reconnectInMs\":1500}")
        assertTrue(m is SSEMessage.Shutdown)
        assertEquals(1500, (m as SSEMessage.Shutdown).reconnectInMs)
    }

    @Test
    fun shutdown_withoutPayload_defaultsToZero() {
        val m = SSEMessage.decode("shutdown", "{}")
        assertTrue(m is SSEMessage.Shutdown)
        assertEquals(0, (m as SSEMessage.Shutdown).reconnectInMs)
    }

    @Test
    fun shutdown_withNegativeMs_clampsToZero() {
        val m = SSEMessage.decode("shutdown", "{\"reconnectInMs\":-1000}")
        assertTrue(m is SSEMessage.Shutdown)
        assertEquals(0, (m as SSEMessage.Shutdown).reconnectInMs)
    }

    @Test
    fun shutdown_withInvalidJson_defaultsToZero() {
        val m = SSEMessage.decode("shutdown", "not json")
        assertTrue(m is SSEMessage.Shutdown)
        assertEquals(0, (m as SSEMessage.Shutdown).reconnectInMs)
    }
}
