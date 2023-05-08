package tech.notifly

import org.json.JSONObject

data class Notification(
    val body: String,
    val title: String,
    val url: String? = null,
) {
    // TODO: define more optional fields
    constructor(notiflyJsonObject: JSONObject) : this(
        body = notiflyJsonObject.getString("bd"),
        title = notiflyJsonObject.getString("ti"),
        url = if (notiflyJsonObject.has("u")) notiflyJsonObject.getString("u") else null,
    )
}
