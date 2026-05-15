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

/**
 * SSE 요청 응답 + raw line stream 묶음. SSEClient 가 status / content-type 가드 후 line 을 소비한다.
 */
internal data class StreamResponse(
    val statusCode: Int,
    val contentType: String?,
    val lines: Flow<String>,
)

/**
 * SSE 연결을 열고 byte stream 을 line 단위로 디코딩해서 반환. 테스트는 결정적 mock 으로 교체 가능.
 */
internal fun interface StreamLineProvider {
    suspend fun open(
        url: String,
        headers: Map<String, String>,
    ): StreamResponse
}

/**
 * HttpURLConnection 기반 default 구현.
 * - 연결을 열어 응답 헤더만 받은 다음 status/contentType 와 함께 lines Flow 를 반환.
 * - lines collect 중 caller 가 cancel 하면 connection.disconnect() 가 호출되어 read() 가 IOException 으로 종료.
 */
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
                // SSE 는 long-lived 라 readTimeout 0 (무한) 으로. heartbeat watchdog 가 application-level timeout 책임.
                connection.readTimeout = 0
                connection.useCaches = false
                connection.doInput = true
                for ((k, v) in headers) connection.setRequestProperty(k, v)

                // getResponseCode() 호출 시점에 실제 HTTP 요청 송신.
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
                                            // downstream 이 닫히면 read 도 중단.
                                            throw IOException("downstream closed")
                                        }
                                    }
                                    close()
                                } catch (e: Throwable) {
                                    // disconnect() 이후 발생하는 IOException 은 정상 종료 신호로 close().
                                    close(e)
                                } finally {
                                    runCatching { stream.close() }
                                }
                            }
                        awaitClose {
                            // cancel/완료 시점에 connection 끊어주면 read() 가 IOException 으로 빠져나옴.
                            runCatching { connection.disconnect() }
                            job.cancel()
                        }
                    }.flowOn(Dispatchers.IO)

                StreamResponse(status, contentType, lines)
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
