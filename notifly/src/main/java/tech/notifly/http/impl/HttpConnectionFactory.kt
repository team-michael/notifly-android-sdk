package tech.notifly.http.impl

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

internal class HttpConnectionFactory : IHttpConnectionFactory {
    @Throws(IOException::class)
    override fun newHttpURLConnection(url: String): HttpURLConnection = URL(url).openConnection() as HttpURLConnection
}
