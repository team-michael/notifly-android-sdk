package tech.notifly

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import tech.notifly.utils.NotiflyLogUtil
import tech.notifly.utils.OSUtils

class NotificationOpenReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title")
        val body = intent.getStringExtra("body")
        val url = intent.getStringExtra("url")
        val campaignId = intent.getStringExtra("campaign_id")
        val notiflyMessageId = intent.getStringExtra("notifly_message_id")

        val launchIntent = if (url != null) {
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } else {
            context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        launchIntent?.putExtra("title", title)
        launchIntent?.putExtra("body", body)

        // Start the activity
        context.startActivity(launchIntent)

        val isAppInForeground = OSUtils.isAppInForeground(context)

        NotiflyLogUtil.logEvent(
            context,
            "push_click",
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
}
