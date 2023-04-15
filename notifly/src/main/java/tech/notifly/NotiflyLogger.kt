package tech.notifly

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
import tech.notifly.UUIDv5.Namespace

object NotiflyLogger {

    private const val LOG_EVENT_URI =
        "https://12lnng07q2.execute-api.ap-northeast-2.amazonaws.com/prod/records"
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    @OptIn(DelicateCoroutinesApi::class)
    fun logEvent(
        context: Context,
        eventName: String,
        eventParams: Map<String, Any>,
        segmentationEventParamKeys: List<String> = listOf(),
        isInternalEvent: Boolean = false
    ) {
        if (eventName.isEmpty()) {
            println("[Notifly] eventName must be provided.")
            return
        }

        GlobalScope.launch {
            try {
                var notiflyCognitoIdToken: String? =
                    NotiflyStorage.get(context, "notiflyCognitoIdToken", null)
                val notiflyUserId: String = NotiflyUtils.getNotiflyUserId(context)
                val notiflyExternalUserId: String =
                    NotiflyStorage.get(context, "notiflyExternalUserId", "")
                val notiflyProjectId: String =
                    NotiflyStorage.get(context, "notiflyProjectId", "")
                val uniqueId: String = NotiflyUtils.getUniqueId(context)
                val osVersion: String = NotiflyUtils.getSystemVersion()
                val appVersion: String = NotiflyUtils.getAppVersion(context)
                val fcmToken: String? = NotiflyUtils.getFcmToken()

                val eventUUID = UUIDv5.generate(
                    Namespace.NAMESPACE_EVENT_ID,
                    "$notiflyUserId$eventName${System.currentTimeMillis()}"
                )
                val eventId = eventUUID.toString().replace("-", "")

                val notiflyDeviceUUID = UUIDv5.generate(Namespace.NAMESPACE_DEVICE_ID, uniqueId)
                val notiflyDeviceId = notiflyDeviceUUID.toString().replace("-", "")

                // Retrieve & Set notiflyCognitoIdToken if not found
                if (notiflyCognitoIdToken.isNullOrEmpty()) {
                    notiflyCognitoIdToken = invalidateCognitoIdToken(context)
                }

                // Validate Required Parameters
                if (notiflyCognitoIdToken.isNullOrEmpty() || notiflyUserId.isEmpty() || notiflyProjectId.isEmpty() ||
                    eventId.isEmpty() || uniqueId.isEmpty() || notiflyDeviceId.isEmpty() || fcmToken.isNullOrEmpty()
                ) {
                    val requiredParameters = listOf(
                        "Cognito ID Token" to notiflyCognitoIdToken,
                        "User ID" to notiflyUserId,
                        "Project ID" to notiflyProjectId,
                        "Event ID" to eventId,
                        "Unique ID" to uniqueId,
                        "Device ID" to notiflyDeviceId,
                        "FCM Token" to fcmToken,
                    )
                    requiredParameters.firstOrNull { it.second.isNullOrEmpty() }
                        ?.let { (missingParamKey, _) ->
                            throw IllegalArgumentException("[Notifly] Missing required parameter in logEvent: $missingParamKey")
                        }
                }

                val requestBody = createRequestBody(
                    notiflyUserId,
                    eventId,
                    eventName,
                    notiflyDeviceId,
                    uniqueId,
                    fcmToken!!, // handled above
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
                    .header("Authorization", notiflyCognitoIdToken!!)
                    .header("Content-Type", "application/json")
                    .post(requestBody)
                    .build()

                val response = NotiflyHttpClient.HTTP_CLIENT.newCall(request).execute()
                Log.d(Notifly.TAG, "response: $response")
                val resultJson = response.body?.let { JSONObject(it.toString()) } ?: JSONObject()
                Log.d(Notifly.TAG, "resultJson: $resultJson")

                if (resultJson.optString("message") == "The incoming token has expired") {
                    invalidateCognitoIdToken(context)
                    logEvent(
                        context,
                        eventName,
                        eventParams,
                        segmentationEventParamKeys,
                        isInternalEvent
                    )
                }
            } catch (e: Exception) {
                println("[Notifly] Failed logging the event. Please retry the initialization. $e")
            }
        }
    }

    private suspend fun invalidateCognitoIdToken(context: Context): String? {
        val username: String = NotiflyStorage.get(context, "notiflyUsername", "")
        val password: String = NotiflyStorage.get(context, "notiflyPassword", "")
        val newCognitoIdToken = NotiflyUtils.getCognitoIdToken(username, password)
        NotiflyStorage.put(context, "notiflyCognitoIdToken", newCognitoIdToken)
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
        externalUserId: String,
        eventParams: Map<String, Any>
    ): RequestBody {
        val data = JSONObject()
            .put("event_params", JSONObject(eventParams))
            .put("id", eventId)
            .put("name", eventName)
            .put("notifly_user_id", notiflyUserId)
            .put("time", System.currentTimeMillis())
            .put("notifly_device_id", notiflyDeviceId)
            .put("external_device_id", externalDeviceId)
            .put("device_token", deviceToken)
            .put("is_internal_event", isInternalEvent)
            .put("segmentation_event_param_keys", segmentationEventParamKeys)
            .put("project_id", prjId)
            .put("platform", NotiflyUtils.getPlatform())
            .put("os_version", osVersion)
            .put("app_version", appVersion)
            .put("external_user_id", externalUserId.ifEmpty { JSONObject.NULL })

        val record = JSONObject()
            .put("data", data)
            .put("partitionKey", notiflyUserId)

        val records = JSONArray()
            .put(record)

        val body = JSONObject().put("records", records)

        Log.d(Notifly.TAG, body.toString())
        return body.toString().toRequestBody(JSON_MEDIA_TYPE)
    }
}