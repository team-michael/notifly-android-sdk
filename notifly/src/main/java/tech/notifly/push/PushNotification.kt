package tech.notifly.push

import org.json.JSONObject
import tech.notifly.utils.Logger
import tech.notifly.utils.NotiflyTimerUtil
import java.io.Serializable
import java.security.SecureRandom

enum class Importance {
    HIGH, NORMAL, LOW
}

data class PushNotification(
    /** The body text of the notification */
    val body: String? = null,
    /** The title text of the notification */
    val title: String? = null,
    /** The Notifly campaign ID of the notification */
    val campaignId: String? = null,
    /** The Android notification ID of the notification */
    val androidNotificationId: Int,
    /** The Notifly message ID of the notification */
    val notiflyMessageId: String? = null,
    /** The importance of the notification. Can be "high", "normal", or "low" */
    val importance: Importance? = null,
    /** The URL to open when the notification is clicked */
    val url: String? = null,
    /** The URL of the image to display in the notification */
    val imageUrl: String? = null,
//    /** The ID of the notification channel to use */
//    val channelId: String? = null,
//    /** The notification's icon. Sets the notification icon to myicon for drawable resource myicon. If you don't send this key in the request, Notifly uses a default icon. */
//    val icon: String? = null,
//    /** The notification's icon color, expressed in #rrggbb format */
//    val color: String? = null,
//    /** The sound to play when the notification is shown. This is usually the name of a sound resource. */
//    val sound: String? = null,
    /** Sent time of the notification in milliseconds */
    val sentTime: Long,
    /** TTL of the notification in seconds */
    val ttl: Int,
    /** Custom data from the push notification */
    val customData: HashMap<String, String>,
    /** Raw data from the push notification */
    val rawPayload: String,
) : Serializable {
    companion object {
        private const val DEFAULT_TTL_IF_NOT_IN_PAYLOAD = 259_200
        private const val GOOGLE_TTL_KEY = "google.ttl"
        private const val GOOGLE_SENT_TIME_KEY = "google.sent_time"
        private const val NOTIFLY_INTERNAL_DATA_KEY = "notifly"
        private const val NOTIFLY_PUSH_NOTIFICATION_TYPE = "push-notification"

        private fun extractCustomDataFromJSONObject(jsonObject: JSONObject): HashMap<String, String> {
            val keySet = jsonObject.keys()
            val customData = HashMap<String, String>()
            while (keySet.hasNext()) {
                val key = keySet.next()
                if (!key.startsWith("google.") && key != NOTIFLY_INTERNAL_DATA_KEY) {
                    try {
                        customData[key] = jsonObject.getString(key)
                    } catch (e: Exception) {
                        Logger.d("Invalid value for key: $key", e)
                    }
                }
            }
            return customData
        }

        fun fromFCMPayload(from: JSONObject): PushNotification? {
            if (!from.has(NOTIFLY_INTERNAL_DATA_KEY)) {
                Logger.d(
                    "FCM message does not have keys for push notification"
                )
                return null
            }

            val notiflyString = from.getString("notifly")
            val notiflyJSONObject = JSONObject(notiflyString)
            if (!notiflyJSONObject.has("type") || notiflyJSONObject.getString("type") != NOTIFLY_PUSH_NOTIFICATION_TYPE) {
                Logger.d(
                    "FCM message is not a Notifly push notification"
                )
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
                importance = if (notiflyJSONObject.has("imp")) {
                    when (notiflyJSONObject.getString("imp")) {
                        "high" -> Importance.HIGH
                        "normal" -> Importance.NORMAL
                        "low" -> Importance.LOW
                        else -> null
                    }
                } else null,
                // The below fields are not yet supported by the Notifly SDK
                channelId = if (notiflyJSONObject.has("chid")) notiflyJSONObject.getString("chid") else null,
//                icon = if (notiflyJSONObject.has("ic")) notiflyJSONObject.getString("ic") else null,
//                color = if (notiflyJSONObject.has("col")) notiflyJSONObject.getString("col") else null,
//                sound = if (notiflyJSONObject.has("sd")) notiflyJSONObject.getString("sd") else null,
                sentTime = if (from.has(GOOGLE_SENT_TIME_KEY)) from.getLong(GOOGLE_SENT_TIME_KEY) else NotiflyTimerUtil.getTimestampMillis(),
                ttl = if (from.has(GOOGLE_TTL_KEY)) from.getInt(GOOGLE_TTL_KEY) else DEFAULT_TTL_IF_NOT_IN_PAYLOAD,
                customData = extractCustomDataFromJSONObject(from),
                rawPayload = from.toString()
            )
        }
    }
}
