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
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tech.notifly.Notifly
import tech.notifly.R
import tech.notifly.push.activities.NotificationOpenedActivity
import tech.notifly.push.impl.PushNotification
import tech.notifly.push.interfaces.IPushNotification
import tech.notifly.utils.Logger
import tech.notifly.utils.NotiflyLogUtil
import tech.notifly.utils.NotiflyNotificationChannelUtil
import tech.notifly.utils.OSUtil
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.URL

class FCMBroadcastReceiver : BroadcastReceiver() {
    companion object {
        private const val FCM_RECEIVE_ACTION = "com.google.android.c2dm.intent.RECEIVE"
        private const val FCM_TYPE = "gcm"
        private const val MESSAGE_TYPE_EXTRA_KEY = "message_type"
        private const val CONNECT_TIMEOUT_MS = 1000L
        private const val READ_TIMEOUT_MS = 3000L

        // 광고 푸시: 커스텀 데이터에 unsubscribe_url 이 있으면 "수신거부" 커스텀 행을 단다.
        private const val UNSUBSCRIBE_URL_DATA_KEY = "unsubscribe_url"

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

    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
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

        val pendingResult = goAsync()
        coroutineScope.launch {
            try {
                processFCMMessage(context, bundle)
            } catch (e: Exception) {
                Logger.e("FCMBroadcastReceiver onReceive failed", e)
            } finally {
                pendingResult.finish()
            }
        }

        setSuccessfulResultCode()
    }

    @Throws(Exception::class)
    private suspend fun processFCMMessage(
        context: Context,
        bundle: Bundle,
    ) {
        Logger.d("FCMBroadcastReceiver intent: $bundle")

        val pushNotification = PushNotification.fromIntentExtras(bundle)
        if (pushNotification != null) {
            val isAppInForeground = OSUtil.isAppInForeground(context)
            showPushNotification(context, pushNotification, isAppInForeground)
        } else {
            Logger.d("FCM message is not valid or not a message from Notifly. Ignoring...")
        }
    }

    private fun logPushDelivered(
        context: Context,
        pushNotification: IPushNotification,
        isAppInForeground: Boolean,
    ) {
        val campaignId = pushNotification.campaignId
        val notiflyMessageId = pushNotification.notiflyMessageId

        NotiflyLogUtil.logEventNonBlocking(
            context,
            "push_delivered",
            mapOf(
                "type" to "message_event",
                "channel" to "push-notification",
                "campaign_id" to campaignId,
                "notifly_message_id" to notiflyMessageId,
                "status" to if (isAppInForeground) "foreground" else "background",
            ),
            listOf(),
            true,
        )
    }

    private fun logPushNotDelivered(
        context: Context,
        pushNotification: IPushNotification,
    ) {
        val campaignId = pushNotification.campaignId
        val notiflyMessageId = pushNotification.notiflyMessageId

        NotiflyLogUtil.logEventNonBlocking(
            context,
            "push_not_delivered",
            mapOf(
                "type" to "message_event",
                "channel" to "push-notification",
                "campaign_id" to campaignId,
                "notifly_message_id" to notiflyMessageId,
                "reason" to "missing POST_NOTIFICATIONS permission",
            ),
            listOf(),
            true,
        )
    }

