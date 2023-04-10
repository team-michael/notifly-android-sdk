package tech.notifly.data

import com.google.gson.annotations.SerializedName

/**
 * JSON structure of data for in-app messaging
 */
data class InAppMessageData(
    @SerializedName("campaign_id") val campaignId: String,
    @SerializedName("notifly_message_type") val notiflyMessageType: String,
    @SerializedName("url") val url: String,
    @SerializedName("modal_properties") val modalProperties: ModalPropertiesData
)