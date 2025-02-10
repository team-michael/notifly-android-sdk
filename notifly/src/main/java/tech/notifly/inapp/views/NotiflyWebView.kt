package tech.notifly.inapp.views

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.util.AttributeSet
import android.webkit.JavascriptInterface
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.constraintlayout.widget.ConstraintLayout
import org.json.JSONObject
import tech.notifly.command.CommandDispatcher
import tech.notifly.command.models.SetUserPropertiesCommand
import tech.notifly.command.models.SetUserPropertiesPayload
import tech.notifly.command.models.TrackEventCommand
import tech.notifly.command.models.TrackEventPayload
import tech.notifly.inapp.InAppMessageUtils
import tech.notifly.inapp.models.EventLogData
import tech.notifly.sdk.NotiflySdkPrefs
import tech.notifly.utils.Logger
import tech.notifly.utils.NotiflyTimerUtil
import tech.notifly.utils.OSUtil
import kotlin.math.roundToInt

class NotiflyWebView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : WebView(context, attrs, defStyleAttr) {
        private val path = Path()
        private val rect = RectF()
        private var radii: FloatArray? = null

        @SuppressLint("SetJavaScriptEnabled")
        fun initialize(
            modalProperties: JSONObject?,
            eventLogData: EventLogData,
            templateName: String?,
            onPageFinishedCallback: () -> Unit,
            onReceivedErrorCallback: (errorMessage: String?) -> Unit,
        ) {
            this.setLayerType(LAYER_TYPE_SOFTWARE, null)
            this.settings.javaScriptEnabled = true

            this.webViewClient =
                object : WebViewClient() {
                    private var pageLoadedSuccessfully = true
                    private var errorMessage: String? = null

                    override fun onPageFinished(
                        view: WebView?,
                        url: String?,
                    ) {
                        super.onPageFinished(view, url)
                        Logger.v("NotiflyWebView.onPageFinished: $url")
                        if (pageLoadedSuccessfully) {
                            Logger.v("NotiflyWebView.onPageFinished: Injecting javascript")
                            evaluateJavascript(javascriptToInject, null)
                            Logger.v(
                                "NotiflyWebView.onPageFinished: Javascript injected",
                            )
                            onPageFinishedCallback()
                        } else {
                            Logger.v("NotiflyWebView.onPageFinished: Page failed to load")
                            onReceivedErrorCallback(errorMessage)
                        }
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?,
                    ) {
                        super.onReceivedError(view, request, error)
                        pageLoadedSuccessfully = false
                        Logger.w("NotiflyWebView.onReceivedError: $error")
                        errorMessage =
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                error?.description.toString()
                            } else {
                                error?.toString()
                            }
                    }

                    override fun onReceivedHttpError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        errorResponse: WebResourceResponse?,
                    ) {
                        super.onReceivedHttpError(view, request, errorResponse)
                        pageLoadedSuccessfully = false
                        Logger.w("NotiflyWebView.onReceivedHttpError: $request, $errorResponse")
                        errorMessage =
                            if (errorResponse != null) "HTTP error: ${errorResponse.statusCode}" else null
                    }

