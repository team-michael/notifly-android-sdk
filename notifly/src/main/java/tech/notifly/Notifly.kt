package tech.notifly

import android.content.Context
import android.util.Log


object Notifly {
    const val TAG = "Notifly"

    fun initialize(
        context: Context,
        projectId: String,
        username: String,
        password: String,
        useCustomClickHandler: Boolean = false,
    ) {
        try {
            NotiflyStorage.put(context, "notiflyProjectId", projectId)
            NotiflyStorage.put(context, "notiflyUsername", username)
            NotiflyStorage.put(context, "notiflyPassword", password)

            // todo: handle in-app messaging
            // todo: handle notification open
            // todo: handle session start
            // todo: use useCustomClickHandler
        } catch (e: Exception) {
            Log.e(TAG, "Notifly initialization failed:", e)
        }
    }
}
