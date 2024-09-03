package tech.notifly.push

import androidx.core.app.NotificationCompat
import tech.notifly.push.impl.NotificationClickEvent
import tech.notifly.push.interfaces.INotificationClickListener
import tech.notifly.push.interfaces.INotificationInterceptor
import tech.notifly.push.interfaces.IPushNotification
import tech.notifly.utils.EventProducer

object PushNotificationManager {
    private val clickListeners = EventProducer<INotificationClickListener>()
    private val unprocessedNotifications = ArrayDeque<IPushNotification>()
    private val interceptors = mutableListOf<INotificationInterceptor>()

    fun addClickListener(listener: INotificationClickListener) {
        clickListeners.subscribe(listener)

        if (clickListeners.hasSubscribers && unprocessedNotifications.any()) {
            for (notification in unprocessedNotifications) {
                clickListeners.fireOnMain { it.onClick(NotificationClickEvent(notification)) }
            }
        }
    }

    fun removeClickListener(listener: INotificationClickListener) = clickListeners.unsubscribe(listener)

    fun notificationOpened(data: IPushNotification) {
        if (clickListeners.hasSubscribers) {
            clickListeners.fireOnMain { it.onClick(NotificationClickEvent(data)) }
        } else {
            unprocessedNotifications.add(data)
        }
    }

    fun addInterceptor(interceptor: INotificationInterceptor) {
        interceptors.add(interceptor)
    }

    fun removeInterceptor(interceptor: INotificationInterceptor) {
        interceptors.remove(interceptor)
    }

    fun applyPostBuild(
        builder: NotificationCompat.Builder,
        notification: IPushNotification,
    ) {
        for (interceptor in interceptors) {
            interceptor.postBuild(builder, notification)
        }
    }
}
