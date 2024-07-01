package tech.notifly.utils

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tech.notifly.inapp.InAppMessageManager
import tech.notifly.inapp.models.EventIntermediateCounts
import tech.notifly.push.NotificationAuthorizationStatus
import tech.notifly.storage.NotiflyStorage
import tech.notifly.storage.NotiflyStorageItem

object NotiflyUserUtil {

    suspend fun setUserProperties(context: Context, params: Map<String, Any?>) {
        try {
            val newParams = params.toMutableMap()
            if (params[N.KEY_EXTERNAL_USER_ID] is String) {
                val externalUserIdToSet = params[N.KEY_EXTERNAL_USER_ID] as String
                val previousNotiflyUserId = NotiflyAuthUtil.getNotiflyUserId(context)
                val previousExternalUserId =
                    NotiflyStorage.get(context, NotiflyStorageItem.EXTERNAL_USER_ID)
                if (previousExternalUserId == null) {
                    Logger.i("[Notifly] <External User ID> not found.")
                }

                NotiflyStorage.put(
                    context,
                    NotiflyStorageItem.EXTERNAL_USER_ID,
                    externalUserIdToSet,
                )
                NotiflyStorage.clear(context, NotiflyStorageItem.USER_ID)

                newParams += mapOf<String, Any?>(
                    N.KEY_PREVIOUS_NOTIFLY_USER_ID to previousNotiflyUserId,
                    N.KEY_PREVIOUS_EXTERNAL_USER_ID to previousExternalUserId,
                )
            } else {
                InAppMessageManager.updateUserProperties(newParams)
            }
            NotiflyLogUtil.logEvent(context, "set_user_properties", newParams, listOf(), true)
        } catch (e: Exception) {
            Logger.w("[Notifly] Failed to set user properties", e)
        }
    }

    suspend fun removeUserId(context: Context) {
        NotiflyStorage.clear(context, NotiflyStorageItem.EXTERNAL_USER_ID)
        NotiflyStorage.clear(context, NotiflyStorageItem.USER_ID)
        NotiflyLogUtil.logEvent(context, "remove_external_user_id", emptyMap(), listOf(), true)
    }

    suspend fun sessionStart(context: Context) {
        withContext(Dispatchers.IO) {
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
                "notif_auth_status" to notifAuthStatus,
                "in_app_message_disabled" to InAppMessageManager.disabled,
                "timezone" to NotiflyUtil.getCurrentTimezone()
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

    fun mergeEventCounts(
        first: MutableList<EventIntermediateCounts>, second: MutableList<EventIntermediateCounts>
    ): MutableList<EventIntermediateCounts> {
        val merged = first.toMutableList()
        for (secondEventCount in second) {
            val index = merged.indexOfFirst {
                secondEventCount.equalsTo(it)
            }
            if (index == -1) {
                merged.add(secondEventCount)
            } else {
                merged[index] = secondEventCount.merge(merged[index])
            }
        }

        Logger.d("[Notifly] Merged event counts: $merged")
        return merged
    }
}
