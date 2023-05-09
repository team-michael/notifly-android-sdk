package tech.notifly.inapp

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Point
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import tech.notifly.Notifly
import tech.notifly.R
import tech.notifly.storage.NotiflyStorage
import tech.notifly.storage.NotiflyStorageItem
import tech.notifly.utils.NotiflyLogUtil

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
            ); // logging in app messaging delivered
        }
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
