package tech.notifly

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import tech.notifly.storage.NotiflyStorage
import tech.notifly.storage.NotiflyStorageItem
import tech.notifly.utils.N.KEY_EXTERNAL_USER_ID
import tech.notifly.utils.NotiflyLogUtil
import tech.notifly.utils.NotiflyUserUtil


object Notifly {
    internal const val VERSION: String = BuildConfig.VERSION


    internal val SDK_TYPE = NotiflySdkType.NATIVE
    internal const val NOTIFICATION_CHANNEL_ID = "NotiflyNotificationChannelId"

    @Volatile
    private var isNotiflyInitialized = false

    @JvmStatic
    @JvmOverloads
    fun setUserId(
        context: Context,
        userId: String? = null,
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
                Logger.e("Notifly setUserId failed", e)
            }
        }
    }

    @JvmStatic
    fun initialize(
        context: Context,
        projectId: String,
        username: String,
        password: String,
    ) {
        if (isNotiflyInitialized) {
            Logger.w("Notifly is already initialized.")
            return
        }

        try {
            // Set Required Properties from User
            NotiflyStorage.put(context, NotiflyStorageItem.PROJECT_ID, projectId)
            NotiflyStorage.put(context, NotiflyStorageItem.USERNAME, username)
            NotiflyStorage.put(context, NotiflyStorageItem.PASSWORD, password)

            // Start Session
            NotiflyUserUtil.sessionStart(context)
        } catch (e: Exception) {
            Logger.e("Notifly initialization failed:", e)
        }
    }


    @JvmStatic
    fun setUserProperties(context: Context, params: Map<String, Any?>) {
        // delegate to NotiflyUserUtil
        CoroutineScope(Dispatchers.IO).launch {
            try {
                NotiflyUserUtil.setUserProperties(context, params)
            } catch (e: Exception) {
                Logger.w("Notifly setUserProperties failed", e)
            }
        }
    }

    @JvmStatic
    @JvmOverloads
    fun trackEvent(
        context: Context,
        eventName: String,
        eventParams: Map<String, Any?> = emptyMap(),
        segmentationEventParamKeys: List<String>? = null,
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

    @JvmStatic
    fun setLogLevel(level: Int) {
        Logger.setLogLevel(level)
    }
}
