package tech.notifly

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import tech.notifly.utils.NotiflyLogUtil
import tech.notifly.utils.NotiflySDKInfoUtil
import tech.notifly.utils.NotiflySdkType

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
            // If react native, send the event to the JS side
            if (NotiflySDKInfoUtil.getSdkType() == NotiflySdkType.REACT_NATIVE) {
                Logger.d("Sending event to react native")
                val eventIntent = Intent("notifly-push-notification").apply {
                    putExtra("url", url)
                    putExtra("campaign_id", campaignId)
                    putExtra("notifly_message_id", notiflyMessageId)
                }
                sendBroadcast(eventIntent)
                return
            }

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
