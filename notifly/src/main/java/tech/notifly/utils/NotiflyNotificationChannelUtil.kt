package tech.notifly.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import tech.notifly.push.impl.Importance

internal object NotiflyNotificationChannelUtil {
    private const val NOTIFLY_HIGH_IMPORTANCE_NOTIFICATION_CHANNEL_ID =
        "NotiflyHighImportanceNotificationChannelId"
    private const val NOTIFLY_HIGH_IMPORTANCE_WITHOUT_BADGE_NOTIFICATION_CHANNEL_ID =
        "NotiflyHighImportanceWithoutBadgeNotificationChannelId"

    private const val NOTIFLY_NORMAL_IMPORTANCE_NOTIFICATION_CHANNEL_ID =
        "NotiflyNotificationChannelId"
    private const val NOTIFLY_NORMAL_IMPORTANCE_WITHOUT_BADGE_NOTIFICATION_CHANNEL_ID =
        "NotiflyNormalImportanceWithoutBadgeNotificationChannelId"

    private const val NOTIFLY_LOW_IMPORTANCE_NOTIFICATION_CHANNEL_ID =
        "NotiflyLowImportanceNotificationChannelId"
    private const val NOTIFLY_LOW_IMPORTANCE_WITHOUT_BADGE_NOTIFICATION_CHANNEL_ID =
        "NotiflyLowImportanceWithoutBadgeNotificationChannelId"

    @RequiresApi(Build.VERSION_CODES.O)
    fun createNotificationChannels(context: Context) {
        val channelsWithBadge = mutableListOf(
            NotificationChannel(
                NOTIFLY_HIGH_IMPORTANCE_NOTIFICATION_CHANNEL_ID,
                "Notifly High Importance Channel",
                NotificationManager.IMPORTANCE_HIGH
            ), NotificationChannel(
                NOTIFLY_NORMAL_IMPORTANCE_NOTIFICATION_CHANNEL_ID,
                "Notifly Normal Importance Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            ), NotificationChannel(
                NOTIFLY_LOW_IMPORTANCE_NOTIFICATION_CHANNEL_ID,
                "Notifly Low Importance Channel",
                NotificationManager.IMPORTANCE_LOW
            )
        )

        val channelsWithoutBadge = mutableListOf(
            NotificationChannel(
                NOTIFLY_HIGH_IMPORTANCE_WITHOUT_BADGE_NOTIFICATION_CHANNEL_ID,
                "Notifly High Importance Channel Without Badge",
                NotificationManager.IMPORTANCE_HIGH
            ), NotificationChannel(
                NOTIFLY_NORMAL_IMPORTANCE_WITHOUT_BADGE_NOTIFICATION_CHANNEL_ID,
                "Notifly Normal Importance Channel Without Badge",
                NotificationManager.IMPORTANCE_DEFAULT
            ), NotificationChannel(
                NOTIFLY_LOW_IMPORTANCE_WITHOUT_BADGE_NOTIFICATION_CHANNEL_ID,
                "Notifly Low Importance Channel Without Badge",
                NotificationManager.IMPORTANCE_LOW
            )
        )
        for (channel in channelsWithoutBadge) {
            channel.setShowBadge(false)
        }

        val channels = channelsWithBadge + channelsWithoutBadge
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannels(channels)
    }

    fun getSystemPriority(importance: Importance?): Int {
        return when (importance) {
            Importance.HIGH -> NotificationCompat.PRIORITY_HIGH
            Importance.NORMAL -> NotificationCompat.PRIORITY_DEFAULT
            Importance.LOW -> NotificationCompat.PRIORITY_LOW
            else -> NotificationCompat.PRIORITY_DEFAULT
        }
    }

    fun getNotificationChannelId(importance: Importance?, disableBadge: Boolean?): String {
        return when (importance) {
            Importance.HIGH -> {
                if (disableBadge == true) {
                    NOTIFLY_HIGH_IMPORTANCE_WITHOUT_BADGE_NOTIFICATION_CHANNEL_ID
                } else {
                    NOTIFLY_HIGH_IMPORTANCE_NOTIFICATION_CHANNEL_ID
                }
            }

            Importance.NORMAL -> {
                if (disableBadge == true) {
                    NOTIFLY_NORMAL_IMPORTANCE_WITHOUT_BADGE_NOTIFICATION_CHANNEL_ID
                } else {
                    NOTIFLY_NORMAL_IMPORTANCE_NOTIFICATION_CHANNEL_ID
                }
            }

            Importance.LOW -> {
                if (disableBadge == true) {
                    NOTIFLY_LOW_IMPORTANCE_WITHOUT_BADGE_NOTIFICATION_CHANNEL_ID
                } else {
                    NOTIFLY_LOW_IMPORTANCE_NOTIFICATION_CHANNEL_ID
                }
            }

            else -> {
                if (disableBadge == true) {
                    NOTIFLY_NORMAL_IMPORTANCE_WITHOUT_BADGE_NOTIFICATION_CHANNEL_ID
                } else {
                    NOTIFLY_NORMAL_IMPORTANCE_NOTIFICATION_CHANNEL_ID
                }
            }
        }
    }
}