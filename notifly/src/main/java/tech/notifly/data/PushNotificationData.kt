package tech.notifly.data

import com.google.gson.annotations.SerializedName

/**
 * JSON structure of data for push_notification
 */
internal data class PushNotificationData(
    @SerializedName("campaign_id") val campaignId: String,
    @SerializedName("link") val link: String
)