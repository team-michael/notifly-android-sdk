package tech.notifly.inapp

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import org.json.JSONObject
import tech.notifly.R
import tech.notifly.command.CommandDispatcher
import tech.notifly.command.models.TrackEventCommand
import tech.notifly.command.models.TrackEventPayload
import tech.notifly.inapp.models.EventLogData
import tech.notifly.inapp.models.ReEligibleConditionUnitType
import tech.notifly.inapp.views.NotiflyWebView
import tech.notifly.inapp.views.TouchInterceptorLayout
import tech.notifly.utils.Logger
import tech.notifly.utils.NotiflyTimerUtil
import kotlin.math.roundToInt


class NotiflyInAppMessageActivity : Activity() {
    companion object {
        private const val DEFAULT_BACKGROUND_OPACITY = 0.2

        @Volatile
        private var isActivityRunning = false
        val isActive: Boolean
            get() = isActivityRunning
    }

    private var mNotiflyWebView: NotiflyWebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isActivityRunning) {
            Logger.d("NotiflyInAppMessageActivity is already active")
            rejectCreation()
            return
        }

        isActivityRunning = true
        Logger.d("NotiflyInAppMessageActivity.onCreate")

        try {
            mNotiflyWebView?.resumeTimers()
        } catch (e: Exception) {
            Logger.e("Error resuming webview timers", e)
        }

        setContentView(R.layout.activity_notifly_in_app_message)

        val (url, modalProperties) = handleIntent(intent)
        if (url == null) {
            Logger.e("Error parsing in app message url")
            finish()
            return
        }

        val eventLogData = getEventLogData(intent)
        val templateName: String? = modalProperties?.optString("template_name")

        mNotiflyWebView = findViewById<NotiflyWebView>(R.id.webView).apply {
            this.visibility = View.INVISIBLE

            Logger.v("Visibility: ${this.visibility}") // Should be 4 (INVISIBLE)

            initialize(modalProperties, eventLogData, templateName, {
                this@NotiflyInAppMessageActivity.onWebViewLoadedComplete(
                    modalProperties, eventLogData
                )
            }, this@NotiflyInAppMessageActivity::onWebViewLoadedWithError)

            loadUrl(url)
        }
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
    }

    private fun onWebViewLoadedComplete(modalProperties: JSONObject?, eventLogData: EventLogData) {
        if (mNotiflyWebView == null) {
            Logger.e("NotiflyWebView is null! Cannot show in app message")
            finish()
            return
        }

        Logger.v("Webview loaded complete, showing...")
        val shouldInterceptTouchEvent =
            modalProperties?.optBoolean("dismissCTATapped", false) ?: false
        val backgroundOpacity =
            modalProperties?.optDouble("backgroundOpacity", DEFAULT_BACKGROUND_OPACITY)
                ?: DEFAULT_BACKGROUND_OPACITY

        Logger.v("shouldInterceptTouchEvent: $shouldInterceptTouchEvent")
        Logger.v("backgroundOpacity: $backgroundOpacity")
        setupTouchInterceptorLayout(
            shouldInterceptTouchEvent, backgroundOpacity
        )

        Logger.v("Previous visibility: ${mNotiflyWebView!!.visibility}") // Should be 4 (INVISIBLE)
        mNotiflyWebView!!.visibility = View.VISIBLE
        mNotiflyWebView!!.bringToFront()
        Logger.v("Visibility: ${mNotiflyWebView!!.visibility}") // 0

        val eventParams = mutableMapOf<String, Any?>(
            "type" to "message_event",
            "channel" to "in-app-message",
            "campaign_id" to eventLogData.campaignId,
            "notifly_message_id" to eventLogData.notiflyMessageId,
        )

        val campaignHiddenUntil = eventLogData.campaignHiddenUntil
        if (campaignHiddenUntil != null) {
            eventParams["hide_until_data"] = mapOf(
                eventLogData.campaignId to campaignHiddenUntil
            )
            InAppMessageManager.updateHideUntilData(
                eventLogData.campaignId, campaignHiddenUntil
            )
        }

        CommandDispatcher.dispatch(
            TrackEventCommand(
                TrackEventPayload(
                    this, "in_app_message_show", eventParams, listOf(), true
                )
            )
        )
    }

    private fun onWebViewLoadedWithError(errorMessage: String?) {
        Logger.e("Error loading in app message! Error: " + (errorMessage ?: "Unknown error"))
        finish()
    }

    override fun finish() {
        // Clear the flag to indicate the activity is no longer running
        isActivityRunning = false

        super.finish()

        // Remove the animation when the activity gets destroyed
        overridePendingTransition(0, 0)
    }

    private fun rejectCreation() {
        Logger.d("Rejecting creation of NotiflyInAppMessageActivity")
        super.finish()
    }

    private fun setupTouchInterceptorLayout(
        shouldInterceptTouchEvent: Boolean, backgroundOpacity: Double
    ) {
        val touchInterceptorLayout =
            findViewById<TouchInterceptorLayout>(R.id.touch_interceptor_layout)

        touchInterceptorLayout.setBackgroundColor(
            Color.argb(
                (backgroundOpacity.coerceIn(0.0, 1.0) * 255).roundToInt(), 0, 0, 0
            )
        )

        if (shouldInterceptTouchEvent) {
            touchInterceptorLayout.onTouchOutsideWebView = {
                finish()
            }
        }
    }

    private fun handleIntent(intent: Intent): Pair<String?, JSONObject?> {
        val url = intent.getStringExtra("in_app_message_url")
        if (url == null) {
            Logger.e("Error parsing in app message url")
            return Pair(null, null)
        }

        val modalProperties =
            intent.getStringExtra("modal_properties")?.let { modalPropertiesString ->
                try {
                    JSONObject(modalPropertiesString)
                } catch (e: Exception) {
                    Logger.e("Error parsing properties of the in app message", e)
                    null
                }
            }

        return Pair(url, modalProperties)
    }

    private fun getEventLogData(intent: Intent): EventLogData {
        val campaignId = intent.getStringExtra("in_app_message_campaign_id")!!
        val notiflyMessageId = intent.getStringExtra("notifly_message_id")
        val campaignHiddenUntil = getCampaignHiddenUntil(intent)

        return EventLogData(campaignId, notiflyMessageId, campaignHiddenUntil)
    }

    private fun getCampaignHiddenUntil(intent: Intent): Int? {
        try {
            if (!intent.getBooleanExtra(
                    "campaign_re_eligibility_specified", false
                )
            ) {
                return null
            }

            val unit =
                ReEligibleConditionUnitType.valueOf(intent.getStringExtra("campaign_re_eligible_unit")!!)
            val duration = intent.getIntExtra("campaign_re_eligible_duration", Int.MIN_VALUE)
            if (duration == Int.MIN_VALUE) {
                Logger.w("Re-eligibility duration is not specified, omitting...")
                return null
            }

            val now = NotiflyTimerUtil.getTimestampSeconds()
            return when (unit) {
                ReEligibleConditionUnitType.HOUR -> now + duration * 60 * 60
                ReEligibleConditionUnitType.DAY -> now + duration * 60 * 60 * 24
                ReEligibleConditionUnitType.WEEK -> now + duration * 60 * 60 * 24 * 7
                ReEligibleConditionUnitType.MONTH -> now + duration * 60 * 60 * 24 * 30
                ReEligibleConditionUnitType.INFINITE -> -1
            }
        } catch (e: Exception) {
            Logger.e("Error parsing re-eligibility condition", e)
            return null
        }
    }
}
