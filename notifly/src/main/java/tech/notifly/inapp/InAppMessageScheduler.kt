package tech.notifly.inapp

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tech.notifly.inapp.models.Campaign
import java.util.UUID

object InAppMessageScheduler {
    fun schedule(context: Context, campaign: Campaign) {
        val campaignId = campaign.id
        val url = campaign.message.url
        val modalProperties = campaign.message.modalProperties
        val messageId = UUID.randomUUID().toString().replace("-", "")
        val delay = campaign.delay ?: 0

        val inAppMessageShowIntent = Intent(
            context, NotiflyInAppMessageActivity::class.java
        ).apply {
            putExtra("in_app_message_campaign_id", campaignId)
            putExtra("in_app_message_url", url)
            putExtra("notifly_message_id", messageId)
            putExtra("modal_properties", modalProperties)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        if (delay == 0) {
            context.startActivity(inAppMessageShowIntent)
        } else {
            CoroutineScope(Dispatchers.Main).launch {
                delay(delay * 1000L)
                context.startActivity(inAppMessageShowIntent)
            }
        }
    }
}
