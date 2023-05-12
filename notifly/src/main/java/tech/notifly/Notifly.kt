package tech.notifly

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import tech.notifly.storage.NotiflyStorage
import tech.notifly.storage.NotiflyStorageItem
import tech.notifly.utils.N.KEY_EXTERNAL_USER_ID
import tech.notifly.utils.NotiflyLogUtil
import tech.notifly.utils.NotiflyUserUtil


object Notifly {
    internal const val TAG = "Notifly"
    internal const val VERSION = "0.0.4"
    internal val SDK_TYPE = NotiflySdkType.NATIVE
    internal const val NOTIFICATION_CHANNEL_ID = "NotiflyNotificationChannelId"

    fun setUserId(
        context: Context,
        userId: String,
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (userId.isNullOrEmpty()) {
                    NotiflyUserUtil.removeUserId(context)
                } else {
                    val params = mapOf(
                        KEY_EXTERNAL_USER_ID to userId
                    )
                    NotiflyUserUtil.setUserProperties(context, params)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Notifly setUserId failed", e)
            }
        }
    }

    fun initialize(
        context: Context,
        projectId: String,
        username: String,
        password: String,
    ) {
        try {
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


    fun setUserProperties(context: Context, params: Map<String, Any?>) {
        // delegate to NotiflyUserUtil
        CoroutineScope(Dispatchers.IO).launch {
            try {
                NotiflyUserUtil.setUserProperties(context, params)
            } catch (e: Exception) {
                Log.w(TAG, "Notifly setUserProperties failed", e)
            }
        }
    }

    fun trackEvent(
        context: Context,
        eventName: String,
        eventParams: Map<String, Any?>,
        segmentationEventParamKeys: List<String> = listOf(),
        isInternalEvent: Boolean = false
    ) {
        NotiflyLogUtil.logEvent(
            context,
            eventName,
            eventParams,
            segmentationEventParamKeys,
            isInternalEvent
        )
    }
}
