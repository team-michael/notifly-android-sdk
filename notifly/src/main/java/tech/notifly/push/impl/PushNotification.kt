package tech.notifly.push.impl

import android.os.Bundle
import org.json.JSONException
import org.json.JSONObject
import tech.notifly.push.interfaces.IPushNotification
import tech.notifly.utils.Logger
import tech.notifly.utils.NotiflyTimerUtil
import java.security.SecureRandom

enum class Importance {
    HIGH,
    NORMAL,
    LOW,
}

data class PushNotification(
    /** The body text of the notification */
    override val body: String? = null,
    /** The title text of the notification */
    override val title: String? = null,
    /** The Notifly campaign ID of the notification */
    override val campaignId: String? = null,
    /** The Android notification ID of the notification */
    override val androidNotificationId: Int,
    /** The Notifly message ID of the notification */
    override val notiflyMessageId: String? = null,
    /** The importance of the notification. Can be "high", "normal", or "low" */
    override val importance: Importance? = null,
    /** Whether to show a badge on the app icon */
    override val disableBadge: Boolean? = null,
    /** The URL to open when the notification is clicked */
    override val url: String? = null,
    /** The URL of the image to display in the notification */
    override val imageUrl: String? = null,
    /** Sent time of the notification in milliseconds */
    override val sentTime: Long,
    /** TTL of the notification in seconds */
    override val ttl: Int,
    /** Custom data from the push notification */
    override val customData: HashMap<String, String>,
    /** Raw data from the push notification */
    override val rawPayload: String,
//    /** The ID of the notification channel to use */
//    val channelId: String? = null,
//    /** The notification's icon. */
//    /** Sets the notification icon to myicon for drawable resource myicon. */
//    /** If you don't send this key in the request, Notifly uses a default icon. */
//    val icon: String? = null,
//    /** The notification's icon color, expressed in #rrggbb format */
//    val color: String? = null,
//    /** The sound to play when the notification is shown. This is usually the name of a sound resource. */
//    val sound: String? = null,
) : IPushNotification {
    companion object {
        private const val DEFAULT_TTL_IF_NOT_IN_PAYLOAD = 259_200
        private const val GOOGLE_TTL_KEY = "google.ttl"
        private const val GOOGLE_SENT_TIME_KEY = "google.sent_time"
        private const val NOTIFLY_INTERNAL_DATA_KEY = "notifly"

        private val RESERVED_KEYS = listOf("from", "notification", "message_type")

        private fun isReservedKey(key: String): Boolean = key.startsWith("google") || key.startsWith("gcm") || RESERVED_KEYS.contains(key) || key == NOTIFLY_INTERNAL_DATA_KEY

        private fun bundleAsJSONObject(bundle: Bundle): JSONObject {
            val json = JSONObject()
            val keys = bundle.keySet()

            for (key in keys) {
                try {
                    json.put(key, bundle.get(key) ?: JSONObject.NULL)
                } catch (e: JSONException) {
                    Logger.e("Failed to convert bundle to json for key: $key", e)
                }
            }
            return json
        }

        private fun extractCustomDataFromJSONObject(bundle: Bundle): HashMap<String, String> {
            val keySet = bundle.keySet()
            val customData = HashMap<String, String>()
            for (key in keySet) {
                if (!isReservedKey(key)) {
                    try {
                        customData[key] = bundle.getString(key)!!
                    } catch (e: Exception) {
                        Logger.d("Invalid value for key: $key", e)
                    }
                }
            }
            return customData
        }

        fun fromIntentExtras(from: Bundle): PushNotification? {
            val internalDataString = from.getString(NOTIFLY_INTERNAL_DATA_KEY)
            if (internalDataString == null) {
                Logger.d(
                    "FCM message does not have keys for push notification",
                )
                return null
            }

            val notiflyJSONObject =
                try {
                    JSONObject(internalDataString)
                } catch (e: JSONException) {
                    Logger.e("Failed to parse internal data", e)
                    return null
                }

            return PushNotification(
                body = if (notiflyJSONObject.has("bd")) notiflyJSONObject.getString("bd") else null,
                title = if (notiflyJSONObject.has("ti")) notiflyJSONObject.getString("ti") else null,
                campaignId = if (notiflyJSONObject.has("cid")) notiflyJSONObject.getString("cid") else null,
                androidNotificationId = SecureRandom().nextInt(),
                notiflyMessageId = if (notiflyJSONObject.has("mid")) notiflyJSONObject.getString("mid") else null,
                url = if (notiflyJSONObject.has("u")) notiflyJSONObject.getString("u") else null,
                imageUrl = if (notiflyJSONObject.has("iu")) notiflyJSONObject.getString("iu") else null,
                importance =
                    if (notiflyJSONObject.has("imp")) {
                        when (notiflyJSONObject.getString("imp")) {
                            "high" -> Importance.HIGH
                            "normal" -> Importance.NORMAL
                            "low" -> Importance.LOW
                            else -> null
                        }
                    } else {
                        null
                    },
                disableBadge = if (notiflyJSONObject.has("db")) notiflyJSONObject.getBoolean("db") else null,
                // The below fields are not yet supported by the Notifly SDK
//                channelId = if (notiflyJSONObject.has("chid")) notiflyJSONObject.getString("chid") else null,
//                icon = if (notiflyJSONObject.has("ic")) notiflyJSONObject.getString("ic") else null,
//                color = if (notiflyJSONObject.has("col")) notiflyJSONObject.getString("col") else null,
//                sound = if (notiflyJSONObject.has("sd")) notiflyJSONObject.getString("sd") else null,
                sentTime =
                    if (from.containsKey(GOOGLE_SENT_TIME_KEY)) {
                        from.getLong(
                            GOOGLE_SENT_TIME_KEY,
                        )
                    } else {
                        NotiflyTimerUtil.getTimestampMillis()
                    },
                ttl = if (from.containsKey(GOOGLE_TTL_KEY)) from.getInt(GOOGLE_TTL_KEY) else DEFAULT_TTL_IF_NOT_IN_PAYLOAD,
                customData = extractCustomDataFromJSONObject(from),
                rawPayload = bundleAsJSONObject(from).toString(),
            )
        }
    }
}
