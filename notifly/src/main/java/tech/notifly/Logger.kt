package tech.notifly

import android.util.Log
import okhttp3.logging.HttpLoggingInterceptor

object Logger {
    private const val TAG = "Notifly"
    var level: Int = Log.WARN
    var httpLogLevel: HttpLoggingInterceptor.Level = HttpLoggingInterceptor.Level.NONE

    fun v(msg: String, tr: Throwable? = null) {
        if (level <= Log.VERBOSE) Log.v(TAG, msg, tr)
    }

    fun d(msg: String, tr: Throwable? = null) {
        if (level <= Log.DEBUG) Log.d(TAG, msg, tr)
    }

    fun i(msg: String, tr: Throwable? = null) {
        if (level <= Log.INFO) Log.i(TAG, msg, tr)
    }

    fun w(msg: String, tr: Throwable? = null) {
        if (level <= Log.WARN) Log.w(TAG, msg, tr)
    }

    fun e(msg: String, tr: Throwable? = null) {
        if (level <= Log.ERROR) Log.e(TAG, msg, tr)
    }

    fun setLogLevel(level: Int) {
        this.level = level
        httpLogLevel = when(level){
            Log.VERBOSE, Log.DEBUG -> HttpLoggingInterceptor.Level.BODY
            Log.INFO -> HttpLoggingInterceptor.Level.HEADERS
            else -> HttpLoggingInterceptor.Level.NONE
        }
    }
}
