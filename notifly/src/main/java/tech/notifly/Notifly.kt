package tech.notifly

import android.content.Context
import android.util.Log
import tech.notifly.storage.NotiflyStorage
import tech.notifly.storage.NotiflyStorageItem


object Notifly {
    internal const val TAG = "Notifly"

    fun initialize(
        context: Context,
        projectId: String,
        username: String,
        password: String,
        useCustomClickHandler: Boolean = false,
    ) {
        try {
            NotiflyStorage.put(context, NotiflyStorageItem.PROJECT_ID, projectId)
            NotiflyStorage.put(context, NotiflyStorageItem.USERNAME, username)
            NotiflyStorage.put(context, NotiflyStorageItem.PASSWORD, password)

            // todo: handle in-app messaging
            // todo: handle notification open
            // todo: handle session start
            // todo: use useCustomClickHandler
        } catch (e: Exception) {
            Log.e(TAG, "Notifly initialization failed:", e)
        }
    }
}
