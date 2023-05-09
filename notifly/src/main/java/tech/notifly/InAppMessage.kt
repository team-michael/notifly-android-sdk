package tech.notifly

import android.util.Base64
import android.util.Log
import org.json.JSONException
import org.json.JSONObject

data class InAppMessage(
    val campaign_id: String,
    val url: String,
    val notifly_message_id: String? = null,
    val modal_properties: JSONObject? = null,
) {
    companion object {
        fun fromFCMPayload(payload: JSONObject): InAppMessage? {
            return try {
                val encodedData = payload.getString("notifly_in_app_message_data")
                val decodedJsonString = decodeBase64Json(encodedData)
                val jsonObject = JSONObject(decodedJsonString)
                parseInAppMessageJson(jsonObject)
            } catch (e: Exception) {
                Log.e("InAppMessage", "Failed to decode or parse JSON", e)
                null
            }
        }


        private fun decodeBase64Json(encodedJson: String): String {
            return String(Base64.decode(encodedJson, Base64.DEFAULT))
        }

        private fun parseInAppMessageJson(jsonObject: JSONObject): InAppMessage {
            return InAppMessage(
                campaign_id = jsonObject.getString("campaign_id"),
                url = jsonObject.getString("url"),
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
    }
}
