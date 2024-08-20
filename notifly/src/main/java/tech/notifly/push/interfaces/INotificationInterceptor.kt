package tech.notifly.push.interfaces

import androidx.core.app.NotificationCompat

interface INotificationInterceptor {
    fun intercept(
        builder: NotificationCompat.Builder,
        notification: IPushNotification,
    ): NotificationCompat.Builder
}
