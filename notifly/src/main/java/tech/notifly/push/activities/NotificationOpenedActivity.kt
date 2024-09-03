package tech.notifly.push.activities

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import tech.notifly.Notifly
import tech.notifly.application.ApplicationEntryAction
import tech.notifly.application.IApplicationService
import tech.notifly.push.PushNotificationManager
import tech.notifly.push.interfaces.IPushNotification
import tech.notifly.services.NotiflyServiceProvider
import tech.notifly.utils.Logger
import tech.notifly.utils.NotiflyLogUtil
import tech.notifly.utils.OSUtil

class NotificationOpenedActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Notifly.initializeWithContext(this)) {
            return
        }
        handleIntent(intent)
        finish()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (!Notifly.initializeWithContext(this)) {
            return
        }
        intent?.let { handleIntent(it) }
        finish()
    }

    private fun handleIntent(intent: Intent) {
        val notification =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getSerializableExtra("notification", IPushNotification::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getSerializableExtra("notification")
                    as? IPushNotification
            }

        if (notification == null) {
            finish()
            return
        }

        val url = notification.url
        val campaignId = notification.campaignId
        val notiflyMessageId = notification.notiflyMessageId
        val wasAppInForeground = intent.getBooleanExtra("was_app_in_foreground", false)

        // Log the push click event
        NotiflyLogUtil.logEventNonBlocking(
            this,
            "push_click",
            mapOf(
                "type" to "message_event",
                "channel" to "push-notification",
                "campaign_id" to campaignId,
                "notifly_message_id" to notiflyMessageId,
                "status" to if (wasAppInForeground) "foreground" else "background",
            ),
            listOf(),
            true,
        )

        // Fire callbacks for push click event
        PushNotificationManager.notificationOpened(notification)

        try {
            val applicationService = NotiflyServiceProvider.getService<IApplicationService>()
            if (!applicationService.isInForeground) {
                applicationService.entryState = ApplicationEntryAction.NOTIFICATION_CLICK
            }
            // Open the URL or launch the app
            val destinationIntent = getIntent(url)
            if (destinationIntent != null) {
                startActivity(destinationIntent)
            }
        } catch (e: Exception) {
            Logger.w("Failed to open URL or launch app", e)
        }
    }

    private fun getIntent(url: String?): Intent? {
        val uri =
            if (url != null) {
                Uri.parse(url.trim { it <= ' ' })
            } else {
                null
            }

        return if (uri != null) {
            OSUtil.openURLInBrowserIntent(uri)
        } else {
            packageManager.getLaunchIntentForPackage(packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }
}
