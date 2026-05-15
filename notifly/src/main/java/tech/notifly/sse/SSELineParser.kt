package tech.notifly.sse

/**
 * WHATWG SSE spec 에 따라 라인 입력을 누적해 SSEEvent 로 dispatch.
 * - 빈 라인이 event terminator (dispatch).
 * - `:` 로 시작하는 라인은 comment.
 * - field 라인: `<field>: <value>` (콜론 뒤 단일 공백 제거).
 * - 첫 라인의 leading BOM(U+FEFF) 은 strip.
 */
internal class SSELineParser {
    private var eventType: String? = null
    private val dataLines = mutableListOf<String>()
    private var isFirstLine = true

    var lastEventId: String? = null
        private set

    fun feed(rawLine: String): SSEEvent? {
        val line = stripLineEnding(rawLine)
        if (line.isEmpty()) return dispatch()
        if (line.startsWith(":")) return null

        val (field, value) = splitFieldValue(line)
        when (field) {
            "id" -> if (!value.contains(NULL_CHAR)) lastEventId = value
            "event" -> eventType = value
            "data" -> dataLines.add(value)
            "retry" -> {} // 무시
            else -> {} // 무시
        }
        return null
    }

    private fun dispatch(): SSEEvent? {
        val type = eventType?.takeIf { it.isNotEmpty() } ?: DEFAULT_TYPE
        val data = if (dataLines.isEmpty()) null else dataLines.joinToString("\n")
        eventType = null
        dataLines.clear()
        return data?.let { SSEEvent(id = lastEventId, type = type, data = it) }
    }

    private fun stripLineEnding(s: String): String {
        var result = s
        if (result.endsWith('\r')) {
            result = result.substring(0, result.length - 1)
        }
        if (isFirstLine) {
            if (result.isNotEmpty() && result[0] == BOM_CHAR) {
                result = result.substring(1)
            }
            isFirstLine = false
        }
        return result
    }

    private fun splitFieldValue(line: String): Pair<String, String> {
        val colon = line.indexOf(':')
        if (colon < 0) return Pair(line, "")
        val field = line.substring(0, colon)
        var valueStart = colon + 1
        if (valueStart < line.length && line[valueStart] == ' ') valueStart += 1
        return Pair(field, line.substring(valueStart))
    }

    companion object {
        private const val DEFAULT_TYPE = "message"
        private val NULL_CHAR: Char = Char(0x0000)
        private val BOM_CHAR: Char = Char(0xFEFF)
    }
}
