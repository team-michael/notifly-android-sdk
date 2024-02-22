package tech.notifly.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import tech.notifly.push.Importance

internal object NotiflyNotificationChannelUtil {
    private const val NOTIFLY_HIGH_IMPORTANCE_NOTIFICATION_CHANNEL_ID =
        "NotiflyHighImportanceNotificationChannelId"
    private const val NOTIFLY_NORMAL_IMPORTANCE_NOTIFICATION_CHANNEL_ID =
        "NotiflyNotificationChannelId"
    private const val NOTIFLY_LOW_IMPORTANCE_NOTIFICATION_CHANNEL_ID =
        "NotiflyLowImportanceNotificationChannelId"

    @RequiresApi(Build.VERSION_CODES.O)
    fun createNotificationChannels(context: Context) {
        val channels = mutableListOf(
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

    fun getNotificationChannelId(importance: Importance?): String {
        return when (importance) {
            Importance.HIGH -> NOTIFLY_HIGH_IMPORTANCE_NOTIFICATION_CHANNEL_ID
            Importance.NORMAL -> NOTIFLY_NORMAL_IMPORTANCE_NOTIFICATION_CHANNEL_ID
            Importance.LOW -> NOTIFLY_LOW_IMPORTANCE_NOTIFICATION_CHANNEL_ID
            else -> NOTIFLY_NORMAL_IMPORTANCE_NOTIFICATION_CHANNEL_ID
        }
    }
}