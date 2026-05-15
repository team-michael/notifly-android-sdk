package tech.notifly.sse

internal data class SSEEvent(
    val id: String?,
    val type: String,
    val data: String,
)
