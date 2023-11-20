package tech.notifly.utils

import android.content.Context
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import tech.notifly.inapp.InAppMessageManager
import tech.notifly.storage.NotiflyStorage
import tech.notifly.storage.NotiflyStorageItem
import tech.notifly.utils.NotiflyIdUtil.Namespace

object NotiflyLogUtil {
    private const val LOG_EVENT_URI =
        "https://12lnng07q2.execute-api.ap-northeast-2.amazonaws.com/prod/records"
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private fun <K, V, R> deepMapValues(input: Map<K, V>, transform: (V) -> R): Map<K, R> {
        return input.mapValues { (_, value) ->
            when (value) {
                is Map<*, *> -> deepMapValues(value as Map<K, V>, transform)
                is Collection<*> -> value.map { deepMapValues(mapOf(0 to it as V), transform)[0] }
                else -> transform(value)
            }
        } as Map<K, R>
    }

    fun logEventSync(
        context: Context,
        eventName: String,
        eventParams: Map<String, Any?> = emptyMap(),
        segmentationEventParamKeys: List<String>? = null,
        isInternalEvent: Boolean = false,
    ) {
        GlobalScope.launch {
            try {
                logEvent(
                    context, eventName, eventParams, segmentationEventParamKeys, isInternalEvent
                )
            } catch (e: Exception) {
                Logger.e("[Notifly] Failed logging the event. $e")
            }
        }
    }

    suspend fun logEvent(
        context: Context,
        eventName: String,
        eventParams: Map<String, Any?> = emptyMap(),
        segmentationEventParamKeys: List<String>? = null,
        isInternalEvent: Boolean = false,
    ) {
        if (eventName.isEmpty()) {
            Logger.e("[Notifly] eventName must be provided.")
            return
        }

        val externalUserId = NotiflyStorage.get(context, NotiflyStorageItem.EXTERNAL_USER_ID)
        if (OSUtils.isAppInForeground(context)) {
            InAppMessageManager.maybeScheduleInWebMessagesAndIngestEvent(
                context,
                eventName,
                externalUserId,
                eventParams,
                isInternalEvent,
                segmentationEventParamKeys
            )
        } else {
            Logger.d("[Notifly] App is not in foreground. Not scheduling in app messages.")
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
         */
        logEventInternal(
            context,
            eventName,
            eventParams,
            segmentationEventParamKeys,
            isInternalEvent,
        )
    }

    private suspend fun logEventInternal(
        context: Context,
        eventName: String,
        eventParams: Map<String, Any?> = emptyMap(),
        segmentationEventParamKeys: List<String>? = null,
        isInternalEvent: Boolean = false,
        retryCount: Int = 0,
    ) {
        val notiflyCognitoIdToken: String =
            NotiflyStorage.get(context, NotiflyStorageItem.COGNITO_ID_TOKEN)
                ?: NotiflyAuthUtil.invalidateCognitoIdToken(context) // invalidate if not set
        val notiflyUserId: String = NotiflyAuthUtil.getNotiflyUserId(context)
        val notiflyExternalUserId: String? =
            NotiflyStorage.get(context, NotiflyStorageItem.EXTERNAL_USER_ID)
        val notiflyProjectId: String = NotiflyStorage.get(context, NotiflyStorageItem.PROJECT_ID)
            ?: throw IllegalStateException("[Notifly] Required parameter <Project ID> is missing")

        val externalDeviceId: String = NotiflyDeviceUtil.getExternalDeviceId(context)
        val notiflyEventId = NotiflyIdUtil.generate(
            Namespace.NAMESPACE_EVENT_ID, "$notiflyUserId$eventName${System.currentTimeMillis()}"
        )
        val notiflyDeviceId =
            NotiflyIdUtil.generate(Namespace.NAMESPACE_DEVICE_ID, externalDeviceId)

        val osVersion: String = NotiflyDeviceUtil.getOsVersion()
        val appVersion: String = NotiflyDeviceUtil.getAppVersion(context)
        val fcmToken: String? = NotiflyFirebaseUtil.getFcmToken()
        if (fcmToken == null) {
            Logger.w("[Notifly] Required parameter <FCM Token> is missing")
        }

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

        val request =
            Request.Builder().url(LOG_EVENT_URI).header("Authorization", notiflyCognitoIdToken)
                .header("Content-Type", "application/json").post(requestBody).build()

        val response = N.HTTP_CLIENT.newCall(request).execute()
        Logger.d("response: $response")
        val resultJson = response.body?.let { JSONObject(it.string()) } ?: JSONObject()
        Logger.d("resultJson: $resultJson")

        // invalidate and retry
        if (resultJson.optString("message") == "The incoming token has expired" && retryCount < 1) {
            NotiflyAuthUtil.invalidateCognitoIdToken(context)
            logEventInternal(
                context,
                eventName,
                eventParams,
                segmentationEventParamKeys,
                isInternalEvent,
                retryCount + 1
            )
        }
    }

    private fun createRequestBody(
        notiflyUserId: String,
        eventId: String,
        eventName: String,
        notiflyDeviceId: String,
        externalDeviceId: String,
        deviceToken: String?,
        isInternalEvent: Boolean,
        segmentationEventParamKeys: List<String>?,
        prjId: String,
        osVersion: String,
        appVersion: String,
        externalUserId: String?,
        eventParams: Map<String, Any?>,
    ): RequestBody {
        val sdkVersion = NotiflySDKInfoUtil.getSdkVersion()
        val sdkType = NotiflySDKInfoUtil.getSdkType()

        // Replace any null values in eventParams with JSONObject.NULL
        val sanitizedParams = deepMapValues(eventParams) { it ?: JSONObject.NULL }

        val data = JSONObject().put("event_params", JSONObject(sanitizedParams)).put("id", eventId)
            .put("name", eventName).put("notifly_user_id", notiflyUserId)
            .put("time", System.currentTimeMillis() / 1000)
            .put("notifly_device_id", notiflyDeviceId).put("external_device_id", externalDeviceId)
            .put(
                "device_token", if (deviceToken.isNullOrEmpty()) JSONObject.NULL else deviceToken
            ).put("is_internal_event", isInternalEvent).put(
                "segmentation_event_param_keys",
                if (segmentationEventParamKeys.isNullOrEmpty()) JSONObject.NULL else JSONArray(
                    segmentationEventParamKeys
                )
            ).put("project_id", prjId).put("platform", NotiflyDeviceUtil.getPlatform())
            .put("os_version", osVersion).put("app_version", appVersion)
            .put("sdk_version", sdkVersion).put("sdk_type", sdkType.toLowerCaseName()).put(
                "external_user_id",
                if (externalUserId.isNullOrEmpty()) JSONObject.NULL else externalUserId
            )

        val record = JSONObject().put("data", data.toString()).put("partitionKey", notiflyUserId)

        val records = JSONArray().put(record)

        val body = JSONObject().put("records", records)

        Logger.d(body.toString())
        return body.toString().toRequestBody(JSON_MEDIA_TYPE)
    }
}
