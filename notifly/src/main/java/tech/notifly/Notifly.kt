package tech.notifly

import android.content.Context
import android.util.Log


object Notifly {
    const val TAG = "Notifly"
    const val PLATFORM = "Android"
    fun initialize(
        context: Context,
        projectId: String,
        username: String,
        password: String,
        useCustomClickHandler: Boolean = false,
    ) {
        try {
            NotiflySharedPreferences.put(context, "notiflyProjectId", projectId)
            NotiflySharedPreferences.put(context, "notiflyUsername", username)
            NotiflySharedPreferences.put(context, "notiflyPassword", password)

            // todo: handle in-app messaging
            // todo: handle notification open
            // todo: handle session start
            // todo: use useCustomClickHandler
        } catch (e: Exception) {
            Log.e(TAG, "Notifly initialization failed:", e)
        }
    }
}
