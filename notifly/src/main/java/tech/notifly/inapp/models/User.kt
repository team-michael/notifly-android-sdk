package tech.notifly.inapp.models

import android.content.Context
import org.json.JSONException
import org.json.JSONObject
import tech.notifly.storage.NotiflyStorage
import tech.notifly.storage.NotiflyStorageItem
import tech.notifly.utils.Logger
import tech.notifly.utils.NotiflyDeviceUtil
import tech.notifly.utils.NotiflySDKInfoUtil

data class EventIntermediateCounts(
    val dt: String, val name: String, val count: Int, val eventParams: Map<String, Any?>
) {
    fun equalsTo(other: EventIntermediateCounts): Boolean {
        return dt == other.dt && name == other.name && eventParams == other.eventParams
    }

    fun merge(other: EventIntermediateCounts): EventIntermediateCounts {
        if (!this.equalsTo(other)) {
            throw IllegalArgumentException("Cannot merge EventIntermediateCounts with different dt, name, and event_params")
        }
        return EventIntermediateCounts(
            dt, name, count + other.count, eventParams
        )
    }

    companion object {
        /**
         * Parses [JSONObject] and loads data to [EventIntermediateCounts] data class.
         * @return [Campaign] object if successfully parsed, `null` otherwise.
         */
        fun fromJSONObject(from: JSONObject): EventIntermediateCounts? {
            try {
                val name = from.getString("name")
                val dt = from.getString("dt")
                val count = from.getInt("count")
                val eventParamsJSONObject = from.optJSONObject("event_params")
                val eventParams = mutableMapOf<String, Any?>()
                if (eventParamsJSONObject != null) {
                    val keys = eventParamsJSONObject.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        eventParams[key] = eventParamsJSONObject.get(key).let {
                            if (it == JSONObject.NULL) null else it
                        }
                    }
                }
                return EventIntermediateCounts(dt, name, count, eventParams)
            } catch (e: JSONException) {
                Logger.d("EventIntermediateCounts parsing failed", e)
                return null
            }
        }
    }
}

data class EventLogData(
    val campaignId: String,
    val notiflyMessageId: String?,
    val campaignHiddenUntil: Int?,
)

data class UserData(
    val platform: String?,
    val osVersion: String?,
    val appVersion: String?,
    val sdkVersion: String?,
    val sdkType: String?,
    val randomBucketNumber: Int?,
    val updatedAt: String?, // Not used
    val userProperties: MutableMap<String, Any?>?,
    val campaignHiddenUntil: MutableMap<String, Int>,
) {
    fun get(context: Context, unit: SegmentConditionUnitType, field: String?): Any? {
        if (field == null) return null
        return when (unit) {
            SegmentConditionUnitType.USER -> userProperties?.get(field)
            SegmentConditionUnitType.DEVICE -> {
                when (field) {
                    "platform" -> platform
                    "os_version" -> osVersion
                    "app_version" -> appVersion
                    "sdk_version" -> sdkVersion
                    "sdk_type" -> sdkType
                    "updated_at" -> updatedAt
                    else -> null
                }
            }

            SegmentConditionUnitType.USER_METADATA -> {
                when (field) {
                    "external_user_id" -> NotiflyStorage.get(
                        context, NotiflyStorageItem.EXTERNAL_USER_ID
                    )

                    "random_bucket_number" -> randomBucketNumber
                    else -> null
                }
            }

            else -> null
        }
    }

    fun merge(other: UserData?): UserData {
        if (other == null) return this

        val result = UserData(platform = other.platform,
            osVersion = other.osVersion,
            appVersion = other.appVersion,
            sdkVersion = other.sdkVersion,
            sdkType = other.sdkType,
            randomBucketNumber = other.randomBucketNumber,
            updatedAt = other.updatedAt,
            userProperties = if (other.userProperties == null && userProperties == null) null else {
                val merged = mutableMapOf<String, Any?>()
                if (userProperties != null) merged.putAll(userProperties)
                if (other.userProperties != null) merged.putAll(other.userProperties)
                merged
            },
            campaignHiddenUntil = run {
                val merged = mutableMapOf<String, Int>()
                merged.putAll(campaignHiddenUntil)
                merged.putAll(other.campaignHiddenUntil)
                merged
            })
        Logger.d("Merged UserData: $result")
        return result
    }

    companion object {
        suspend fun fromJSONObject(context: Context, from: JSONObject): UserData? {
            try {
                val platform = NotiflyDeviceUtil.getPlatform()
                val osVersion = NotiflyDeviceUtil.getOsVersion()
                val appVersion = NotiflyDeviceUtil.getAppVersion(context)
                val sdkVersion = NotiflySDKInfoUtil.getSdkVersion()
                val sdkType = NotiflySDKInfoUtil.getSdkType().toLowerCaseName()

                // random_bucket_number can either be an int or a string
                val randomBucketNumber = if (from.has("random_bucket_number")) {
                    when (val randomBucketNumber = from.get("random_bucket_number")) {
                        is Int -> {
                            randomBucketNumber
                        }

                        is String -> {
                            randomBucketNumber.toIntOrNull()
                        }

                        else -> {
                            null
                        }
                    }
                } else null

                val updatedAt = if (from.has("updated_at")) from.getString("updated_at") else null

                val userPropertiesJSONObject =
                    if (from.has("user_properties")) from.getJSONObject("user_properties") else null
                val userProperties = if (userPropertiesJSONObject != null) {
                    val keys = userPropertiesJSONObject.keys()
                    val userProperties = mutableMapOf<String, Any?>()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        userProperties[key] = userPropertiesJSONObject.get(key).let {
                            if (it == JSONObject.NULL) null else it
                        }
                    }
                    userProperties
                } else null

                val campaignHiddenUntilJSONObject =
                    if (from.has("campaign_hidden_until")) from.getJSONObject("campaign_hidden_until") else null
                val campaignHiddenUntil = if (campaignHiddenUntilJSONObject != null) {
                    val keys = campaignHiddenUntilJSONObject.keys()
                    val result = mutableMapOf<String, Int>()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        result[key] = campaignHiddenUntilJSONObject.getInt(key)
                    }
                    result
                } else mutableMapOf()

                return UserData(
                    platform = platform,
                    osVersion = osVersion,
                    appVersion = appVersion,
                    sdkVersion = sdkVersion,
                    sdkType = sdkType,
                    randomBucketNumber = randomBucketNumber,
                    updatedAt = updatedAt,
                    userProperties = userProperties,
                    campaignHiddenUntil = campaignHiddenUntil
                )
            } catch (e: JSONException) {
                Logger.d("Error parsing UserData: $e")
                return null
            }
        }
    }
}
