package tech.notifly.push

import androidx.core.app.NotificationCompat
import tech.notifly.push.impl.NotificationClickEvent
import tech.notifly.push.interfaces.INotificationClickListener
import tech.notifly.push.interfaces.INotificationInterceptor
import tech.notifly.push.interfaces.IPushNotification
import tech.notifly.utils.EventProducer
import tech.notifly.utils.Logger

object PushNotificationManager {
    private val clickListeners = EventProducer<INotificationClickListener>()
    private val unprocessedNotifications = ArrayDeque<IPushNotification>()
    private val interceptors = mutableListOf<INotificationInterceptor>()

    fun addClickListener(listener: INotificationClickListener) {
        Logger.d("PushNotificationManager: addClickListener called")
        clickListeners.subscribe(listener)

        if (clickListeners.hasSubscribers && unprocessedNotifications.any()) {
            Logger.d("PushNotificationManager: dispatching ${unprocessedNotifications.size} unprocessed notifications")
            for (notification in unprocessedNotifications) {
                Logger.d("Dispatching stored notification: $notification")
                clickListeners.fireOnMain { it.onClick(NotificationClickEvent(notification)) }
            }
        }
    }

    fun removeClickListener(listener: INotificationClickListener) {
        Logger.d("PushNotificationManager: removeClickListener called")
        clickListeners.unsubscribe(listener)
    }

    fun notificationOpened(data: IPushNotification) {
        Logger.d("PushNotificationManager: notificationOpened called with $data")

        if (clickListeners.hasSubscribers) {
            Logger.d("PushNotificationManager: notifying listeners")
            clickListeners.fireOnMain { it.onClick(NotificationClickEvent(data)) }
        } else {
            Logger.d("PushNotificationManager: no listeners available, queuing notification")
            unprocessedNotifications.add(data)
        }
    }

    fun addInterceptor(interceptor: INotificationInterceptor) {
        Logger.d("PushNotificationManager: addInterceptor called: $interceptor")
        interceptors.add(interceptor)
    }

    fun removeInterceptor(interceptor: INotificationInterceptor) {
        Logger.d("PushNotificationManager: removeInterceptor called: $interceptor")
        interceptors.remove(interceptor)
    }

    suspend fun applyPostBuild(
        builder: NotificationCompat.Builder,
        notification: IPushNotification,
    ) {
        Logger.d("PushNotificationManager: applyPostBuild called with ${interceptors.size} interceptors")
        for (interceptor in interceptors) {
            Logger.d("Applying interceptor: $interceptor")
            interceptor.postBuild(builder, notification)
            interceptor.postBuildAsync(builder, notification)
        }
    }
}
