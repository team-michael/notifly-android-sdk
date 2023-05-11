package tech.notifly.inapp

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Path
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewOutlineProvider
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.RequiresApi
import androidx.constraintlayout.widget.ConstraintLayout
import org.json.JSONObject
import tech.notifly.Notifly
import tech.notifly.R
import tech.notifly.utils.NotiflyLogUtil
import kotlin.math.roundToInt


class NotiflyInAppMessageActivity : Activity() {
    companion object {
        var isActivityRunning = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(Notifly.TAG, "NotiflyInAppMessageActivity.onCreate")
        if (isActivityRunning) {
            Log.d(
                Notifly.TAG,
                "NotiflyInAppMessageActivity is already running, ignoring onCreate."
            )
            finish()
            return
        }

        isActivityRunning = true

        setContentView(R.layout.activity_notifly_in_app_message)
        val webView: WebView = findViewById(R.id.webView)
        setupWebView(webView)

        val intent = intent
        val (url, modalProperties) = handleIntent(intent)
        if (url == null) {
            return
        }

        val density = getDensity()
        Log.d(Notifly.TAG, "density: $density")

        handleViewDimensions(webView, modalProperties, density)

        webView.loadUrl(url)
        setupTouchInterceptorLayout(modalProperties?.optDouble("backdrop_opacity", 0.0))

        val campaignId = intent.getStringExtra("in_app_message_campaign_id")!!
        val notiflyMessageId = intent.getStringExtra("notifly_message_id")
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

    override fun onDestroy() {
        super.onDestroy()

        // Clear the flag to indicate the activity is no longer running
        isActivityRunning = false
    }

    override fun finish() {
        super.finish()
        // Remove the animation when the activity gets destroyed
        overridePendingTransition(0, 0)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView(webView: WebView) {
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
            ),
            "Android"
        )
    }

    private fun setupTouchInterceptorLayout(backdropOpacity: Double?) {
        val touchInterceptorLayout =
            findViewById<TouchInterceptorLayout>(R.id.touch_interceptor_layout)

        val cappedOpacity = backdropOpacity?.coerceIn(0.0, 1.0) ?: 0.0
        val backgroundColor = Color.argb(
            (cappedOpacity * 255).roundToInt(),
            0,
            0,
            0
        )
        touchInterceptorLayout.setBackgroundColor(backgroundColor)
        touchInterceptorLayout.onTouchOutsideWebView = {
            finish()
        }
    }

    private fun handleIntent(intent: Intent): Pair<String?, JSONObject?> {
        val url = intent.getStringExtra("in_app_message_url")
        if (url == null) {
            Log.e(Notifly.TAG, "Error parsing in app message url")
            return Pair(null, null)
        }

        val modalProperties =
            intent.getStringExtra("modal_properties")?.let { modalPropertiesString ->
                try {
                    JSONObject(modalPropertiesString)
                } catch (e: Exception) {
                    Log.e(Notifly.TAG, "Error parsing modal properties of the in app message", e)
                    null
                }
            }

        return Pair(url, modalProperties)
    }

    private fun handleViewDimensions(
        webView: WebView,
        modalProperties: JSONObject?,
        density: Float
    ) {
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
            setBorderRadius(webView, properties, density)
        }
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

    private fun setBorderRadius(
        webView: WebView,
        modalProperties: JSONObject,
        density: Float
    ) {
        val topLeftRadiusDp = modalProperties.optInt("borderTopLeftRadius", 0)
        val topRightRadiusDp = modalProperties.optInt("borderTopRightRadius", 0)
        val bottomLeftRadiusDp = modalProperties.optInt("borderBottomLeftRadius", 0)
        val bottomRightRadiusDp = modalProperties.optInt("borderBottomRightRadius", 0)
        if (topLeftRadiusDp > 0 || topRightRadiusDp > 0 || bottomLeftRadiusDp > 0 || bottomRightRadiusDp > 0) {
            webView.clipToOutline = true
            webView.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View?, outline: Outline?) {
                    view?.let {
                        val topLeftRadiusPx = (topLeftRadiusDp * density).roundToInt()
                        val topRightRadiusPx = (topRightRadiusDp * density).roundToInt()
                        val bottomLeftRadiusPx = (bottomLeftRadiusDp * density).roundToInt()
                        val bottomRightRadiusPx = (bottomRightRadiusDp * density).roundToInt()
                        val path = Path()
                        path.addRoundRect(
                            0f, 0f, it.width.toFloat(), it.height.toFloat(),
                            floatArrayOf(
                                topLeftRadiusPx.toFloat(), topLeftRadiusPx.toFloat(),
                                topRightRadiusPx.toFloat(), topRightRadiusPx.toFloat(),
                                bottomRightRadiusPx.toFloat(), bottomRightRadiusPx.toFloat(),
                                bottomLeftRadiusPx.toFloat(), bottomLeftRadiusPx.toFloat()
                            ),
                            Path.Direction.CW
                        )

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            outline?.setPath(path)
                        } else {
                            outline?.setConvexPath(path)
                        }
                    }
                }
            }
        }
    }


    private class InAppMessageJSInterface(
        private val context: Context,
    ) {
        @JavascriptInterface
        @Suppress("unused")
        fun postMessage(json: String) {
            Log.d(Notifly.TAG, "In-app message postMessage: $json")
            val data = JSONObject(json)
            val type = data.getString("type")
            val buttonName = data.getString("button_name")
            val link = data.optString("link", null.toString())
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
