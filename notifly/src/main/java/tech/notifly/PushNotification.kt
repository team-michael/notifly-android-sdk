package tech.notifly

import org.json.JSONObject

data class PushNotification(
    /** The body text of the notification */
    val body: String,
    /** The title text of the notification */
    val title: String,
    /** The Notifly campaign ID of the notification */
    val campaign_id: String,
    /** The Notifly message ID of the notification */
    val notifly_message_id: String? = null,
    /** The URL to open when the notification is clicked */
    val url: String? = null,
    /** The URL of the image to display in the notification */
    val image_url: String? = null,
    /** The ID of the notification channel to use */
    val channel_id: String? = null,
    /** The notification's icon. Sets the notification icon to myicon for drawable resource myicon. If you don't send this key in the request, Notifly uses a default icon. */
    val icon: String? = null,
    /** The notification's icon color, expressed in #rrggbb format */
    val color: String? = null,
    /** The sound to play when the notification is shown. This is usually the name of a sound resource. */
    val sound: String? = null,
) {
    constructor(notiflyJsonObject: JSONObject) : this(
        body = notiflyJsonObject.getString("bd"),
        title = notiflyJsonObject.getString("ti"),
        campaign_id = notiflyJsonObject.getString("cid"),
        notifly_message_id = if (notiflyJsonObject.has("mid")) notiflyJsonObject.getString("mid") else null,
        url = if (notiflyJsonObject.has("u")) notiflyJsonObject.getString("u") else null,
        image_url = if (notiflyJsonObject.has("iu")) notiflyJsonObject.getString("iu") else null,
        // The below fields are not yet supported by the Notifly SDK
        channel_id = if (notiflyJsonObject.has("chid")) notiflyJsonObject.getString("chid") else null,
        icon = if (notiflyJsonObject.has("ic")) notiflyJsonObject.getString("ic") else null,
        color = if (notiflyJsonObject.has("col")) notiflyJsonObject.getString("col") else null,
        sound = if (notiflyJsonObject.has("sd")) notiflyJsonObject.getString("sd") else null,
    )
}
