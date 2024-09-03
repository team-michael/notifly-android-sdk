package tech.notifly.http

import org.json.JSONObject

interface IHttpClient {
    suspend fun post(
        url: String,
        body: JSONObject,
        headers: Map<String, String>?,
    ): HttpResponse

    suspend fun get(
        url: String,
        headers: Map<String, String>?,
    ): HttpResponse

    // Add more methods here if needed
}
