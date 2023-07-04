package tech.notifly.inapp

import android.content.Context
import android.content.Intent
import tech.notifly.utils.Logger
import tech.notifly.inapp.models.Campaign
import java.util.UUID

object InAppMessageScheduler {
    fun schedule(context: Context, campaign: Campaign) {
        val delay = campaign.delay ?: 0
        if (delay > 0) {
            Thread {
                Thread.sleep(delay * 1000L)
                show(context, campaign)
            }.start()
        } else {
            show(context, campaign)
        }
    }

    private fun show(context: Context, campaign: Campaign) {
        if (NotiflyInAppMessageActivity.isActive) {
            Logger.d("NotiflyInAppMessageActivity is already active")
            return
        }

        val campaignId = campaign.id
        val url = campaign.message.url
        val modalProperties = campaign.message.modalProperties
        val messageId = UUID.randomUUID().toString().replace("-", "")

        context.startActivity(Intent(
            context, NotiflyInAppMessageActivity::class.java
        ).apply {
            putExtra("in_app_message_campaign_id", campaignId)
            putExtra("in_app_message_url", url)
            putExtra("notifly_message_id", messageId)
            putExtra("modal_properties", modalProperties)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}
