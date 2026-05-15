package tech.notifly.sse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SSELineParserTest {
    // 기본 dispatch

    @Test
    fun singleEvent_withTypeAndData_dispatchOnEmptyLine() {
        val p = SSELineParser()
        assertNull(p.feed("event: sync"))
        assertNull(p.feed("data: {\"ts\":1}"))
        val event = p.feed("")
        assertEquals(SSEEvent(id = null, type = "sync", data = "{\"ts\":1}"), event)
    }

    @Test
    fun dataOnly_typeDefaultsToMessage() {
        val p = SSELineParser()
        assertNull(p.feed("data: hello"))
        assertEquals(SSEEvent(id = null, type = "message", data = "hello"), p.feed(""))
    }

    @Test
    fun emptyData_doesNotDispatch() {
        val p = SSELineParser()
        assertNull(p.feed("event: sync"))
        assertNull(p.feed(""))
    }

    @Test
    fun lineWithNoColon_treatedAsFieldNameWithEmptyValue() {
        val p = SSELineParser()
        assertNull(p.feed("data"))
        assertEquals(SSEEvent(id = null, type = "message", data = ""), p.feed(""))
    }

    // data 멀티라인

    @Test
    fun multipleDataLines_joinedWithNewline() {
        val p = SSELineParser()
        assertNull(p.feed("data: line1"))
        assertNull(p.feed("data: line2"))
        assertNull(p.feed("data:line3"))
        assertEquals(SSEEvent(id = null, type = "message", data = "line1\nline2\nline3"), p.feed(""))
    }

    // comment / heartbeat

    @Test
    fun commentLine_ignoredAndDoesNotDispatch() {
        val p = SSELineParser()
        assertNull(p.feed(": heartbeat"))
        assertNull(p.feed(":"))
        assertNull(p.feed("data: hi"))
        assertEquals(SSEEvent(id = null, type = "message", data = "hi"), p.feed(""))
    }

    // id 처리

    @Test
    fun idField_setsLastEventIdAndIsAttachedToDispatchedEvent() {
        val p = SSELineParser()
        assertNull(p.feed("id: 42"))
        assertNull(p.feed("data: x"))
        val event = p.feed("")
        assertEquals("42", event?.id)
        assertEquals("42", p.lastEventId)
    }

    @Test
    fun idBufferPersists_acrossEventsWhenNotResent() {
        val p = SSELineParser()
        p.feed("id: 7")
        p.feed("data: a")
        val first = p.feed("")
        assertEquals("7", first?.id)

        p.feed("data: b")
        val second = p.feed("")
        assertEquals("7", second?.id)
        assertEquals("7", p.lastEventId)
    }

    @Test
    fun idEmptyValue_resetsBufferToEmptyString() {
        val p = SSELineParser()
        p.feed("id: 5")
        p.feed("data: a")
        p.feed("")
        assertEquals("5", p.lastEventId)

        p.feed("id:")
        p.feed("data: b")
        val event = p.feed("")
        assertEquals("", event?.id)
        assertEquals("", p.lastEventId)
    }

    @Test
    fun idWithNullByte_isIgnored() {
        val p = SSELineParser()
        p.feed("id: ok")
        p.feed("data: a")
        p.feed("")
        assertEquals("ok", p.lastEventId)

        // U+0000 포함 id 는 무시 (spec).
        p.feed("id: bad${Char(0x0000)}value")
        p.feed("data: b")
        val event = p.feed("")
        assertEquals("ok", event?.id)
        assertEquals("ok", p.lastEventId)
    }

    // retry / unknown field

    @Test
    fun retryField_isIgnored() {
        val p = SSELineParser()
        assertNull(p.feed("retry: 5000"))
        assertNull(p.feed("data: x"))
        assertEquals(SSEEvent(id = null, type = "message", data = "x"), p.feed(""))
    }

    @Test
    fun unknownField_isIgnored() {
        val p = SSELineParser()
        assertNull(p.feed("custom: whatever"))
        assertNull(p.feed("data: x"))
        assertEquals(SSEEvent(id = null, type = "message", data = "x"), p.feed(""))
    }

    // line ending / BOM

    @Test
    fun trailingCarriageReturn_isStripped() {
        val p = SSELineParser()
        assertNull(p.feed("event: sync\r"))
        assertNull(p.feed("data: payload\r"))
        assertEquals(SSEEvent(id = null, type = "sync", data = "payload"), p.feed("\r"))
    }

    @Test
    fun bomAtStart_isStripped() {
        val p = SSELineParser()
        val bom = Char(0xFEFF).toString()
        assertNull(p.feed("${bom}event: sync"))
        assertNull(p.feed("data: x"))
        assertEquals(SSEEvent(id = null, type = "sync", data = "x"), p.feed(""))
    }

    @Test
    fun bom_onlyStrippedFromFirstLine_preservedInDataValueAfter() {
        val p = SSELineParser()
        val bom = Char(0xFEFF).toString()
        assertNull(p.feed("${bom}event: msg"))
        assertNull(p.feed("data: ${bom}value"))
        assertEquals("${bom}value", p.feed("")?.data)
    }

    // 콜론 뒤 공백 처리

    @Test
    fun singleLeadingSpace_isStripped() {
        val p = SSELineParser()
        p.feed("data: x")
        assertEquals("x", p.feed("")?.data)
    }

    @Test
    fun multipleLeadingSpaces_onlyOneStripped() {
        val p = SSELineParser()
        p.feed("data:  x")
        assertEquals(" x", p.feed("")?.data)
    }

    @Test
    fun noLeadingSpace_valuePreserved() {
        val p = SSELineParser()
        p.feed("data:x")
        assertEquals("x", p.feed("")?.data)
    }

    // 통합 시나리오

    @Test
    fun typicalServerSentSequence() {
        val p = SSELineParser()
        p.feed(": stream opened")
        p.feed("event: connected")
        p.feed("data: {\"projectId\":\"abc\"}")
        val connected = p.feed("")
        assertEquals("connected", connected?.type)

        p.feed("id: 42")
        p.feed("event: sync")
        p.feed("data: {}")
        val sync = p.feed("")
        assertEquals(SSEEvent(id = "42", type = "sync", data = "{}"), sync)

        p.feed(": heartbeat")
        assertNull(p.feed(""))

        p.feed("id: 43")
        p.feed("event: server-event")
        p.feed("data: {\"name\":\"order_completed\",")
        p.feed("data:  \"eventParams\":{}}")
        val event = p.feed("")
        assertEquals("43", event?.id)
        assertEquals("server-event", event?.type)
        assertEquals("{\"name\":\"order_completed\",\n \"eventParams\":{}}", event?.data)
    }
}
