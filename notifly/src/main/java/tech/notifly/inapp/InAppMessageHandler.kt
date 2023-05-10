package tech.notifly.inapp

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.Log
import android.view.Gravity
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import org.json.JSONObject
import tech.notifly.Notifly
import tech.notifly.R
import tech.notifly.utils.NotiflyLogUtil
import kotlin.math.roundToInt

class InAppMessageHandler {
    fun handleInAppMessage(activity: Activity, intent: Intent) {

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

        val (screenWidth, screenHeight) = InAppMessageUtils.getScreenWidthAndHeight(activity)
        val (widthPx, heightPx) = InAppMessageUtils.getViewDimensions(
            modalProperties,
            screenWidth,
            screenHeight
        )
        Log.d(Notifly.TAG, "screenWidth: $screenWidth, screenHeight: $screenHeight")
        Log.d(Notifly.TAG, "In-app message widthPx: $widthPx, heightPx: $heightPx")

        val position = modalProperties?.optString("position") ?: "full"

        url?.let {
            showWebViewDialog(activity, it, widthPx, heightPx, position)
            NotiflyLogUtil.logEvent(
                activity,
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

    private fun showWebViewDialog(activity: Activity, url: String, widthPx: Float, heightPx: Float, position: String) {
        val webViewDialog = Dialog(activity)
        webViewDialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        webViewDialog.setContentView(R.layout.activity_notifly_in_app_message)

        val webView = webViewDialog.findViewById<WebView>(R.id.webView)
        initWebView(webView)
        setPositionAndSize(webView, widthPx, heightPx, position)
        webView.loadUrl(url)

        webViewDialog.show()
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


    private fun initWebView(webView: WebView) {
        val webSettings = webView.settings
        webSettings.javaScriptEnabled = true
        webSettings.loadWithOverviewMode = true
        webSettings.useWideViewPort = true
        webView.webViewClient = WebViewClient()

        // Set background transparent
        webView.setBackgroundColor(Color.TRANSPARENT)
        webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
    }
}