    private suspend fun showPushNotification(
        context: Context,
        pushNotification: IPushNotification,
        wasAppInForeground: Boolean,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Logger.w("POST_NOTIFICATIONS permission is not granted. Notification will not be shown.")
                logPushNotDelivered(context, pushNotification)
                return
            }
        }
        val body = pushNotification.body
        val title = pushNotification.title
        val notificationId = pushNotification.androidNotificationId
        val notiflyMessageId = pushNotification.notiflyMessageId
        val imageUrl = pushNotification.imageUrl
        val bitmap = loadImage(imageUrl)

        val notificationOpenIntent =
            Intent(context, NotificationOpenedActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra("notification", pushNotification)
                putExtra("was_app_in_foreground", wasAppInForeground)
            }

        requestCodeCounter++
        val uniqueRequestCode =
            notiflyMessageId.hashCode().let {
                if (it == 0) requestCodeCounter else it
            }

        val pendingIntent =
            PendingIntent.getActivity(
                context,
                uniqueRequestCode,
                notificationOpenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotiflyNotificationChannelUtil.createNotificationChannels(context)
        }

        val channelId =
            NotiflyNotificationChannelUtil.getNotificationChannelId(
                pushNotification.importance,
                pushNotification.disableBadge,
            )
        val priority = NotiflyNotificationChannelUtil.getSystemPriority(pushNotification.importance)
        val notificationIcon = getNotificationIcon(context)

        val builder =
            NotificationCompat
                .Builder(context, channelId)
                .setSmallIcon(notificationIcon)
                .setContentTitle(title)
                .setContentText(body)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(priority)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        // 광고 푸시 여부 = unsubscribe_url 커스텀 데이터 보유.
        val unsubscribeUrl = pushNotification.customData[UNSUBSCRIBE_URL_DATA_KEY]
        val isAdPush = !unsubscribeUrl.isNullOrBlank()

        // 일반 푸시 스타일은 고객 인터셉터(applyPostBuild)가 덮어쓸 수 있도록 그 전에 적용한다.
        if (!isAdPush) {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(body))
            if (bitmap != null) {
                builder.setStyle(
                    NotificationCompat
                        .BigPictureStyle()
                        .bigPicture(bitmap)
                        .bigLargeIcon(null as Bitmap?),
                )
            }
        }

        PushNotificationManager.applyPostBuild(builder, pushNotification)

        // 광고 푸시: 수신거부 노출은 규정상 필수이므로, 고객 인터셉터(applyPostBuild) 이후에
        // 커스텀 스타일을 적용해 덮어쓰기되지 않게 한다.
        // (setColor/setLargeIcon 등 비-스타일 커스터마이즈는 그대로 보존됨)
        if (isAdPush) {
            val unsubscribeIntent =
                Intent(context, NotificationOpenedActivity::class.java).apply {
                    // 본문 클릭 PendingIntent 와 구분되도록 고유 action 을 지정한다.
                    action = "tech.notifly.push.UNSUBSCRIBE.$notificationId"
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra("notification", pushNotification)
                    putExtra("was_app_in_foreground", wasAppInForeground)
                    putExtra(NotificationOpenedActivity.EXTRA_UNSUBSCRIBE_URL, unsubscribeUrl)
                }
            val unsubscribePendingIntent =
                PendingIntent.getActivity(
                    context,
                    uniqueRequestCode + 1,
                    unsubscribeIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )

            val bigView = RemoteViews(context.packageName, R.layout.notifly_notification_ad_expanded)
            bigView.setTextViewText(R.id.notifly_title, title)
            bigView.setTextViewText(R.id.notifly_body, body)
            applyAdPushNotificationColors(bigView)
            if (bitmap != null) {
                bigView.setImageViewBitmap(R.id.notifly_image, bitmap)
                bigView.setViewVisibility(R.id.notifly_image, View.VISIBLE)
            } else {
                bigView.setViewVisibility(R.id.notifly_image, View.GONE)
            }
            bigView.setOnClickPendingIntent(R.id.notifly_unsubscribe_row, unsubscribePendingIntent)

            builder.setStyle(NotificationCompat.DecoratedCustomViewStyle())
            builder.setCustomBigContentView(bigView)
        }

        val notification = builder.build()
        Logger.d("FCMBroadcastReceiver notification: $notification")

        // Show the notification
        NotificationManagerCompat.from(context).notify(notificationId, notification)
        logPushDelivered(context, pushNotification, wasAppInForeground)
    }

    private fun getNotificationIcon(context: Context): Int {
        val res = context.resources
        val packageName = context.packageName
        val notificationIconName = "ic_stat_notifly_default"

        @SuppressLint("ResourceType")
        val notificationIconResId =
            res.getIdentifier(notificationIconName, "drawable", packageName)
        return if (notificationIconResId != 0) {
            notificationIconResId
        } else {
            // return launcher icon
            val appInfo =
                context.packageManager.getApplicationInfo(
                    context.packageName,
                    PackageManager.GET_META_DATA,
                )
            val launcherIcon = appInfo.icon
            if (launcherIcon != 0) {
                launcherIcon
            } else {
                R.drawable.baseline_notifications_24 // bell icon
            }
        }
    }

    private fun applyAdPushNotificationColors(remoteViews: RemoteViews) {
        val isNightMode =
            Resources.getSystem().configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES
        val primaryTextColor = if (isNightMode) Color.WHITE else Color.rgb(33, 33, 33)
        val secondaryTextColor = if (isNightMode) Color.rgb(224, 224, 224) else Color.rgb(97, 97, 97)
        val dividerColor = if (isNightMode) Color.WHITE else Color.BLACK

        remoteViews.setTextColor(R.id.notifly_title, primaryTextColor)
        remoteViews.setTextColor(R.id.notifly_body, secondaryTextColor)
        remoteViews.setTextColor(R.id.notifly_unsubscribe_text, secondaryTextColor)
        remoteViews.setInt(R.id.notifly_divider, "setBackgroundColor", dividerColor)
        remoteViews.setInt(R.id.notifly_unsubscribe_icon, "setColorFilter", secondaryTextColor)
        remoteViews.setInt(R.id.notifly_unsubscribe_arrow, "setColorFilter", secondaryTextColor)
    }

    private suspend fun loadImage(src: String?): Bitmap? =
        withContext(Dispatchers.IO) {
            if (src == null) return@withContext null
            try {
                val url = URL(src)
                val connection =
                    url.openConnection().apply {
                        connectTimeout = CONNECT_TIMEOUT_MS.toInt()
                        readTimeout = READ_TIMEOUT_MS.toInt()
                    }
                connection.getInputStream().use { inputStream ->
                    BitmapFactory.decodeStream(inputStream)
                }
            } catch (e: ConnectException) {
                Logger.w("Connection error", e)
                null
            } catch (e: SocketTimeoutException) {
                Logger.w("Read timeout", e)
                null
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

    private fun setSuccessfulResultCode() {
        if (isOrderedBroadcast) {
            resultCode = Activity.RESULT_OK
        }
    }
}
