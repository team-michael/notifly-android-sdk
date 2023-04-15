package tech.notifly.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import tech.notifly.Notifly
import tech.notifly.storage.NotiflyStorage
import tech.notifly.storage.NotiflyStorageItem

object NotiflyUserUtil {

    suspend fun setUserProperties(context: Context, params: Map<String, String>) {
        try {
            if (params.containsKey(N.KEY_EXTERNAL_USER_ID)) {
                val previousNotiflyUserId = NotiflyAuthUtil.getNotiflyUserId(context)
                val previousExternalUserId = NotiflyStorage.get(context, NotiflyStorageItem.EXTERNAL_USER_ID)
                    ?: throw IllegalStateException("[Notifly] <External User ID> not found. You should call Notifly.initialize first")

                NotiflyStorage.put(context, NotiflyStorageItem.EXTERNAL_USER_ID, params[N.KEY_EXTERNAL_USER_ID])
                NotiflyStorage.clear(context, NotiflyStorageItem.USER_ID)

                val newParams = mapOf<String, Any>(
                    N.KEY_PREVIOUS_NOTIFLY_USER_ID to previousNotiflyUserId,
                    N.KEY_PREVIOUS_EXTERNAL_USER_ID to previousExternalUserId,
                )

                NotiflyLogUtil.logEvent(context, "set_user_properties", newParams, listOf(), true)
            }
        } catch(e: Exception) {
            Log.w(Notifly.TAG, "[Notifly] Failed to set user properties")
        }
    }

    fun removeUserId(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            NotiflyStorage.clear(context, NotiflyStorageItem.EXTERNAL_USER_ID)
            NotiflyStorage.clear(context, NotiflyStorageItem.USER_ID)

            NotiflyLogUtil.logEvent(context, "remove_external_user_id", emptyMap(), listOf(), true)
        }
    }
}