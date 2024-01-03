package tech.notifly.sample

import android.content.Intent
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import tech.notifly.Notifly
import tech.notifly.sample.ui.theme.NotiflyAndroidSDKTheme

class WebViewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            NotiflyAndroidSDKTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background
                ) {
                    Column(
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        AndroidView(
                            factory = { context ->
                                WebView(context).apply {
                                    settings.javaScriptEnabled = true
                                    webViewClient = WebViewClient()
                                    loadUrl("https://csjunha.com")
                                }
                            }, modifier = Modifier
                                .fillMaxHeight(0.8f)
                                .weight(0.8f)
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxHeight(0.2f)
                                .fillMaxWidth(1.0f)
                                .weight(0.2f)
                                .background(Color.Gray),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Button(onClick = { launchSampleActivity() }) {
                                Text(text = "Go to SampleActivity")
                            }
                            Button(onClick = {
                                Notifly.trackEvent(
                                    this@WebViewActivity,
                                    "webview-test-event"
                                )
                            }, modifier = Modifier.padding(top = 8.dp)) {
                                Text(text = "WebView Test Event")
                            }
                            Button(
                                onClick = {
                                    Notifly.setUserProperties(
                                        this@WebViewActivity, mapOf(
                                            "testKey1" to "testValue1"
                                        )
                                    )
                                },
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                Text(text = "WebView Test Event")
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun MyWebView(): WebView {
        val webView = remember {
            WebView(this@WebViewActivity).apply {
                settings.javaScriptEnabled = true
                webViewClient = WebViewClient()
                loadUrl("https://csjunha.com")
            }
        }
        return webView
    }

    private fun launchSampleActivity() {
        val intent = Intent(this, SampleActivity::class.java)
        startActivity(intent)
    }
}