package tech.notifly

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import tech.notifly.inapp.NotiflyInAppMessageActivity
import tech.notifly.storage.NotiflyStorage
import tech.notifly.storage.NotiflyStorageItem
import tech.notifly.utils.N.KEY_EXTERNAL_USER_ID
import tech.notifly.utils.NotiflyUserUtil


object Notifly {
    internal const val TAG = "Notifly"
    internal const val NOTIFICATION_CHANNEL_ID = "NotiflyNotificationChannelId"

    fun setUserId(
        context: Context,
        userId: String,
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (userId.isNullOrEmpty()) {
                    NotiflyUserUtil.removeUserId(context)
                } else {
                    val params = mapOf(
                        KEY_EXTERNAL_USER_ID to userId
                    )
                    NotiflyUserUtil.setUserProperties(context, params)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Notifly setUserId failed", e)
            }
        }
    }

    fun initialize(
        context: Context,
        projectId: String,
        username: String,
        password: String,
        useCustomClickHandler: Boolean = false,
    ) {
        try {
            // Set Required Properties from User
            NotiflyStorage.put(context, NotiflyStorageItem.PROJECT_ID, projectId)
            NotiflyStorage.put(context, NotiflyStorageItem.USERNAME, username)
            NotiflyStorage.put(context, NotiflyStorageItem.PASSWORD, password)

            // Show In-App Message
            val inAppMessageCampaignId =
                NotiflyStorage.get(context, NotiflyStorageItem.IN_APP_MESSAGE_CAMPAIGN_ID)
            val inAppMessageUrl = NotiflyStorage.get(context, NotiflyStorageItem.IN_APP_MESSAGE_URL)
            if (inAppMessageCampaignId.isNotBlank() && inAppMessageUrl.isNotBlank()) {
                context.startActivity(
                    Intent(
                        context,
                        NotiflyInAppMessageActivity::class.java
                    ).apply {
                        putExtra("in_app_message_campaign_id", inAppMessageCampaignId)
                        putExtra("in_app_message_url", inAppMessageUrl)
                    })
            }

            // Start Session
            NotiflyUserUtil.sessionStart(context)
        } catch (e: Exception) {
            Log.e(TAG, "Notifly initialization failed:", e)
        }
    }

    // TODO: replace with FCMBroadcastReceiver
    fun handleInAppMessage(context: Context, message: RemoteMessage): Boolean {
        Log.d(TAG, "handleInAppMessage(${message.data})")
        return try {
            val isInAppMessage = message.data["notifly_message_type"] == "in-app-message"
            Log.d(TAG, "isInAppMessage: $isInAppMessage")
            if (isInAppMessage) {
                message.data["url"]?.let { url ->
                    Log.d(TAG, "url: $url")
                    NotiflyStorage.put(context, NotiflyStorageItem.IN_APP_MESSAGE_URL, url)
                }
                message.data["campaign_id"]?.let { campaignId ->
                    Log.d(TAG, "campaign_id: $campaignId")
                    NotiflyStorage.put(
                        context,
                        NotiflyStorageItem.IN_APP_MESSAGE_CAMPAIGN_ID,
                        campaignId
                    )
                }
            }
            isInAppMessage
        } catch (err: Exception) {
            println("[Notifly] In-app message handling failed: $err")
            false
        }
    }

    fun setUserProperties(context: Context, params: Map<String, Any?>) {
        // delegate to NotiflyUserUtil
        CoroutineScope(Dispatchers.IO).launch {
            try {
                NotiflyUserUtil.setUserProperties(context, params)
            } catch (e: Exception) {
                Log.w(TAG, "Notifly setUserProperties failed", e)
            }
        }
    }
}
