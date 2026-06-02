package tech.notifly.sse

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream

class SSEByteLineSplitterTest {
    private fun split(bytes: ByteArray): List<String> {
        val out = mutableListOf<String>()
        SSEByteLineSplitter.split(ByteArrayInputStream(bytes)) { out.add(it) }
        return out
    }

    private fun split(s: String): List<String> = split(s.toByteArray(Charsets.UTF_8))

    // 기본 terminator

    @Test
    fun lfOnly_yieldsSingleEmptyLine() {
        assertEquals(listOf(""), split("\n"))
    }

    @Test
    fun crOnly_yieldsSingleEmptyLine() {
        assertEquals(listOf(""), split("\r"))
    }

    @Test
    fun crlf_yieldsSingleEmptyLine() {
        assertEquals(listOf(""), split("\r\n"))
    }

    @Test
    fun singleLine_lf() {
        assertEquals(listOf("hello"), split("hello\n"))
    }

    @Test
    fun singleLine_cr() {
        assertEquals(listOf("hello"), split("hello\r"))
    }

    @Test
    fun singleLine_crlf() {
        assertEquals(listOf("hello"), split("hello\r\n"))
    }

    // EOF discard (WHATWG: pending data must be discarded)

    @Test
    fun unterminatedTail_isDiscarded() {
        assertEquals(emptyList<String>(), split("hello"))
    }

    @Test
    fun terminatedThenUnterminated_yieldsOnlyTerminated() {
        assertEquals(listOf("a"), split("a\nbcd"))
    }

    // mixed line endings

    @Test
    fun mixedLineEndings_inSingleStream() {
        assertEquals(listOf("a", "b", "c", "d"), split("a\nb\r\nc\rd\n"))
    }

    // 연속 blank line — SSE event terminator 핵심

    @Test
    fun consecutiveLF_yieldsMultipleBlankLines() {
        assertEquals(listOf("", "", ""), split("\n\n\n"))
    }

    @Test
    fun consecutiveCRLF_yieldsMultipleBlankLines() {
        assertEquals(listOf("", ""), split("\r\n\r\n"))
    }

    @Test
    fun blankLineBetweenContent() {
        assertEquals(listOf("a", "", "b"), split("a\n\nb\n"))
    }

    @Test
    fun sseEventBlock_endsWithBlankLine() {
        assertEquals(listOf("event: sync", "data: {}", ""), split("event: sync\ndata: {}\n\n"))
    }

    // CR 직후 비-LF byte

    @Test
    fun crFollowedByText_treatsCRAsTerminator() {
        assertEquals(listOf("a", "b"), split("a\rb\n"))
    }

    @Test
    fun crFollowedByCR_eachIsTerminator() {
        assertEquals(listOf("a", "", "b"), split("a\r\rb\n"))
    }

    @Test
    fun lfFollowedByCR_eachIsTerminator() {
        assertEquals(listOf("a", "", "b"), split("a\n\rb\n"))
    }

    // unicode 보존

    @Test
    fun unicodeContent_preserved() {
        assertEquals(listOf("안녕하세요"), split("안녕하세요\n"))
    }

    @Test
    fun emojiContent_preserved() {
        assertEquals(listOf("hi 🚀"), split("hi 🚀\n"))
    }

    @Test
    fun diacritics_preserved() {
        assertEquals(listOf("café"), split("café\n"))
    }

    // 통합 SSE 시나리오

    @Test
    fun realisticSSEEventStream() {
        val stream =
            "event: connected\n" +
                "data: {\"projectId\":\"abc\"}\n" +
                "\n" +
                "id: 42\n" +
                "event: sync\n" +
                "data: {}\n" +
                "\n" +
                ": heartbeat\n" +
                "\n"
        val expected =
            listOf(
                "event: connected",
                "data: {\"projectId\":\"abc\"}",
                "",
                "id: 42",
                "event: sync",
                "data: {}",
                "",
                ": heartbeat",
                "",
            )
        assertEquals(expected, split(stream))
    }
}
