package tech.notifly.inapp

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import tech.notifly.inapp.models.Campaign
import tech.notifly.utils.Logger
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object InAppMessageScheduler {
    private val handler = Handler(Looper.getMainLooper())
    private val scheduledCampaigns = ConcurrentHashMap<String, Runnable>()
    private val lock = Any()

    fun schedule(
        context: Context,
        campaign: Campaign,
    ) {
        val appContext = context.applicationContext
        val delay = campaign.delay ?: 0
        if (delay > 0) {
            synchronized(lock) {
                // Cancel existing timer for same campaign to prevent ghost messages
                scheduledCampaigns.remove(campaign.id)?.let { handler.removeCallbacks(it) }

                val runnable =
                    object : Runnable {
                        override fun run() {
                            synchronized(lock) {
                                if (scheduledCampaigns[campaign.id] !== this) return
                                scheduledCampaigns.remove(campaign.id)
                            }
                            show(appContext, campaign)
                        }
                    }
                scheduledCampaigns[campaign.id] = runnable
                handler.postDelayed(runnable, delay * 1000L)
            }
            Logger.d("[Notifly] Scheduled campaign: ${campaign.id} with delay: ${delay}s")
        } else {
            show(appContext, campaign)
        }
    }

    fun getScheduledCampaignIds(): List<String> = scheduledCampaigns.keys().toList()

    fun deschedule(campaignId: String) {
        synchronized(lock) {
            scheduledCampaigns.remove(campaignId)?.let { handler.removeCallbacks(it) }
        }
        Logger.d("[Notifly] Descheduled campaign: $campaignId")
    }

    fun descheduleAll() {
        synchronized(lock) {
            scheduledCampaigns.forEach { (_, runnable) -> handler.removeCallbacks(runnable) }
            scheduledCampaigns.clear()
        }
        Logger.d("[Notifly] Descheduled all campaigns")
    }

    private fun show(
        context: Context,
        campaign: Campaign,
    ) {
        if (NotiflyInAppMessageActivity.isActive) {
            Logger.d("NotiflyInAppMessageActivity is already active")
            return
        }

        val campaignId = campaign.id
        val url = campaign.message.url
        val modalProperties = campaign.message.modalProperties
        val messageId = UUID.randomUUID().toString().replace("-", "")

        context.startActivity(
            Intent(
                context,
                NotiflyInAppMessageActivity::class.java,
            ).apply {
                putExtra("in_app_message_campaign_id", campaignId)
                putExtra("in_app_message_url", url)
                putExtra("notifly_message_id", messageId)
                putExtra("modal_properties", modalProperties)
                putExtra("campaign_re_eligibility_specified", campaign.reEligibleCondition != null)
                if (campaign.reEligibleCondition != null) {
                    putExtra("campaign_re_eligible_unit", campaign.reEligibleCondition.unit.name)
                    putExtra(
                        "campaign_re_eligible_duration",
                        campaign.reEligibleCondition.duration,
                    )
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }
}
