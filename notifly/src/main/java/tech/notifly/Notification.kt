package tech.notifly

import org.json.JSONObject

data class Notification(
    val body: String,
    val title: String,
    val campaign_id: String,
    val notifly_message_id: String? = null,
    val url: String? = null,
) {
    // TODO: define more optional fields
    constructor(notiflyJsonObject: JSONObject) : this(
        body = notiflyJsonObject.getString("bd"),
        title = notiflyJsonObject.getString("ti"),
        campaign_id = notiflyJsonObject.getString("cid"),
        notifly_message_id = if (notiflyJsonObject.has("mid")) notiflyJsonObject.getString("mid") else null,
        url = if (notiflyJsonObject.has("u")) notiflyJsonObject.getString("u") else null,
    )
}
