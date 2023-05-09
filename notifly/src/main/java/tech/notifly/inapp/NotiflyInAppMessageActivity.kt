package tech.notifly.inapp

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import org.json.JSONObject
import tech.notifly.Notifly
import tech.notifly.R
import tech.notifly.utils.NotiflyLogUtil
import kotlin.math.roundToInt

class NotiflyInAppMessageActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notifly_in_app_message)
        Log.d(Notifly.TAG, "NotiflyInAppMessageActivity.onCreate")

        val webView: WebView = findViewById(R.id.webView)
        val intent = intent
        val campaignId = intent.getStringExtra("in_app_message_campaign_id")!!
        val url = intent.getStringExtra("in_app_message_url")
        val notiflyMessageId = intent.getStringExtra("notifly_message_id")
        val modalPropertiesString = intent.getStringExtra("modal_properties")
        val modalProperties = try {
            if (modalPropertiesString != null) {
                JSONObject(modalPropertiesString)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(Notifly.TAG, "Error parsing modal properties of the in app message", e)
            null
        }

        val (screenWidth, screenHeight) = InAppMessageUtils.getScreenWidthAndHeight(this)
        val (widthPx, heightPx) = InAppMessageUtils.getViewDimensions(
            modalProperties,
            screenWidth,
            screenHeight
        )
        Log.d(Notifly.TAG, "screenWidth: $screenWidth, screenHeight: $screenHeight")
        Log.d(Notifly.TAG, "In-app message widthPx: $widthPx, heightPx: $heightPx")

        modalProperties?.let { properties ->
            val position = properties.optString("position", "full")
            setPositionAndSize(webView, widthPx, heightPx, position)
        }

        url?.let {
            webView.loadUrl(it)
            NotiflyLogUtil.logEvent(
                this,
                "in_app_message_show",
                mapOf(
                    "type" to "message_event",
                    "channel" to "in-app-message",
                    "campaign" to campaignId,
                    "notifly_message_id" to notiflyMessageId,
                ),
                listOf(),
                true
            ) // logging in app messaging delivered
        }
    }

    private fun setPositionAndSize(
        webView: WebView,
        widthPx: Float,
        heightPx: Float,
        position: String
    ) {
        val layoutParams = FrameLayout.LayoutParams(widthPx.roundToInt(), heightPx.roundToInt())

        when (position) {
            "top" -> layoutParams.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            "bottom" -> layoutParams.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            "left" -> layoutParams.gravity = Gravity.START or Gravity.CENTER_VERTICAL
            "right" -> layoutParams.gravity = Gravity.END or Gravity.CENTER_VERTICAL
            "center" -> layoutParams.gravity = Gravity.CENTER
            else -> { // Default to full screen
                layoutParams.width = FrameLayout.LayoutParams.MATCH_PARENT
                layoutParams.height = FrameLayout.LayoutParams.MATCH_PARENT
            }
        }

        webView.layoutParams = layoutParams
    }


    private class InAppMessageJSInterface(
        private val context: Context,
        private val webView: WebView,
    ) {
        @JavascriptInterface
        fun handleButtonClick(type: String, buttonName: String, link: String?) {
            when (type) {
                "close" -> {
                    (context as Activity).runOnUiThread {
                        (webView.parent.parent as? AlertDialog)?.dismiss()
                    }
                }

                "main_button" -> {
                    if (link != null) {
                        (context as Activity).runOnUiThread {
                            (webView.parent.parent as? AlertDialog)?.dismiss()
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(link)).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            )
                        }
                    }
                }
                // Handle other button types if necessary
            }
        }
    }
}
