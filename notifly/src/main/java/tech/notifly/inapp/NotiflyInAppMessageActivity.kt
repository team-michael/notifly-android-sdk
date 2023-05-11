package tech.notifly.inapp

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.ConstraintLayout
import org.json.JSONObject
import tech.notifly.Notifly
import tech.notifly.R
import tech.notifly.utils.NotiflyLogUtil
import kotlin.math.roundToInt


class NotiflyInAppMessageActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(Notifly.TAG, "NotiflyInAppMessageActivity.onCreate")

        setContentView(R.layout.activity_notifly_in_app_message)

        val webView: WebView = findViewById(R.id.webView)
        webView.settings.javaScriptEnabled = true
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                // Inject JavaScript after page loaded, otherwise it will not be able to find the button trigger
                val injectedJavaScript = """
                    console.log('Injected JavaScript is running');
                    const button_trigger = document.getElementById('notifly-button-trigger');
                    button_trigger.addEventListener('click', function(event){
                        if (!event.notifly_button_click_type) return;
                        window.Android.postMessage(JSON.stringify({
                            type: event.notifly_button_click_type,
                            button_name: event.notifly_button_name,
                            link: event.notifly_button_click_link ?? null,
                        }));
                    });
                """.trimIndent()

                webView.evaluateJavascript(injectedJavaScript, null)
            }
        }
        webView.webChromeClient = WebChromeClient()
        webView.addJavascriptInterface(
            InAppMessageJSInterface(
                this,
                webView,
            ),
            "Android"
        )

        val intent = intent
        val campaignId = intent.getStringExtra("in_app_message_campaign_id")!!
        val url = intent.getStringExtra("in_app_message_url")
        if (url == null) {
            Log.e(Notifly.TAG, "Error parsing in app message url")
            return
        }
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

        val density = getDensity()
        Log.d(Notifly.TAG, "density: $density")

        val (screenWidth, screenHeight) = InAppMessageUtils.getScreenWidthAndHeight(this, density)
        val (widthDp, heightDp) = InAppMessageUtils.getViewDimensions(
            modalProperties,
            screenWidth,
            screenHeight,
        )
        Log.d(Notifly.TAG, "screenWidth: $screenWidth, screenHeight: $screenHeight")
        Log.d(Notifly.TAG, "In-app message widthDp: $widthDp, heightDp: $heightDp")

        modalProperties?.let { properties ->
            val position = properties.optString("position", "full")
            Log.d(Notifly.TAG, "In-app message position: $position")
            setPositionAndSize(webView, widthDp, heightDp, density, position)
        }

        webView.loadUrl(url)

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
        )
    }

    private fun getDensity(): Float {
        return resources.displayMetrics.density
    }

    private fun setPositionAndSize(
        webView: WebView,
        widthDp: Float,
        heightDp: Float,
        density: Float,
        position: String
    ) {

        val layoutParams = ConstraintLayout.LayoutParams(
            (widthDp * density).roundToInt(),
            (heightDp * density).roundToInt()
        )

        when (position) {
            "top" -> {
                layoutParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                layoutParams.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                layoutParams.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            }

            "bottom" -> {
                layoutParams.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                layoutParams.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                layoutParams.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            }

            "left" -> {
                layoutParams.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                layoutParams.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                layoutParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            }

            "right" -> {
                layoutParams.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                layoutParams.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                layoutParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            }

            else -> { // default to center. same as full.
                layoutParams.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                layoutParams.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                layoutParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                layoutParams.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            }

        }

        webView.layoutParams = layoutParams
    }


    private class InAppMessageJSInterface(
        private val context: Context,
        private val webView: WebView,
    ) {
        @JavascriptInterface
        fun postMessage(json: String) {
            Log.d(Notifly.TAG, "In-app message postMessage: $json")
            val data = JSONObject(json)
            val type = data.getString("type")
            val buttonName = data.getString("button_name")
            val link = data.optString("link", null)
            handleButtonClick(type, buttonName, link)
        }

        fun handleButtonClick(type: String, buttonName: String, link: String?) {
            when (type) {
                "close" -> {
                    Log.d(Notifly.TAG, "In-app message close button clicked")
                    (context as Activity).finish()
                    // TODO: log close_button_click event
                }

                "main_button" -> {
                    Log.d(Notifly.TAG, "In-app message main button clicked")
                    // TODO: log main_button_click event
                    if (link != null && link != "null") {
                        Log.d(Notifly.TAG, "In-app message main button link: link")
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
                        context.startActivity(intent)
                    }
                    (context as Activity).finish()
                }

                "hide_in_app_message" -> {
                    Log.d(Notifly.TAG, "In-app message hide button clicked")
                    (context as Activity).finish()
                    // TODO: handle hide_in_app_message
                    // TODO: log hide_in_app_message_button_click event
                }

                "survey_submit_button" -> {
                    Log.d(Notifly.TAG, "In-app message survey submit button clicked")
                    (context as Activity).finish()
                    // TODO: handle survey_submit_button
                    // TODO: log survey_submit_button_click event
                }
            }
        }
    }
}
