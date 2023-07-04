package tech.notifly.inapp.views

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import android.net.Uri
import android.util.AttributeSet
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.constraintlayout.widget.ConstraintLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import tech.notifly.utils.Logger
import tech.notifly.inapp.InAppMessageUtils
import tech.notifly.inapp.models.EventLogData
import tech.notifly.utils.NotiflyLogUtil
import tech.notifly.utils.NotiflyUserUtil
import kotlin.math.roundToInt

class NotiflyWebView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr) {
    private val path = Path()
    private val rect = RectF()
    private var radii: FloatArray? = null

    fun initialize(
        modalProperties: JSONObject?, eventLogData: EventLogData, templateName: String?
    ) {
        this.setLayerType(LAYER_TYPE_HARDWARE, null)
        this.settings.javaScriptEnabled = true
        this.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                evaluateJavascript(javascriptToInject, null)
            }
        }
        this.addJavascriptInterface(
            NotiflyJavascriptInterface(
                context, eventLogData, templateName
            ), JS_INTERFACE_NAME
        )
        this.setViewDimensions(modalProperties, context.resources.displayMetrics.density)
    }

    private fun setViewDimensions(modalProperties: JSONObject?, density: Float) {
        val (screenWidth, screenHeight) = InAppMessageUtils.getScreenWidthAndHeight(
            context as Activity, density
        )
        val (widthDp, heightDp) = InAppMessageUtils.getViewDimensions(
            modalProperties,
            screenWidth,
            screenHeight,
        )

        Logger.d("screenWidth: $screenWidth, screenHeight: $screenHeight")
        Logger.d("In-app message widthDp: $widthDp, heightDp: $heightDp")

        modalProperties?.let { properties ->
            val position = properties.optString("position", "full")
            Logger.d("In-app message position: $position")
            setPositionAndSize(widthDp, heightDp, density, position)
            setBorderRadius(properties, density)
        }
    }

    private fun setPositionAndSize(
        widthDp: Float, heightDp: Float, density: Float, position: String
    ) {

        val layoutParams = ConstraintLayout.LayoutParams(
            (widthDp * density).roundToInt(), (heightDp * density).roundToInt()
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

        this.layoutParams = layoutParams
    }

    private fun setBorderRadius(
        modalProperties: JSONObject, density: Float
    ) {
        val topLeftRadiusDp = modalProperties.optInt("borderTopLeftRadius", 0)
        val topRightRadiusDp = modalProperties.optInt("borderTopRightRadius", 0)
        val bottomLeftRadiusDp = modalProperties.optInt("borderBottomLeftRadius", 0)
        val bottomRightRadiusDp = modalProperties.optInt("borderBottomRightRadius", 0)
        if (topLeftRadiusDp > 0 || topRightRadiusDp > 0 || bottomLeftRadiusDp > 0 || bottomRightRadiusDp > 0) {
            val topLeftRadiusPx = (topLeftRadiusDp * density).roundToInt()
            val topRightRadiusPx = (topRightRadiusDp * density).roundToInt()
            val bottomLeftRadiusPx = (bottomLeftRadiusDp * density).roundToInt()
            val bottomRightRadiusPx = (bottomRightRadiusDp * density).roundToInt()
            this.radii = floatArrayOf(
                topLeftRadiusPx.toFloat(),
                topLeftRadiusPx.toFloat(),
                topRightRadiusPx.toFloat(),
                topRightRadiusPx.toFloat(),
                bottomRightRadiusPx.toFloat(),
                bottomRightRadiusPx.toFloat(),
                bottomLeftRadiusPx.toFloat(),
                bottomLeftRadiusPx.toFloat()
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (radii != null) {
            rect.set(0f, 0f, width.toFloat(), height.toFloat())
            path.reset()
            path.addRoundRect(rect, radii!!, Path.Direction.CW)
            canvas.clipPath(path)
        }
        super.onDraw(canvas)
    }

    private inner class NotiflyJavascriptInterface(
        private val context: Context, val eventLogData: EventLogData, val templateName: String?
    ) {
        @JavascriptInterface
        @Suppress("unused")
        fun postMessage(json: String) {
            Logger.d("In-app message postMessage: $json")
            val data = JSONObject(json)
            val type = data.getString("type")
            val buttonName = data.getString("button_name")
            val link = data.optString("link", null.toString())
            val extraData = data.optJSONObject("extra_data")
            handleButtonClick(type, buttonName, link, extraData, templateName)
        }

        fun handleButtonClick(
            type: String,
            buttonName: String,
            link: String?,
            extraData: JSONObject?,
            templateName: String?
        ) {
            when (type) {
                "close" -> {
                    Logger.d("In-app message close button clicked")
                    logInAppMessageButtonClick("close_button_click", buttonName)
                    (context as Activity).finish()
                }

                "main_button" -> {
                    Logger.d("In-app message main button clicked")
                    logInAppMessageButtonClick("main_button_click", buttonName)
                    if (link != null && link != "null") {
                        Logger.d("In-app message main button link: link")
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
                        context.startActivity(intent)
                    }
                    (context as Activity).finish()
                }

                "hide_in_app_message" -> {
                    Logger.d("In-app message hide button clicked")
                    logInAppMessageButtonClick("hide_in_app_message_button_click", buttonName)
                    templateName?.let {
                        val key = "hide_in_app_message_$it"
                        CoroutineScope(Dispatchers.IO).launch {
                            NotiflyUserUtil.setUserProperties(context, mapOf(key to true))
                        }
                    }
                    (context as Activity).finish()
                }

                "survey_submit_button" -> {
                    Logger.d("In-app message survey submit button clicked")
                    logInAppMessageButtonClick("survey_submit_button_click", buttonName, extraData)
                    (context as Activity).finish()
                }
            }
        }

        fun logInAppMessageButtonClick(
            eventName: String, buttonName: String, extraData: JSONObject? = null
        ) {
            val eventParams = mutableMapOf<String, Any?>(
                "type" to "message_event",
                "channel" to "in-app-message",
                "button_name" to buttonName,
                "campaign_id" to eventLogData.campaignId,
                "notifly_message_id" to eventLogData.notiflyMessageId,
            )
            if (eventName == "survey_submit_button_click" && extraData != null) {
                eventParams["notifly_extra_data"] = extraData
            }
            NotiflyLogUtil.logEvent(
                context,
                eventName,
                eventParams,
                listOf(),
                true,
            )
        }
    }

    companion object {
        private val javascriptToInject = """
                    const notifly_button_trigger = document.getElementById('notifly-button-trigger');
                    if (notifly_button_trigger) {
                        notifly_button_trigger.addEventListener('click', function(event){
                            if (!event.notifly_button_click_type) return;
                            window.Android.postMessage(JSON.stringify({
                                type: event.notifly_button_click_type,
                                button_name: event.notifly_button_name,
                                link: event.notifly_button_click_link ?? null,
                                extra_data: event.notifly_extra_data ?? null,
                            }));
                        });
                    }
                """.trimIndent()
        private const val JS_INTERFACE_NAME = "Android"
    }
}