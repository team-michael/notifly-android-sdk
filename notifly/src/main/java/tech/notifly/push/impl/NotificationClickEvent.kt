package tech.notifly.push.impl

import tech.notifly.push.interfaces.INotificationClickEvent
import tech.notifly.push.interfaces.IPushNotification

class NotificationClickEvent(
    override val notification: IPushNotification,
) : INotificationClickEvent