                    override fun onReceivedSslError(
                        view: WebView?,
                        handler: SslErrorHandler?,
                        error: SslError?,
                    ) {
                        super.onReceivedSslError(view, handler, error)
                        pageLoadedSuccessfully = false
                        Logger.w("NotiflyWebView.onReceivedSslError: $error")
                        errorMessage = if (error != null) "SSL error: ${error.primaryError}" else null
                    }
                }

            this.addJavascriptInterface(
                NotiflyJavascriptInterface(
                    context,
                    eventLogData,
                    templateName,
                ),
                JS_INTERFACE_NAME,
            )

            this.setViewDimensions(modalProperties, context.resources.displayMetrics.density)
        }

        private fun setViewDimensions(
            modalProperties: JSONObject?,
            density: Float,
        ) {
            val (screenWidth, screenHeight) =
                InAppMessageUtils.getScreenWidthAndHeight(
                    context as Activity,
                    density,
                )
            val (widthDp, heightDp) =
                InAppMessageUtils.getViewDimensions(
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
            widthDp: Float,
            heightDp: Float,
            density: Float,
            position: String,
        ) {
            val layoutParams =
                ConstraintLayout.LayoutParams(
                    (widthDp * density).roundToInt(),
                    (heightDp * density).roundToInt(),
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
            modalProperties: JSONObject,
            density: Float,
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
                this.radii =
                    floatArrayOf(
                        topLeftRadiusPx.toFloat(),
                        topLeftRadiusPx.toFloat(),
                        topRightRadiusPx.toFloat(),
                        topRightRadiusPx.toFloat(),
                        bottomRightRadiusPx.toFloat(),
                        bottomRightRadiusPx.toFloat(),
                        bottomLeftRadiusPx.toFloat(),
                        bottomLeftRadiusPx.toFloat(),
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
            private val context: Context,
            val eventLogData: EventLogData,
            val templateName: String?,
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
                templateName: String?,
            ) {
                try {
                    when (type) {
                        "close" -> {
                            Logger.d("In-app message close button clicked")
                            logInAppMessageButtonClick("close_button_click", buttonName)
                        }

                        "main_button" -> {
                            Logger.d("In-app message main button clicked")
                            logInAppMessageButtonClick("main_button_click", buttonName)
                            if (link != null && link != "null") {
                                Logger.d("In-app message main button link: link")
                                val intent = getIntent(link)
                                if (intent != null) {
                                    context.startActivity(intent)
                                }
                            }
                        }

                        "hide_in_app_message" -> {
                            Logger.d("In-app message hide button clicked")
                            logInAppMessageButtonClick("hide_in_app_message_button_click", buttonName)
                            if (extraData == null) {
                                templateName?.let {
                                    val key = "hide_in_app_message_$it"
                                    CommandDispatcher.dispatch(
                                        SetUserPropertiesCommand(
                                            SetUserPropertiesPayload(
                                                context,
                                                mapOf(key to true),
                                            ),
                                        ),
                                    )
                                }
                            } else {
                                val hideUntilInDays = extraData.optInt("hide_until_in_days", -1)
                                val now = NotiflyTimerUtil.getTimestampSeconds()
                                val hideUntilInTimestamp =
                                    if (hideUntilInDays == -1) {
                                        -1
                                    } else {
                                        now + (hideUntilInDays * 24 * 60 * 60)
                                    }
                                val key = "hide_in_app_message_until_$templateName"
                                CommandDispatcher.dispatch(
                                    SetUserPropertiesCommand(
                                        SetUserPropertiesPayload(
                                            context,
                                            mapOf(key to hideUntilInTimestamp),
                                        ),
                                    ),
                                )
                            }
                        }

                        "survey_submit_button" -> {
                            Logger.d("In-app message survey submit button clicked")
                            logInAppMessageButtonClick(
                                "survey_submit_button_click",
                                buttonName,
                                extraData,
                            )
                        }
                    }
                } catch (e: Exception) {
                    Logger.w(
                        "[Notifly] Unexpected error occurs while handling in-app message button click event. This error is mostly caused by invalid url you have entered.",
                        e,
                    )
                } finally {
                    (context as Activity).finish()
                }
            }

            fun logInAppMessageButtonClick(
                eventName: String,
                buttonName: String,
                extraData: JSONObject? = null,
            ) {
                val eventParams =
                    mutableMapOf<String, Any?>(
                        "type" to "message_event",
                        "channel" to "in-app-message",
                        "button_name" to buttonName,
                        "campaign_id" to eventLogData.campaignId,
                        "notifly_message_id" to eventLogData.notiflyMessageId,
                    )
                if (eventName == "survey_submit_button_click" && extraData != null) {
                    eventParams["notifly_extra_data"] = extraData
                }
                CommandDispatcher.dispatch(
                    TrackEventCommand(
                        TrackEventPayload(
                            context,
                            eventName,
                            eventParams,
                            listOf(),
                            true,
                        ),
                    ),
                )
            }
        }

        private fun getIntent(url: String?): Intent? {
            val uri =
                if (url != null) {
                    Uri.parse(url.trim { it <= ' ' })
                } else {
                    null
                }

            return if (uri != null) {
                if (OSUtil.isInAppLink(uri)) {
                    val intentFlags = NotiflySdkPrefs.inAppMessage.getIntentFlagsForInAppLinkOpening()
                    Logger.v("Opening in-app link with flags: $intentFlags")
                    OSUtil.openURLInBrowserIntent(uri, intentFlags)
                } else {
                    OSUtil.openURLInBrowserIntent(uri)
                }
            } else {
                null
            }
        }

        companion object {
            private val javascriptToInject =
                """
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
