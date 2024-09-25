package tech.notifly.push.interfaces

import androidx.core.app.NotificationCompat

interface INotificationInterceptor {
    fun postBuild(
        builder: NotificationCompat.Builder,
        notification: IPushNotification,
    ): NotificationCompat.Builder = builder

    suspend fun postBuildAsync(
        builder: NotificationCompat.Builder,
        notification: IPushNotification,
    ): NotificationCompat.Builder = builder
}
