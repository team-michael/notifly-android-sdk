package tech.notifly.push

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
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
        val url = intent.getStringExtra("url")
        val campaignId = intent.getStringExtra("campaign_id")
        val notiflyMessageId = intent.getStringExtra("notifly_message_id")
        val wasAppInForeground = intent.getBooleanExtra("was_app_in_foreground", false)

        NotiflyLogUtil.logEventSync(
            this, "push_click", mapOf(
                "type" to "message_event",
                "channel" to "push-notification",
                "campaign_id" to campaignId,
                "notifly_message_id" to notiflyMessageId,
                "status" to if (wasAppInForeground) "foreground" else "background"
            ), listOf(), true
        )

        try {
            // Open the URL or launch the app
            if (url != null) {
                val urlIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(urlIntent)
            } else {
                val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
                launchIntent?.apply {
                    putExtra("test", "test")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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
