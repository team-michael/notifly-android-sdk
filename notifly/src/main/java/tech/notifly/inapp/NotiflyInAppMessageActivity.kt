package tech.notifly.inapp

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import org.json.JSONObject
import tech.notifly.R
import tech.notifly.inapp.models.EventLogData
import tech.notifly.inapp.views.NotiflyWebView
import tech.notifly.inapp.views.TouchInterceptorLayout
import tech.notifly.utils.Logger
import tech.notifly.utils.NotiflyLogUtil
import kotlin.math.roundToInt


class NotiflyInAppMessageActivity : Activity() {
    companion object {
        private const val DEFAULT_BACKGROUD_OPACITY = 0.2

        @Volatile
        private var isActivityRunning = false
        val isActive: Boolean
            get() = isActivityRunning
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isActivityRunning) {
            Logger.d("NotiflyInAppMessageActivity is already active")
            rejectCreation()
            return
        }
        isActivityRunning = true
        Logger.d("NotiflyInAppMessageActivity.onCreate")

        setContentView(R.layout.activity_notifly_in_app_message)

        val intent = intent
        val (url, modalProperties) = handleIntent(intent)
        if (url == null) {
            return
        }

        val eventLogData = getEventLogData(intent)
        val templateName: String? = modalProperties?.optString("template_name")

        this.setVisible(false)

        findViewById<NotiflyWebView>(R.id.webView).apply {
            initialize(modalProperties,
                eventLogData,
                templateName,
                { this@NotiflyInAppMessageActivity.onWebViewLoadedComplete(modalProperties, eventLogData) },
                { this@NotiflyInAppMessageActivity.onWebViewLoadedWithError() })
            loadUrl(url)
        }
    }

    private fun onWebViewLoadedComplete(modalProperties: JSONObject?, eventLogData: EventLogData) {
        val shouldInterceptTouchEvent =
            modalProperties?.optBoolean("dismissCTATapped", false) ?: false
        val backgroundOpacity =
            modalProperties?.optDouble("backgroundOpacity", DEFAULT_BACKGROUD_OPACITY)
                ?: DEFAULT_BACKGROUD_OPACITY
        setupTouchInterceptorLayout(
            shouldInterceptTouchEvent, backgroundOpacity
        )

        this.setVisible(true)

        NotiflyLogUtil.logEvent(
            this, "in_app_message_show", mapOf(
                "type" to "message_event",
                "channel" to "in-app-message",
                "campaign_id" to eventLogData.campaignId,
                "notifly_message_id" to eventLogData.notiflyMessageId,
            ), listOf(), true
        )
    }

    private fun onWebViewLoadedWithError() {
        Logger.e("Error loading in app message")
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

        return EventLogData(campaignId, notiflyMessageId)
    }
}
