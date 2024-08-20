package tech.notifly.push.interceptors

import androidx.core.app.NotificationCompat
import tech.notifly.push.interfaces.IPushNotification

interface INotificationInterceptor {
    fun intercept(
        builder: NotificationCompat.Builder,
        notification: IPushNotification,
    ): NotificationCompat.Builder
}
