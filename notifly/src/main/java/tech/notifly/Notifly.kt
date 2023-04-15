package tech.notifly

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import tech.notifly.storage.NotiflyStorage
import tech.notifly.storage.NotiflyStorageItem
import tech.notifly.utils.N.KEY_EXTERNAL_USER_ID
import tech.notifly.utils.NotiflyUserUtil


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
            // todo: handle in-app messaging
            // todo: handle notification open
            // todo: use useCustomClickHandler

            // Set Required Properties from User
            NotiflyStorage.put(context, NotiflyStorageItem.PROJECT_ID, projectId)
            NotiflyStorage.put(context, NotiflyStorageItem.USERNAME, username)
            NotiflyStorage.put(context, NotiflyStorageItem.PASSWORD, password)

            // Start Session
            NotiflyUserUtil.sessionStart(context)
        } catch (e: Exception) {
            Log.e(TAG, "Notifly initialization failed:", e)
        }
    }

    fun setUserId(
        context: Context,
        userId: String,
    ) {
        try {
            CoroutineScope(Dispatchers.IO).launch {
                val params = mapOf(
                    KEY_EXTERNAL_USER_ID to userId,
                )
                NotiflyUserUtil.setUserProperties(context, params)
                NotiflyUserUtil.removeUserId(context)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Notifly setUserId failed", e)
        }
    }
}
