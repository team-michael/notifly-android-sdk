package tech.notifly.push.interfaces

import tech.notifly.push.impl.Importance
import java.io.Serializable

interface IPushNotification : Serializable {
    val body: String?
    val title: String?
    val campaignId: String?
    val androidNotificationId: Int
    val notiflyMessageId: String?
    val importance: Importance?
    val url: String?
    val imageUrl: String?

    //    /** The ID of the notification channel to use */
//    val channelId: String? = null
//    /** The notification's icon. Sets the notification icon to myicon for drawable resource myicon. If you don't send this key in the request, Notifly uses a default icon. */
//    val icon: String? = null
//    /** The notification's icon color, expressed in #rrggbb format */
//    val color: String? = null
//    /** The sound to play when the notification is shown. This is usually the name of a sound resource. */
//    val sound: String? = null

    val sentTime: Long
    val ttl: Int
    val customData: HashMap<String, String>
    val rawPayload: String
}
