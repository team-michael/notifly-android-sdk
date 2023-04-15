package tech.notifly.data

import com.google.gson.annotations.SerializedName

/**
 * JSON structure of notification for push_notification
 */
internal data class NotificationData(
    @SerializedName("title") val title: String,
    @SerializedName("body") val body: String
)