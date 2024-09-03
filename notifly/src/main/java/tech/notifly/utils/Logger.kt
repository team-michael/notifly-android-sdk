package tech.notifly.utils

import android.util.Log

object Logger {
    private const val TAG = "Notifly"
    var level: Int = Log.WARN

    fun v(
        msg: String,
        tr: Throwable? = null,
    ) {
        if (level <= Log.VERBOSE) Log.v(TAG, msg, tr)
    }

    fun d(
        msg: String,
        tr: Throwable? = null,
    ) {
        if (level <= Log.DEBUG) Log.d(TAG, msg, tr)
    }

    fun i(
        msg: String,
        tr: Throwable? = null,
    ) {
        if (level <= Log.INFO) Log.i(TAG, msg, tr)
    }

    fun w(
        msg: String,
        tr: Throwable? = null,
    ) {
        if (level <= Log.WARN) Log.w(TAG, msg, tr)
    }

    fun e(
        msg: String,
        tr: Throwable? = null,
    ) {
        if (level <= Log.ERROR) Log.e(TAG, msg, tr)
    }

    fun setLogLevel(level: Int) {
        Logger.level = level
    }
}
