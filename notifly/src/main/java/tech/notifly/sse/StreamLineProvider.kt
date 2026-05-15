package tech.notifly.sse

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import tech.notifly.utils.Logger
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

internal data class StreamResponse(
    val statusCode: Int,
    val contentType: String?,
    val lines: Flow<String>,
    val close: () -> Unit,
)

internal fun interface StreamLineProvider {
    suspend fun open(
        url: String,
        headers: Map<String, String>,
    ): StreamResponse
}

internal class DefaultStreamLineProvider : StreamLineProvider {
    override suspend fun open(
        url: String,
        headers: Map<String, String>,
    ): StreamResponse =
        runInterruptible(Dispatchers.IO) {
            val connection = (URL(url).openConnection() as HttpURLConnection)
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = 0
                connection.useCaches = false
                connection.doInput = true
                for ((k, v) in headers) connection.setRequestProperty(k, v)

                val status = connection.responseCode
                val contentType = connection.contentType

                val lines =
                    callbackFlow<String> {
                        val job =
                            launch(Dispatchers.IO) {
                                val stream =
                                    try {
                                        connection.inputStream
                                    } catch (e: IOException) {
                                        close(e)
                                        return@launch
                                    }
                                try {
                                    SSEByteLineSplitter.split(stream) { line ->
                                        val result = trySend(line)
                                        if (result.isFailure) {
                                            throw IOException("downstream closed")
                                        }
                                    }
                                    close()
                                } catch (e: Throwable) {
                                    close(e)
                                } finally {
                                    runCatching { stream.close() }
                                }
                            }
                        awaitClose {
                            runCatching { connection.disconnect() }
                            job.cancel()
                        }
                    }.flowOn(Dispatchers.IO)

                StreamResponse(
                    statusCode = status,
                    contentType = contentType,
                    lines = lines,
                    close = { runCatching { connection.disconnect() } },
                )
            } catch (e: Throwable) {
                runCatching { connection.disconnect() }
                Logger.e("[sse] DefaultStreamLineProvider.open failed", e)
                throw e
            }
        }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 15_000
    }
}
