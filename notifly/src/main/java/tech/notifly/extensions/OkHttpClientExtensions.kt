package tech.notifly.extensions

import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

internal suspend fun OkHttpClient.await(request: Request): okhttp3.Response = suspendCoroutine { continuation ->
    newCall(request).enqueue(object : okhttp3.Callback {
        override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
            continuation.resume(response)
        }

        override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
            continuation.resumeWithException(e)
        }
    })
}