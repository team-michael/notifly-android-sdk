package tech.notifly

import android.util.Base64
import org.json.JSONObject

data class InAppMessage(
    val campaign_id: String,
    val url: String,
    val notifly_message_id: String? = null,
    val modal_properties: String? = null,
) {
    companion object {
        fun fromFCMPayload(payload: JSONObject): InAppMessage? {
            return try {
                val encodedData = payload.getString("notifly_in_app_message_data")
                val decodedJsonString = decodeBase64Json(encodedData)
                val jsonObject = JSONObject(decodedJsonString)
                parseInAppMessageJson(jsonObject)
            } catch (e: Exception) {
                Logger.e("InAppMessage: Failed to decode or parse JSON", e)
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
                modal_properties = if (jsonObject.has("modal_properties")) jsonObject.getString("modal_properties") else null,
            )
        }
    }
}
