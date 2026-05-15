package tech.notifly.sse

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/**
 * WHATWG SSE spec 의 line splitter.
 * - \n / \r\n / \r 각각이 line terminator. blank line 도 yield.
 * - EOF 도달 시 buffer 의 incomplete data 는 discard (spec).
 * - Charset 는 UTF-8 고정. invalid byte sequence 는 String(ByteArray, UTF_8) 의 기본 동작에 따라 replacement (U+FFFD) 로 치환.
 *
 * `split` 은 blocking. caller 가 IO dispatcher 안에서 호출해야 한다. cancellation 은 source 측에서
 * stream 을 close 하면 read() 가 IOException 으로 종료된다.
 */
internal object SSEByteLineSplitter {
    @Throws(IOException::class)
    fun split(
        source: InputStream,
        emit: (String) -> Unit,
    ) {
        val buffer = ByteArrayOutputStream(INITIAL_BUFFER_CAPACITY)
        var prevWasCR = false
        while (true) {
            val byte = source.read()
            if (byte < 0) return // EOF: pending data discard.
            when (byte) {
                LF -> {
                    if (prevWasCR) {
                        prevWasCR = false
                        continue
                    }
                    emit(buffer.toUtf8String())
                    buffer.reset()
                }
                CR -> {
                    emit(buffer.toUtf8String())
                    buffer.reset()
                    prevWasCR = true
                }
                else -> {
                    prevWasCR = false
                    buffer.write(byte)
                }
            }
        }
    }

    private fun ByteArrayOutputStream.toUtf8String(): String {
        if (size() == 0) return ""
        return String(this.toByteArray(), Charsets.UTF_8)
    }

    private const val LF = 0x0A
    private const val CR = 0x0D
    private const val INITIAL_BUFFER_CAPACITY = 256
}
