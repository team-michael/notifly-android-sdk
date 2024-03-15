package tech.notifly.http

import java.net.HttpURLConnection

class HttpResponse(
    val statusCode: Int,
    val payload: String?,
    val throwable: Throwable? = null,
) {
    val isSuccess: Boolean
        get() = statusCode == HttpURLConnection.HTTP_OK || statusCode == HttpURLConnection.HTTP_ACCEPTED || statusCode == HttpURLConnection.HTTP_NOT_MODIFIED || statusCode == HttpURLConnection.HTTP_CREATED
}