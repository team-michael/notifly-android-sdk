package tech.notifly.http.impl

import android.net.TrafficStats
import android.os.Build
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import tech.notifly.http.HttpClientOptions
import tech.notifly.http.HttpResponse
import tech.notifly.http.IHttpClient
import tech.notifly.utils.Logger
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.UnknownHostException
import java.util.Scanner

internal class HttpClient(
    private val _connectionFactory: IHttpConnectionFactory,
    private val _options: HttpClientOptions,
) : IHttpClient {
    override suspend fun post(
        url: String,
        body: JSONObject,
        headers: Map<String, String>?,
    ): HttpResponse {
        return makeRequest(url, "POST", body, headers, _options.httpTimeout)
    }

    override suspend fun get(url: String, headers: Map<String, String>?): HttpResponse {
        return makeRequest(url, "GET", null, headers, _options.httpGetTimeout)
    }

    private suspend fun makeRequest(
        url: String,
        method: String,
        jsonBody: JSONObject?,
        headers: Map<String, String>?,
        timeout: Int,
    ): HttpResponse {
        try {
            return withTimeout(getThreadTimeout(timeout).toLong()) {
                return@withTimeout makeRequestIODispatcher(url, method, jsonBody, headers, timeout)
            }
        } catch (e: TimeoutCancellationException) {
            Logger.e("HttpClient: Request timed out: $url", e)
            return HttpResponse(0, null, e)
        } catch (e: Throwable) {
            return HttpResponse(0, null, e)
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private suspend fun makeRequestIODispatcher(
        url: String,
        method: String,
        jsonBody: JSONObject?,
        headers: Map<String, String>?,
        timeout: Int,
    ): HttpResponse {
        var retVal: HttpResponse? = null

        val job = GlobalScope.launch(Dispatchers.IO) {
            var httpResponse = -1
            var con: HttpURLConnection? = null

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                TrafficStats.setThreadStatsTag(THREAD_ID)
            }

            try {
                con = _connectionFactory.newHttpURLConnection(url)

                con.useCaches = false
                con.connectTimeout = timeout
                con.readTimeout = timeout

                if (jsonBody != null) {
                    con.doInput = true
                }

                if (method != "GET") {
                    con.setRequestProperty("Content-Type", "application/json;charset=UTF-8")
                    con.requestMethod = method
                    con.doOutput = true
                }

                if (headers != null) {
                    for ((key, value) in headers) {
                        con.setRequestProperty(key, value)
                    }
                }

                if (jsonBody != null) {
                    val strJsonBody = jsonBody.toString()
                    Logger.d("HttpClient: $url/$method - $strJsonBody")

                    val sendBytes = strJsonBody.toByteArray(charset("UTF-8"))
                    con.setFixedLengthStreamingMode(sendBytes.size)
                    val outputStream = con.outputStream
                    outputStream.write(sendBytes)
                } else {
                    Logger.d("HttpClient: $url/$method")
                }

                // Network request is made from getResponseCode()
                httpResponse = con.responseCode

                when (httpResponse) {
                    HttpURLConnection.HTTP_ACCEPTED, HttpURLConnection.HTTP_CREATED, HttpURLConnection.HTTP_OK -> {
                        val inputStream = con.inputStream
                        val scanner = Scanner(inputStream, "UTF-8")
                        val json = if (scanner.useDelimiter("\\A").hasNext()) scanner.next() else ""
                        scanner.close()

                        Logger.d("HttpClient: $url/$method - Status:$httpResponse, Response: $json")

                        retVal = HttpResponse(httpResponse, json)
                    }

                    else -> {
                        Logger.e("HttpClient: $url/$method - Failed Status:$httpResponse")

                        var inputStream = con.errorStream
                        if (inputStream == null) {
                            inputStream = con.inputStream
                        }

                        var jsonResponse: String? = null
                        if (inputStream != null) {
                            val scanner = Scanner(inputStream, "UTF-8")
                            jsonResponse =
                                if (scanner.useDelimiter("\\A").hasNext()) scanner.next() else ""
                            scanner.close()
                            Logger.d("HttpClient: $url/$method - Status:$httpResponse, Response: $jsonResponse")
                        } else {
                            Logger.d("HttpClient: $url/$method - Status:$httpResponse, No Response Body")
                        }

                        retVal = HttpResponse(httpResponse, jsonResponse)
                    }
                }
            } catch (e: Throwable) {
                if (e is ConnectException || e is UnknownHostException) {
                    Logger.i("HttpClient: Could not send last request, device is offline. Throwable: ${e.javaClass.name}")
                } else {
                    Logger.d("HttpClient: $method Error thrown from network stack. ", e)
                }

                retVal = HttpResponse(httpResponse, null, e)
            } finally {
                con?.disconnect()
            }
        }

        job.join()
        return retVal!!
    }

    private fun getThreadTimeout(timeout: Int): Int {
        return timeout + 5000
    }

    companion object {
        private const val THREAD_ID = 10000
    }
}
