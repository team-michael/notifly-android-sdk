@file:Suppress("DEPRECATION")

package tech.notifly.push

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import tech.notifly.Notifly
import tech.notifly.R
import tech.notifly.push.activities.NotificationOpenedActivity
import tech.notifly.push.impl.PushNotification
import tech.notifly.push.interfaces.IPushNotification
import tech.notifly.utils.Logger
import tech.notifly.utils.NotiflyLogUtil
import tech.notifly.utils.NotiflyNotificationChannelUtil
import tech.notifly.utils.OSUtils
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class FCMBroadcastReceiver : BroadcastReceiver() {
    companion object {
        private const val FCM_RECEIVE_ACTION = "com.google.android.c2dm.intent.RECEIVE"
        private const val FCM_TYPE = "gcm"
        private const val MESSAGE_TYPE_EXTRA_KEY = "message_type"

        @Volatile
        var requestCodeCounter = 0

        private fun isFCMMessage(intent: Intent): Boolean {
            if (FCM_RECEIVE_ACTION == intent.action) {
                val messageType = intent.getStringExtra(MESSAGE_TYPE_EXTRA_KEY)
                return messageType == null || FCM_TYPE == messageType
            }
            return false
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val bundle = intent.extras
        if (bundle == null || "google.com/iid" == bundle.getString("from") || bundle.getString("notifly") == null) {
            return
        }

        if (!Notifly.initializeWithContext(context)) {
            return
        }

        if (!isFCMMessage(intent)) {
            setSuccessfulResultCode()
            return
        }

        try {
            processFCMMessage(context, bundle)
        } catch (e: Exception) {
            Logger.e("FCMBroadcastReceiver onReceive failed", e)
        }

        setSuccessfulResultCode()
    }

    @Throws(Exception::class)
    private fun processFCMMessage(context: Context, bundle: Bundle) {
        Logger.d("FCMBroadcastReceiver intent: $bundle")

        val pushNotification = PushNotification.fromIntentExtras(bundle)
        if (pushNotification != null) {
            val isAppInForeground = OSUtils.isAppInForeground(context)
            showPushNotification(context, pushNotification, isAppInForeground)
        } else {
            Logger.d("FCM message is not valid or not a message from Notifly. Ignoring...")
        }
    }

    private fun logPushDelivered(
        context: Context, pushNotification: IPushNotification, isAppInForeground: Boolean
    ) {
        val campaignId = pushNotification.campaignId
        val notiflyMessageId = pushNotification.notiflyMessageId

        NotiflyLogUtil.logEventNonBlocking(
            context, "push_delivered", mapOf(
                "type" to "message_event",
                "channel" to "push-notification",
                "campaign_id" to campaignId,
                "notifly_message_id" to notiflyMessageId,
                "status" to if (isAppInForeground) "foreground" else "background"
            ), listOf(), true
        )
    }

    private fun showPushNotification(
        context: Context, pushNotification: IPushNotification, wasAppInForeground: Boolean
    ) {
        val body = pushNotification.body
        val title = pushNotification.title
        val notificationId = pushNotification.androidNotificationId
        val notiflyMessageId = pushNotification.notiflyMessageId
        val imageUrl = pushNotification.imageUrl
        val bitmap = runBlocking { loadImage(imageUrl) }

        val notificationOpenIntent = Intent(context, NotificationOpenedActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("notification", pushNotification)
            putExtra("was_app_in_foreground", wasAppInForeground)
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

        val channelId = NotiflyNotificationChannelUtil.getNotificationChannelId(
            pushNotification.importance, pushNotification.disableBadge
        )
        val priority = NotiflyNotificationChannelUtil.getSystemPriority(pushNotification.importance)
        val notificationIcon = getNotificationIcon(context)

        val builder = NotificationCompat.Builder(context, channelId).setSmallIcon(notificationIcon)
            .setContentTitle(title).setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent).setAutoCancel(true).setPriority(priority)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        PushNotificationManager.applyInterceptors(builder, pushNotification)

        if (bitmap != null) {
            builder.setStyle(
                NotificationCompat.BigPictureStyle().bigPicture(bitmap)
                    .bigLargeIcon(null as Bitmap?)
            )
        }

        val notification = builder.build()
        Logger.d("FCMBroadcastReceiver notification: $notification")

        // Show the notification
        if (ActivityCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
            logPushDelivered(context, pushNotification, wasAppInForeground)
        } else {
            Logger.w("POST_NOTIFICATIONS permission is not granted")
        }
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

    private fun setSuccessfulResultCode() {
        if (isOrderedBroadcast) {
            resultCode = Activity.RESULT_OK
        }
    }
}
