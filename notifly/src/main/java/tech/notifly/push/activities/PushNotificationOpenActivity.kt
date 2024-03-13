package tech.notifly.push.activities

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import tech.notifly.push.PushNotificationManager
import tech.notifly.push.interfaces.IPushNotification
import tech.notifly.utils.Logger
import tech.notifly.utils.NotiflyLogUtil

class PushNotificationOpenActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        Logger.d("PushNotificationOpenActivity onCreate")
        super.onCreate(savedInstanceState)
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        Logger.d("PushNotificationOpenActivity onNewIntent")
        super.onNewIntent(intent)
        intent?.let { handleIntent(it) }
    }

    private fun handleIntent(intent: Intent) {
        Logger.d("PushNotificationOpenActivity handleIntent: $intent")

        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra("notification", IPushNotification::class.java)
        } else {
            @Suppress("DEPRECATION") intent.getSerializableExtra("notification") as? IPushNotification
        }

        if (notification == null) {
            Logger.e("PushNotificationOpenActivity: No notification found in intent")
            finish()
            return
        }

        val url = notification.url
        val campaignId = notification.campaignId
        val notiflyMessageId = notification.notiflyMessageId
        val wasAppInForeground = intent.getBooleanExtra("was_app_in_foreground", false)

        // Log the push click event
        NotiflyLogUtil.logEventSync(
            this, "push_click", mapOf(
                "type" to "message_event",
                "channel" to "push-notification",
                "campaign_id" to campaignId,
                "notifly_message_id" to notiflyMessageId,
                "status" to if (wasAppInForeground) "foreground" else "background"
            ), listOf(), true
        )

        // Fire callbacks for push click event
        PushNotificationManager.notificationOpened(notification)

        try {
            // Open the URL or launch the app
            if (url != null) {
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(this)
                }
            } else {
                packageManager.getLaunchIntentForPackage(packageName)?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                    startActivity(this)
                }
            }
        } catch (e: Exception) {
            Logger.w("Failed to open URL or launch app", e)
        } finally {
            finish()
        }
    }
}
