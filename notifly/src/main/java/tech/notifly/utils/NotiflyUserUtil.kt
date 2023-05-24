package tech.notifly.utils

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import tech.notifly.Logger
import tech.notifly.NotificationAuthorizationStatus
import tech.notifly.storage.NotiflyStorage
import tech.notifly.storage.NotiflyStorageItem

object NotiflyUserUtil {

    suspend fun setUserProperties(context: Context, params: Map<String, Any?>) {
        try {
            val newParams = params.toMutableMap()
            if (params.containsKey(N.KEY_EXTERNAL_USER_ID)) {
                val previousNotiflyUserId = NotiflyAuthUtil.getNotiflyUserId(context)
                val previousExternalUserId =
                    NotiflyStorage.get(context, NotiflyStorageItem.EXTERNAL_USER_ID)
                if (previousExternalUserId == null) {
                    Logger.i("[Notifly] <External User ID> not found.")
                }

                NotiflyStorage.put(
                    context,
                    NotiflyStorageItem.EXTERNAL_USER_ID,
                    params[N.KEY_EXTERNAL_USER_ID] as String?
                )
                NotiflyStorage.clear(context, NotiflyStorageItem.USER_ID)

                newParams += mapOf<String, Any?>(
                    N.KEY_PREVIOUS_NOTIFLY_USER_ID to previousNotiflyUserId,
                    N.KEY_PREVIOUS_EXTERNAL_USER_ID to previousExternalUserId,
                )
            }
            NotiflyLogUtil.logEvent(context, "set_user_properties", newParams, listOf(), true)
        } catch (e: Exception) {
            Logger.w("[Notifly] Failed to set user properties", e)
        }
    }

    fun removeUserId(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            NotiflyStorage.clear(context, NotiflyStorageItem.EXTERNAL_USER_ID)
            NotiflyStorage.clear(context, NotiflyStorageItem.USER_ID)

            NotiflyLogUtil.logEvent(context, "remove_external_user_id", emptyMap(), listOf(), true)
        }
    }

    fun sessionStart(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            val platform = NotiflyDeviceUtil.getPlatform()
            val apiLevel = NotiflyDeviceUtil.getApiLevel()
            val deviceBrand = NotiflyDeviceUtil.getBrand()
            val deviceModel = NotiflyDeviceUtil.getModel()
            val userAgent = System.getProperty("http.agent")
            val notifAuthStatus = getNotifAuthStatus(context).value

            val openAppEventParams = mapOf(
                "platform" to platform,
                "device_model" to deviceModel,
                "properties" to mapOf(
                    "device_brand" to deviceBrand,
                    "api_level" to apiLevel,
                    "user_agent" to userAgent
                ),
                "notif_auth_status" to notifAuthStatus
            )

            NotiflyLogUtil.logEvent(
                context,
                "session_start",
                openAppEventParams,
                listOf(),
                true,
            )
        }
    }

    private fun getNotifAuthStatus(context: Context): NotificationAuthorizationStatus {
        return if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            NotificationAuthorizationStatus.AUTHORIZED
        } else {
            NotificationAuthorizationStatus.DENIED
        }
    }
}
