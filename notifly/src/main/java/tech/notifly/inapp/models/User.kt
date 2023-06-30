package tech.notifly.inapp.models

import org.json.JSONException
import org.json.JSONObject
import tech.notifly.Logger

data class EventIntermediateCounts(
    val dt: String, val name: String, val count: Int, val event_params: Map<String, Any?>
) {
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
                val eventParamsJSONObject = from.getJSONObject("event_params")
                val eventParams = mutableMapOf<String, Any?>()
                val keys = eventParamsJSONObject.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    eventParams[key] = eventParamsJSONObject.get(key).let {
                        if (it == JSONObject.NULL) null else it
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

data class UserData(
    val platform: String?,
    val osVersion: String?,
    val appVersion: String?,
    val sdkVersion: String?,
    val sdkType: String?,
    val updatedAt: String?, // Not used
    val userProperties: MutableMap<String, Any?>?
) {
    fun get(unit: SegmentConditionUnitType, field: String?): Any? {
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

            else -> null
        }
    }

    companion object {
        fun fromJSONObject(from: JSONObject): UserData? {
            try {
                val platform = if (from.has("platform")) from.getString("platform") else null
                val osVersion = if (from.has("os_version")) from.getString("os_version") else null
                val appVersion =
                    if (from.has("app_version")) from.getString("app_version") else null
                val sdkVersion =
                    if (from.has("sdk_version")) from.getString("sdk_version") else null
                val sdkType = if (from.has("sdk_type")) from.getString("sdk_type") else null
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

                return UserData(
                    platform, osVersion, appVersion, sdkVersion, sdkType, updatedAt, userProperties
                )
            } catch (e: JSONException) {
                Logger.d("Error parsing UserData: $e")
                return null
            }
        }
    }
}
