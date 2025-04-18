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
        Logger.d("NotificationOpenedActivity onCreate called")
        if (!Notifly.initializeWithContext(this)) {
            Logger.w("Notifly initialization failed in onCreate")
            return
        }
        Logger.d("Intent received in onCreate: $intent")
        handleIntent(intent)
        finish()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        Logger.d("NotificationOpenedActivity onNewIntent called")
        if (!Notifly.initializeWithContext(this)) {
            Logger.w("Notifly initialization failed in onNewIntent")
            return
        }
        Logger.d("Intent received in onNewIntent: $intent")
        intent?.let { handleIntent(it) }
        finish()
    }

    private fun handleIntent(intent: Intent) {
        Logger.d("Handling intent in NotificationOpenedActivity: $intent")

        val notification =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getSerializableExtra("notification", IPushNotification::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getSerializableExtra("notification")
                    as? IPushNotification
            }

        if (notification == null) {
            Logger.w("Notification object is null in intent extras")
            finish()
            return
        }

        Logger.d("Extracted notification: $notification")

        val url = notification.url
        val campaignId = notification.campaignId
        val notiflyMessageId = notification.notiflyMessageId
        val wasAppInForeground = intent.getBooleanExtra("was_app_in_foreground", false)

        Logger.d("Notification info - campaignId: $campaignId, notiflyMessageId: $notiflyMessageId, url: $url, wasAppInForeground: $wasAppInForeground")

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
        Logger.d("Logged push_click event")

        // Fire callbacks for push click event
        PushNotificationManager.notificationOpened(notification)
        Logger.d("PushNotificationManager.notificationOpened fired")

        try {
            val applicationService = NotiflyServiceProvider.getService<IApplicationService>()
            Logger.d("Application is in foreground: ${applicationService.isInForeground}")
            if (!applicationService.isInForeground) {
                applicationService.entryState = ApplicationEntryAction.NOTIFICATION_CLICK
                Logger.d("Set application entryState to NOTIFICATION_CLICK")
            }
            // Open the URL or launch the app
            val destinationIntent = getIntent(url)
            Logger.d("Resolved destinationIntent: $destinationIntent")
            if (destinationIntent != null) {
                Logger.d("Starting activity with intent: $destinationIntent")
                startActivity(destinationIntent)
            } else {
                Logger.w("destinationIntent was null, cannot launch")
            }
        } catch (e: Exception) {
            Logger.w("Failed to open URL or launch app", e)
        }
    }

    private fun getIntent(url: String?): Intent? {
        val uri =
            if (url != null) {
                Uri.parse(url.trim { it <= ' ' }).also {
                    Logger.d("Parsed URI from URL: $it")
                }
            } else {
                Logger.d("No URL provided, will try to open app launcher intent")
                null
            }

        return if (uri != null) {
            OSUtil.openURLInBrowserIntent(uri).also {
                Logger.d("Created browser intent: $it")
            }
        } else {
            packageManager.getLaunchIntentForPackage(packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                Logger.d("Created launch intent for package: $this")
            }
        }
    }
}
