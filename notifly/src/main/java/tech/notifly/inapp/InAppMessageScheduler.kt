package tech.notifly.inapp

import android.app.Activity
import android.content.Context
import android.content.Intent
import tech.notifly.application.IApplicationLifecycleHandler
import tech.notifly.application.IApplicationService
import tech.notifly.inapp.models.Campaign
import tech.notifly.services.NotiflyServiceProvider
import tech.notifly.utils.Logger
import java.util.UUID

object InAppMessageScheduler : IApplicationLifecycleHandler {
    private val pendingCampaigns = mutableListOf<Campaign>()

    @Volatile
    private var isRegistered = false

    private fun ensureRegistered() {
        if (!isRegistered) {
            synchronized(this) {
                if (!isRegistered) {
                    try {
                        val applicationService = NotiflyServiceProvider.getService<IApplicationService>()
                        applicationService.addApplicationLifecycleHandler(this)
                        isRegistered = true
                        Logger.d("InAppMessageScheduler: Registered lifecycle handler")
                    } catch (e: Exception) {
                        Logger.e("InAppMessageScheduler: Failed to register lifecycle handler", e)
                    }
                }
            }
        }
    }

    fun schedule(
        context: Context,
        campaign: Campaign,
    ) {
        ensureRegistered()

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

    private fun show(
        context: Context,
        campaign: Campaign,
    ) {
        if (NotiflyInAppMessageActivity.isActive) {
            Logger.d("NotiflyInAppMessageActivity is already active")
            return
        }

        val applicationService = NotiflyServiceProvider.getService<IApplicationService>()
        val currentActivity = applicationService.current

        if (currentActivity == null) {
            Logger.d("Notifly: No current Activity, deferring in-app message (campaign: ${campaign.id})")
            synchronized(pendingCampaigns) {
                pendingCampaigns.add(campaign)
            }
            return
        }

        showInternal(currentActivity, campaign)
    }

    private fun showInternal(
        activity: Activity,
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

        val intent =
            Intent(activity, NotiflyInAppMessageActivity::class.java).apply {
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
            }

        Logger.d("Notifly: Showing in-app message from Activity context (campaign: ${campaign.id})")
        activity.startActivity(intent)
    }

    override fun onFocus(first: Boolean) {
        val applicationService = NotiflyServiceProvider.getService<IApplicationService>()
        val currentActivity = applicationService.current

        if (currentActivity != null) {
            synchronized(pendingCampaigns) {
                if (pendingCampaigns.isNotEmpty()) {
                    Logger.d("Notifly: Flushing ${pendingCampaigns.size} pending in-app message(s)")
                    val toShow = pendingCampaigns.toList()
                    pendingCampaigns.clear()

                    toShow.forEach { campaign ->
                        showInternal(currentActivity, campaign)
                    }
                }
            }
        }
    }

    override fun onUnfocused() {
        // No-op
    }
}
