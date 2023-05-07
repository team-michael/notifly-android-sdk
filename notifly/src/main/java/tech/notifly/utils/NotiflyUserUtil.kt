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

    suspend fun setUserProperties(context: Context, params: Map<String, Any?>) {
        try {
            val newParams = params.toMutableMap()
            if (params.containsKey(N.KEY_EXTERNAL_USER_ID)) {
                val previousNotiflyUserId = NotiflyAuthUtil.getNotiflyUserId(context)
                val previousExternalUserId =
                    NotiflyStorage.get(context, NotiflyStorageItem.EXTERNAL_USER_ID)
                if (previousExternalUserId == null) {
                    Log.i(Notifly.TAG, "[Notifly] <External User ID> not found.")
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
            Log.w(Notifly.TAG, "[Notifly] Failed to set user properties", e)
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

            val openAppEventParams = mapOf(
                "platform" to platform,
                "device_model" to deviceModel,
                "properties" to mapOf(
                    "device_brand" to deviceBrand,
                    "api_level" to apiLevel,
                    "user_agent" to userAgent
                )
            )

            NotiflyLogUtil.logEvent(context, "session_start", openAppEventParams)
        }
    }
}