package tech.notifly

import org.json.JSONObject

data class Notification(
    val body: String,
    val title: String,
    val url: String? = null,
) {
    constructor(notiflyJsonObject: JSONObject) : this(
        body = notiflyJsonObject.getString("bd"),
        title = notiflyJsonObject.getString("ti"),
        url = if (notiflyJsonObject.has("u")) notiflyJsonObject.getString("u") else null,
    )
}
