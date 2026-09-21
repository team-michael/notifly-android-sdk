package tech.notifly.inapp

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import tech.notifly.application.IApplicationService
import tech.notifly.inapp.models.Campaign
import tech.notifly.kmp.popup.model.PopupRenderInput
import tech.notifly.services.NotiflyServiceProvider
import tech.notifly.storage.NotiflyStorage
import tech.notifly.storage.NotiflyStorageItem
import tech.notifly.utils.Logger
import tech.notifly.utils.NotiflyAuthUtil
import tech.notifly.utils.NotiflyDeviceUtil
import tech.notifly.utils.NotiflyIdUtil
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object InAppMessageScheduler {
    private val handler = Handler(Looper.getMainLooper())
    private val scheduledCampaigns = ConcurrentHashMap<String, Runnable>()
    private val renderScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val renderingCampaigns = ConcurrentHashMap<String, Job>()
    private val renderedHtml = ConcurrentHashMap<String, String>()
    private val lock = Any()

    /** Uses the existing timer for delays and starts an asynchronous request only for SSR popups. */
    @JvmOverloads
    fun schedule(
        context: Context,
        campaign: Campaign,
        eventName: String? = null,
        eventParams: Map<String, Any?> = emptyMap(),
    ) {
        val appContext = context.applicationContext
        val renderJob =
            if (campaign.message.templateRenderingMode == "ssr") {
                createRenderJob(appContext, campaign, eventName, eventParams)
            } else {
                null
            }
        val display: () -> Unit = {
            if (renderJob != null) renderJob.start() else show(appContext, campaign, null)
        }
        val delay = campaign.delay ?: 0
        synchronized(lock) {
            if (delay > 0 || renderJob != null) {
                scheduledCampaigns.remove(campaign.id)?.let { handler.removeCallbacks(it) }
                renderingCampaigns.remove(campaign.id)?.cancel()
            }
            if (renderJob != null) {
                renderingCampaigns[campaign.id] = renderJob
                renderJob.invokeOnCompletion { renderingCampaigns.remove(campaign.id, renderJob) }
            }
            if (delay > 0) {
                val runnable =
                    object : Runnable {
                        override fun run() {
                            synchronized(lock) {
                                if (scheduledCampaigns[campaign.id] !== this) return
                                scheduledCampaigns.remove(campaign.id)
                            }
                            display()
                        }
                    }
                scheduledCampaigns[campaign.id] = runnable
                handler.postDelayed(runnable, delay * 1000L)
                Logger.d("[Notifly] Scheduled campaign: ${campaign.id} with delay: ${delay}s")
                return
            }
        }
        display()
    }

    private fun createRenderJob(
        context: Context,
        campaign: Campaign,
        eventName: String?,
        eventParams: Map<String, Any?>,
    ): Job {
        val externalUserId = NotiflyStorage.get(context, NotiflyStorageItem.EXTERNAL_USER_ID)
        return renderScope.launch(start = CoroutineStart.LAZY) {
            try {
                val html = renderPopup(context, campaign, externalUserId, eventName, eventParams) ?: return@launch
                ensureActive()
                show(context, campaign, html)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Logger.w("[Notifly] Failed to prepare in-app message", error)
            }
        }
    }

    private suspend fun renderPopup(
        context: Context,
        campaign: Campaign,
        externalUserId: String?,
        eventName: String?,
        eventParams: Map<String, Any?>,
    ): String? {
        if (!canPresentRenderedPopup(context, externalUserId)) return null
        val projectId = NotiflyStorage.get(context, NotiflyStorageItem.PROJECT_ID) ?: return null
        val userId = NotiflyAuthUtil.getNotiflyUserId(context)
        val deviceId =
            NotiflyIdUtil.generate(
                NotiflyIdUtil.Namespace.NAMESPACE_DEVICE_ID,
                NotiflyDeviceUtil.getExternalDeviceId(context),
            )
        if (!canPresentRenderedPopup(context, externalUserId)) return null
        val html =
            InAppMessageRenderer(projectId).render(
                PopupRenderInput("ssr", campaign.id, userId, deviceId, eventName, eventParams),
            ) ?: return null
        return html.takeIf {
            canPresentRenderedPopup(context, externalUserId) &&
                NotiflyStorage.get(context, NotiflyStorageItem.PROJECT_ID) == projectId
        }
    }

    private fun canPresentRenderedPopup(
        context: Context,
        externalUserId: String?,
    ): Boolean =
        !InAppMessageManager.disabled &&
            !NotiflyInAppMessageActivity.isActive &&
            NotiflyServiceProvider.getService<IApplicationService>().isInForeground &&
            NotiflyStorage.get(context, NotiflyStorageItem.EXTERNAL_USER_ID) == externalUserId

    /** Transfers HTML in-process instead of placing its potentially large body in an Intent. */
    internal fun consumeRenderedHtml(messageId: String?): String? = messageId?.let { renderedHtml.remove(it) }

    fun getScheduledCampaignIds(): List<String> = synchronized(lock) { (scheduledCampaigns.keys + renderingCampaigns.keys).toList() }

    fun deschedule(campaignId: String) {
        synchronized(lock) {
            scheduledCampaigns.remove(campaignId)?.let { handler.removeCallbacks(it) }
            renderingCampaigns.remove(campaignId)?.cancel()
        }
        Logger.d("[Notifly] Descheduled campaign: $campaignId")
    }

    fun descheduleAll() {
        synchronized(lock) {
            scheduledCampaigns.values.forEach { handler.removeCallbacks(it) }
            scheduledCampaigns.clear()
            renderingCampaigns.values.toList().forEach { it.cancel() }
            renderingCampaigns.clear()
            renderedHtml.clear()
        }
        Logger.d("[Notifly] Descheduled all campaigns")
    }

    private fun show(
        context: Context,
        campaign: Campaign,
        html: String?,
    ) {
        if (NotiflyInAppMessageActivity.isActive) {
            Logger.d("NotiflyInAppMessageActivity is already active")
            return
        }

        val campaignId = campaign.id
        val url = campaign.message.url
        val modalProperties = campaign.message.modalProperties
        val messageId = UUID.randomUUID().toString().replace("-", "")
        if (html != null) renderedHtml[messageId] = html
        try {
            context.startActivity(
                Intent(context, NotiflyInAppMessageActivity::class.java).apply {
                    putExtra("in_app_message_campaign_id", campaignId)
                    putExtra("in_app_message_url", url)
                    putExtra("notifly_message_id", messageId)
                    putExtra("in_app_message_rendered", html != null)
                    putExtra("modal_properties", modalProperties)
                    putExtra("campaign_re_eligibility_specified", campaign.reEligibleCondition != null)
                    if (campaign.reEligibleCondition != null) {
                        putExtra("campaign_re_eligible_unit", campaign.reEligibleCondition.unit.name)
                        putExtra("campaign_re_eligible_duration", campaign.reEligibleCondition.duration)
                    }
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
                },
            )
        } catch (error: Exception) {
            renderedHtml.remove(messageId)
            throw error
        }
    }
}
