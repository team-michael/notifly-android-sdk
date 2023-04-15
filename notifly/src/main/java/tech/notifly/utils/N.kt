package tech.notifly.utils

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

/**
 * Static values used internally
 */
internal object N {

    const val PLATFORM = "Android"

    val HTTP_CLIENT = OkHttpClient().newBuilder()
        .followRedirects(true) // Ensure that redirects are followed
        .followSslRedirects(true) // Ensure that SSL redirects are followed
        .addNetworkInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY // TODO: Disable when publish
        })
        .build()
}