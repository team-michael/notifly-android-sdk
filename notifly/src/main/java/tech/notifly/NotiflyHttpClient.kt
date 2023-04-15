package tech.notifly

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

object NotiflyHttpClient {
    val HTTP_CLIENT = OkHttpClient().newBuilder()
        .followRedirects(true) // Ensure that redirects are followed
        .followSslRedirects(true) // Ensure that SSL redirects are followed
        .addNetworkInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY // TODO: Disable when publish
        })
        .build()
}