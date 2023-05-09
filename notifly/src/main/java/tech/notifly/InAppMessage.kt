package tech.notifly

import android.util.Log
import org.json.JSONException
import org.json.JSONObject

data class InAppMessage(
    val campaign_id: String,
    val url: String,
    val notifly_message_type: String,
    val notifly_message_id: String? = null,
    val modal_properties: JSONObject? = null,
) {
    // TODO(minyong): update with encoded payload from lambda
    constructor(jsonObject: JSONObject) : this(
        campaign_id = jsonObject.getString("campaign_id"),
        url = jsonObject.getString("url"),
        notifly_message_type = jsonObject.getString("notifly_message_type"),
        notifly_message_id = if (jsonObject.has("mid")) jsonObject.getString("mid") else null,
        modal_properties = if (jsonObject.has("modal_properties")) {
            try {
                JSONObject(jsonObject.getString("modal_properties"))
            } catch (e: JSONException) {
                Log.e(Notifly.TAG, "Failed to parse modal_properties JSON", e)
                null
            }
        } else {
            null
        }
    )
}
