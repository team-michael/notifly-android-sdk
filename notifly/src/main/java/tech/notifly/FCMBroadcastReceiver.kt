@file:Suppress("DEPRECATION")

package tech.notifly

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.legacy.content.WakefulBroadcastReceiver
import org.json.JSONException
import org.json.JSONObject
import tech.notifly.inapp.NotiflyInAppMessageBroadcastReceiver
import tech.notifly.utils.NotiflyLogUtil
import tech.notifly.utils.OSUtils

class FCMBroadcastReceiver : WakefulBroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Thread {
            try {
                handleFCMMessage(context, intent)
            } catch (e: Exception) {
                Log.e(Notifly.TAG, "onReceive failed", e)
            }
        }.start()
    }

    @Throws(Exception::class)
    private fun handleFCMMessage(context: Context, intent: Intent) {
        val extras = intent.extras ?: run {
            Log.e(Notifly.TAG, "intent extras is NULL")
            return
        }

        val keys = extras.keySet()
        for (key in keys) {
            Log.d(Notifly.TAG, "FCMBroadcastReceiver intent key: $key, value: " + extras.get(key))
        }

        val jsonObject = bundleAsJSONObject(extras)
        val pushNotification = extractPushNotification(jsonObject)
        if (pushNotification != null) {
            val isAppInForeground = OSUtils.isAppInForeground(context)
            logPushDelivered(context, pushNotification, isAppInForeground)
            showPushNotification(context, pushNotification, isAppInForeground)
        } else if (OSUtils.isAppInForeground(context)) {
            extractInAppMessage(jsonObject)?.let { inAppMessage ->
                showInAppMessage(context, inAppMessage)
            }
        }

    }

    private fun extractPushNotification(jsonObject: JSONObject): PushNotification? {
        if (!jsonObject.has("notifly")) {
            Log.d(
                Notifly.TAG,
                "FCM message does not have keys for push notification"
            )
            return null
        }

        val notiflyString = jsonObject.getString("notifly")
        val notiflyJSONObject = JSONObject(notiflyString)
        return PushNotification(notiflyJSONObject)
    }

    private fun extractInAppMessage(jsonObject: JSONObject): InAppMessage? {
        if (!jsonObject.has("notifly_message_type")
            || jsonObject.getString("notifly_message_type") != "in-app-message"
            || !jsonObject.has("notifly_in_app_message_data")
        ) {
            Log.d(
                Notifly.TAG,
                "FCM message does not have keys for in-app message"
            )
            return null
        }

        return InAppMessage.fromFCMPayload(jsonObject)
    }

    private fun logPushDelivered(
        context: Context,
        pushNotification: PushNotification,
        isAppInForeground: Boolean
    ) {
        val campaignId = pushNotification.campaign_id
        val notiflyMessageId = pushNotification.notifly_message_id

        NotiflyLogUtil.logEvent(
            context,
            "push_delivered",
            mapOf(
                "type" to "message_event",
                "channel" to "push-notification",
                "campaign_id" to campaignId,
                "notifly_message_id" to notiflyMessageId,
                "status" to if (isAppInForeground) "foreground" else "background"
            ),
            listOf(),
            true
        )
    }

    private fun showPushNotification(
        context: Context,
        pushNotification: PushNotification,
        isAppInForeground: Boolean
    ) {
        val title = pushNotification.title
        val body = pushNotification.body
        val url = pushNotification.url
        val campaignId = pushNotification.campaign_id
        val notiflyMessageId = pushNotification.notifly_message_id

        val notificationOpenIntent =
            Intent(context, PushNotificationOpenActivity::class.java).apply {
                putExtra("title", title)
                putExtra("body", body)
                putExtra("url", url)
                putExtra("campaign_id", campaignId)
                putExtra("notifly_message_id", notiflyMessageId)
                putExtra("was_app_in_foreground", isAppInForeground)
            }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            notificationOpenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelName = "Notifly Notification Channel"
            val channelDescription = "This is the Notifly Notification Channel"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel =
                NotificationChannel(Notifly.NOTIFICATION_CHANNEL_ID, channelName, importance)
            channel.description = channelDescription

            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }

        val builder = NotificationCompat.Builder(context, Notifly.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_delete) // TODO: replace with a default icon
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        // TODO: set style
        // builder.setStyle(NotificationCompat.BigTextStyle().bigText(body))

        val notification = builder.build()
        // log
        Log.d(Notifly.TAG, "FCMBroadcastReceiver notification: $notification")

        val notificationId = notiflyMessageId?.toIntOrNull() ?: 1
        // Show the notification
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } else {
            Log.w(Notifly.TAG, "POST_NOTIFICATIONS permission is not granted")
        }
    }

    private fun showInAppMessage(context: Context, inAppMessage: InAppMessage) {
        val campaignId = inAppMessage.campaign_id
        val url = inAppMessage.url
        val notiflyMessageId = inAppMessage.notifly_message_id
        val modalProperties = inAppMessage.modal_properties

        val inAppMessageShowIntent = Intent(
            context,
            NotiflyInAppMessageBroadcastReceiver::class.java
        ).apply {
            putExtra("in_app_message_campaign_id", campaignId)
            putExtra("in_app_message_url", url)
            putExtra("notifly_message_id", notiflyMessageId)
            putExtra("modal_properties", modalProperties)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.sendBroadcast(inAppMessageShowIntent)
    }

    private fun bundleAsJSONObject(bundle: Bundle): JSONObject {
        val json = JSONObject()
        val keys = bundle.keySet()

        for (key in keys) {
            try {
                json.put(key, bundle.get(key))
            } catch (e: JSONException) {
                Log.e(Notifly.TAG, "Failed to convert bundle to json", e)
            }
        }
        return json
    }

}
