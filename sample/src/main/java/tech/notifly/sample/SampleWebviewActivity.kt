package tech.notifly.sample

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout

class SampleWebviewActivity: AppCompatActivity() {
    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rootView = ConstraintLayout(this)
        rootView.id = View.generateViewId()
        rootView.layoutParams = ConstraintLayout.LayoutParams(
            ConstraintLayout.LayoutParams.MATCH_PARENT,
            ConstraintLayout.LayoutParams.MATCH_PARENT
        )

        webView = WebView(this)
        webView.id = View.generateViewId()
        webView.layoutParams = ConstraintLayout.LayoutParams(320, 452)
        rootView.addView(webView)

        setContentView(rootView)

        setupConstraints()
        initWebView()
        loadContent()
    }

    private fun setupConstraints() {
        val layoutParams = webView.layoutParams as ConstraintLayout.LayoutParams
        layoutParams.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
        layoutParams.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
        layoutParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
        layoutParams.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
        layoutParams.horizontalBias = 0.5f
        layoutParams.verticalBias = 0.5f
        webView.layoutParams = layoutParams
    }

    private fun initWebView() {
        val webSettings = webView.settings
        webSettings.javaScriptEnabled = true
        webSettings.loadWithOverviewMode = true
        webSettings.useWideViewPort = true
        webView.webViewClient = WebViewClient()

        webView.setBackgroundColor(Color.TRANSPARENT)
        webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
    }

    private fun loadContent() {
        val htmlContent = "<html><body style=\"background-color: white;\"><h1>Hello, world!</h1></body></html>"
        webView.loadData(htmlContent, "text/html", "UTF-8")
    }
}
