package tech.notifly.utils

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import tech.notifly.NotiflySdkType

/**
 * Static values used internally
 */
internal object N {

    /**
     * Regardless of [NotiflySdkType], Platform Information is fixed to "Android"
     */
    const val PLATFORM = "android"

    /**
     * Reserved Key for backward compatibility based on User Property or Event Logging.
     */
    const val KEY_EXTERNAL_USER_ID = "external_user_id"
    /**
     * Reserved Key for backward compatibility based on User Property or Event Logging.
     */
    const val KEY_PREVIOUS_NOTIFLY_USER_ID = "previous_notifly_user_id"
    /**
     * Reserved Key for backward compatibility based on User Property or Event Logging.
     */
    const val KEY_PREVIOUS_EXTERNAL_USER_ID = "previous_external_user_id"

    val HTTP_CLIENT = OkHttpClient().newBuilder()
        .followRedirects(true) // Ensure that redirects are followed
        .followSslRedirects(true) // Ensure that SSL redirects are followed
        .addNetworkInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY // TODO: Disable when publish
        })
        .build()
}