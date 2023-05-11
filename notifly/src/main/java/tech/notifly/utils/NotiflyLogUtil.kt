package tech.notifly.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import tech.notifly.Notifly
import tech.notifly.utils.NotiflyIdUtil.Namespace
import tech.notifly.storage.NotiflyStorage
import tech.notifly.storage.NotiflyStorageItem
import java.lang.IllegalStateException

object NotiflyLogUtil {

    private const val LOG_EVENT_URI = "https://12lnng07q2.execute-api.ap-northeast-2.amazonaws.com/prod/records"
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    @OptIn(DelicateCoroutinesApi::class)
    fun logEvent(
        context: Context,
        eventName: String,
        eventParams: Map<String, Any?>,
        segmentationEventParamKeys: List<String> = listOf(),
        isInternalEvent: Boolean = false,
        retryCount: Int = 0,
    ) {
        if (eventName.isEmpty()) {
            println("[Notifly] eventName must be provided.")
            return
        }

        /**
         * Required Parameters:
         * - Cognito ID Token: invalidate once when not found
         * - User ID: [NotiflyAuthUtil.getNotiflyUserId] ensures non-null
         * - Project ID: throw if null
         * - Event ID: [NotiflyIdUtil.generateUUIDv5] ensures non-null
         * - Unique ID: non-null is ensured by native-level API
         * - Device ID: non-null is ensured by native-level API
         * - FCM Token: nullable if sdk-caller did not integrate nor set FCM and its token.
         *
         * [IllegalStateException] will be thrown if <Project ID> or <FCM Token> is null.
         */
        GlobalScope.launch {
            try {
                val notiflyCognitoIdToken: String = NotiflyStorage.get(context, NotiflyStorageItem.COGNITO_ID_TOKEN)
                    ?: invalidateCognitoIdToken(context) // invalidate if not set
                val notiflyUserId: String = NotiflyAuthUtil.getNotiflyUserId(context)
                val notiflyExternalUserId: String? = NotiflyStorage.get(context, NotiflyStorageItem.EXTERNAL_USER_ID)
                val notiflyProjectId: String = NotiflyStorage.get(context, NotiflyStorageItem.PROJECT_ID)
                    ?: throw IllegalStateException("[Notifly] Required parameter <Project ID> is missing")

                val externalDeviceId: String = NotiflyDeviceUtil.getExternalDeviceId(context)
                val notiflyEventId =
                    NotiflyIdUtil.generate(Namespace.NAMESPACE_EVENT_ID, "$notiflyUserId$eventName${System.currentTimeMillis()}")
                val notiflyDeviceId = NotiflyIdUtil.generate(Namespace.NAMESPACE_DEVICE_ID, externalDeviceId)

                val osVersion: String = NotiflyDeviceUtil.getOsVersion()
                val appVersion: String = NotiflyDeviceUtil.getAppVersion(context)
                val fcmToken: String = NotiflyFirebaseUtil.getFcmToken()
                    ?: throw IllegalStateException("[Notifly] Required parameter <FCM Token> is missing")

                val requestBody = createRequestBody(
                    notiflyUserId,
                    notiflyEventId,
                    eventName,
                    notiflyDeviceId,
                    externalDeviceId,
                    fcmToken,
                    isInternalEvent,
                    segmentationEventParamKeys,
                    notiflyProjectId,
                    osVersion,
                    appVersion,
                    notiflyExternalUserId,
                    eventParams
                )

                val request = Request.Builder()
                    .url(LOG_EVENT_URI)
                    .header("Authorization", notiflyCognitoIdToken)
                    .header("Content-Type", "application/json")
                    .post(requestBody)
                    .build()

                val response = N.HTTP_CLIENT.newCall(request).execute()
                Log.d(Notifly.TAG, "response: $response")
                val resultJson = response.body?.let { JSONObject(it.string()) } ?: JSONObject()
                Log.d(Notifly.TAG, "resultJson: $resultJson")

                // invalidate and retry
                if (resultJson.optString("message") == "The incoming token has expired" && retryCount < 1) {
                    invalidateCognitoIdToken(context)
                    logEvent(
                        context,
                        eventName,
                        eventParams,
                        segmentationEventParamKeys,
                        isInternalEvent,
                        retryCount + 1
                    )
                }
            } catch (e: Exception) {
                println("[Notifly] Failed logging the event. Please retry the initialization. $e")
            }
        }
    }

    /**
     * Invalidates and save [NotiflyStorageItem.COGNITO_ID_TOKEN]
     *
     * @throws IllegalStateException if [NotiflyStorageItem.USERNAME] or [NotiflyStorageItem.PASSWORD] is null
     */
    private suspend fun invalidateCognitoIdToken(context: Context): String {
        val username: String = NotiflyStorage.get(context, NotiflyStorageItem.USERNAME)
            ?: throw IllegalStateException("[Notifly] username not found. You should call Notifly.initialize before this.")
        val password: String = NotiflyStorage.get(context, NotiflyStorageItem.PASSWORD)
            ?: throw IllegalStateException("[Notifly] password not found. You should call Notifly.initialize before this.")

        val newCognitoIdToken = NotiflyAuthUtil.getCognitoIdToken(username, password)
        NotiflyStorage.put(context, NotiflyStorageItem.COGNITO_ID_TOKEN, newCognitoIdToken)
        return newCognitoIdToken
    }

    private fun createRequestBody(
        notiflyUserId: String,
        eventId: String,
        eventName: String,
        notiflyDeviceId: String,
        externalDeviceId: String,
        deviceToken: String,
        isInternalEvent: Boolean,
        segmentationEventParamKeys: List<String>,
        prjId: String,
        osVersion: String,
        appVersion: String,
        externalUserId: String?,
        eventParams: Map<String, Any?>
    ): RequestBody {
        // Replace any null values in eventParams with JSONObject.NULL
        val sanitizedParams = eventParams.mapValues { if (it.value == null) JSONObject.NULL else it.value }

        val data = JSONObject()
            .put("event_params", JSONObject(sanitizedParams))
            .put("id", eventId)
            .put("name", eventName)
            .put("notifly_user_id", notiflyUserId)
            .put("time", System.currentTimeMillis() / 1000)
            .put("notifly_device_id", notiflyDeviceId)
            .put("external_device_id", externalDeviceId)
            .put("device_token", deviceToken)
            .put("is_internal_event", isInternalEvent)
            .put("segmentation_event_param_keys", segmentationEventParamKeys)
            .put("project_id", prjId)
            .put("platform", NotiflyDeviceUtil.getPlatform())
            .put("os_version", osVersion)
            .put("app_version", appVersion)
            .put("sdk_version", Notifly.VERSION)
            .put("sdk_type", Notifly.SDK_TYPE.toLowerCaseName())
            .put("external_user_id", if (externalUserId.isNullOrEmpty()) JSONObject.NULL else externalUserId)

        val record = JSONObject()
            .put("data", data.toString())
            .put("partitionKey", notiflyUserId)

        val records = JSONArray()
            .put(record)

        val body = JSONObject().put("records", records)

        Log.d(Notifly.TAG, body.toString())
        return body.toString().toRequestBody(JSON_MEDIA_TYPE)
    }
}