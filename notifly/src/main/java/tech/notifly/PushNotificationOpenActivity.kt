package tech.notifly

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import tech.notifly.utils.NotiflyLogUtil

class PushNotificationOpenActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val url = intent.getStringExtra("url")
        val campaignId = intent.getStringExtra("campaign_id")
        val notiflyMessageId = intent.getStringExtra("notifly_message_id")
        val wasAppInForeground = intent.getBooleanExtra("was_app_in_foreground", false)

        NotiflyLogUtil.logEvent(
            this,
            "push_click",
            mapOf(
                "type" to "message_event",
                "channel" to "push-notification",
                "campaign_id" to campaignId,
                "notifly_message_id" to notiflyMessageId,
                "status" to if (wasAppInForeground) "foreground" else "background"
            ),
            listOf(),
            true
        )

        try {
            // Open the URL or launch the app
            if (url != null) {
                val urlIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).applyIntentCommons()
                startActivity(urlIntent)
            } else {
                packageManager.getLaunchIntentForPackage(packageName)?.applyIntentCommons()?.let {
                    startActivity(it)
                }
            }
        } catch (e: Exception) {
            Logger.w("Failed to open URL or launch app", e)
        } finally {
            finish()
        }
    }

    private fun Intent.applyIntentCommons(): Intent {
        return this.apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
