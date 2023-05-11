package tech.notifly.sample

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import tech.notifly.sample.ui.theme.NotiflyAndroidSDKTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat

// deeplink: notiflyandroidtestapp://playground
class PlaygroundActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val hasNotificationPermission = hasNotificationPermission(this)

        setContent {
            NotiflyAndroidSDKTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Text(text = "Notifly Playground", fontSize = 20.sp, fontWeight = FontWeight.Bold)

                        Text(text = "Notification Permission: $hasNotificationPermission")

                        Button(onClick = { /* do something */ }) {
                            Text("Dummy Button")
                        }
                        Button(
                            onClick = { launchSampleActivity() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = "Go to SampleActivity")
                        }
                    }
                }
            }
        }
    }

    private fun launchSampleActivity() {
        val intent = Intent(this, SampleActivity::class.java)
        startActivity(intent)
    }

    private fun hasNotificationPermission(context: Context): Boolean {
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
}
