@file:Suppress("DEPRECATION")

package tech.notifly.push

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.legacy.content.WakefulBroadcastReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import tech.notifly.R
import tech.notifly.utils.Logger
import tech.notifly.utils.NotiflyLogUtil
import tech.notifly.utils.NotiflyNotificationChannelUtil
import tech.notifly.utils.OSUtils
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class FCMBroadcastReceiver : WakefulBroadcastReceiver() {
    companion object {
        @Volatile
        var requestCodeCounter = 0
    }

    override fun onReceive(context: Context, intent: Intent) {
        Thread {
            try {
                handleFCMMessage(context, intent)
            } catch (e: Exception) {
                Logger.e("FCMBroadcastReceiver onReceive failed", e)
            }
        }.start()
    }

    @Throws(Exception::class)
    private fun handleFCMMessage(context: Context, intent: Intent) {
        val extras = intent.extras ?: run {
            Logger.d("FCM message does not have data field")
            return
        }

        val jsonObject = bundleAsJSONObject(extras)
        Logger.d("FCMBroadcastReceiver intent: $jsonObject")

        val pushNotification = extractPushNotification(jsonObject)
        if (pushNotification != null) {
            val isAppInForeground = OSUtils.isAppInForeground(context)
            logPushDelivered(context, pushNotification, isAppInForeground)
            showPushNotification(context, pushNotification)
        }
    }

    private fun extractPushNotification(jsonObject: JSONObject): PushNotification? {
        if (!jsonObject.has("notifly")) {
            Logger.d(
                "FCM message does not have keys for push notification"
            )
            return null
        }

        val notiflyString = jsonObject.getString("notifly")
        val notiflyJSONObject = JSONObject(notiflyString)
        if (!notiflyJSONObject.has("type") || notiflyJSONObject.getString("type") != "push-notification") {
            Logger.d(
                "FCM message is not a Notifly push notification"
            )
            return null
        }
        return PushNotification(notiflyJSONObject)
    }

    private fun logPushDelivered(
        context: Context, pushNotification: PushNotification, isAppInForeground: Boolean
    ) {
        val campaignId = pushNotification.campaign_id
        val notiflyMessageId = pushNotification.notifly_message_id

        NotiflyLogUtil.logEventSync(
            context, "push_delivered", mapOf(
                "type" to "message_event",
                "channel" to "push-notification",
                "campaign_id" to campaignId,
                "notifly_message_id" to notiflyMessageId,
                "status" to if (isAppInForeground) "foreground" else "background"
            ), listOf(), true
        )
    }

    private fun showPushNotification(context: Context, pushNotification: PushNotification) {
        val title = pushNotification.title
        val body = pushNotification.body
        val url = pushNotification.url
        val campaignId = pushNotification.campaign_id
        val notiflyMessageId = pushNotification.notifly_message_id
        val imageUrl = pushNotification.image_url
        val bitmap = runBlocking { loadImage(imageUrl) }

        val notificationOpenIntent =
            Intent(context, PushNotificationOpenActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra("title", title)
                putExtra("body", body)
                putExtra("url", url)
                putExtra("campaign_id", campaignId)
                putExtra("notifly_message_id", notiflyMessageId)
            }

        requestCodeCounter++
        val uniqueRequestCode = notiflyMessageId.hashCode().let {
            if (it == 0) requestCodeCounter else it
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            uniqueRequestCode,
            notificationOpenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotiflyNotificationChannelUtil.createNotificationChannels(context)
        }

        val channelId =
            NotiflyNotificationChannelUtil.getNotificationChannelId(pushNotification.importance)
        val priority = NotiflyNotificationChannelUtil.getSystemPriority(pushNotification.importance)
        val notificationIcon = getNotificationIcon(context)

        val builder = NotificationCompat.Builder(context, channelId).setSmallIcon(notificationIcon)
            .setContentTitle(title).setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent).setAutoCancel(true).setPriority(priority)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        if (bitmap != null) {
            builder.setStyle(
                NotificationCompat.BigPictureStyle().bigPicture(bitmap)
                    .bigLargeIcon(null as Bitmap?)
            )
        }

        val notification = builder.build()
        Logger.d("FCMBroadcastReceiver notification: $notification")

        val notificationId = notiflyMessageId?.toIntOrNull() ?: 1
        // Show the notification
        if (ActivityCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } else {
            Logger.w("POST_NOTIFICATIONS permission is not granted")
        }
    }

    private fun bundleAsJSONObject(bundle: Bundle): JSONObject {
        val json = JSONObject()
        val keys = bundle.keySet()

        for (key in keys) {
            try {
                json.put(key, bundle.get(key))
            } catch (e: JSONException) {
                Logger.e("Failed to convert bundle to json", e)
            }
        }
        return json
    }

    private fun getNotificationIcon(context: Context): Int {
        val res = context.resources
        val packageName = context.packageName
        val notificationIconName = "ic_stat_notifly_default"

        @SuppressLint("ResourceType") val notificationIconResId =
            res.getIdentifier(notificationIconName, "drawable", packageName)
        return if (notificationIconResId != 0) {
            notificationIconResId
        } else {
            // return launcher icon
            val appInfo = context.packageManager.getApplicationInfo(
                context.packageName, PackageManager.GET_META_DATA
            )
            val launcherIcon = appInfo.icon
            if (launcherIcon != 0) {
                launcherIcon
            } else {
                R.drawable.baseline_notifications_24 // bell icon
            }
        }
    }

    private suspend fun getBitmapFromURL(src: String): Bitmap? {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL(src)
                val connection = url.openConnection() as HttpURLConnection
                connection.doInput = true
                connection.connect()
                val input = connection.inputStream
                BitmapFactory.decodeStream(input)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    private suspend fun loadImage(imageUrl: String?): Bitmap? = suspendCoroutine { continuation ->
        if (imageUrl != null) {
            GlobalScope.launch {
                val bitmap = getBitmapFromURL(imageUrl)
                Logger.d("FCMBroadcastReceiver bitmap: $bitmap")
                continuation.resume(bitmap)
            }
        } else {
            continuation.resume(null)
        }
    }
}
