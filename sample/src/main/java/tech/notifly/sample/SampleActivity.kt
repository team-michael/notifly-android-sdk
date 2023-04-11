package tech.notifly.sample

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.Manifest
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import tech.notifly.NotiflyAuthenticator
import tech.notifly.sample.ui.theme.NotiflyAndroidSDKTheme

class SampleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        askNotificationPermission()

        setContent {
            NotiflyAndroidSDKTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SampleVerticalList("minyong", "000000")
                }
            }
        }
    }

    // Declare the launcher at the top of your Activity/Fragment:
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // FCM SDK (and your app) can post notifications.
        } else {
            // TODO: Inform user that that your app will not show notifications.
        }
    }

    private fun askNotificationPermission() {
        // This is only necessary for API level >= 33 (TIRAMISU)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                // FCM SDK (and your app) can post notifications.
            } else if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                // TODO: display an educational UI explaining to the user the features that will be enabled
                //       by them granting the POST_NOTIFICATION permission. This UI should provide the user
                //       "OK" and "No thanks" buttons. If the user selects "OK," directly request the permission.
                //       If the user selects "No thanks," allow the user to continue without notifications.
            } else {
                // Directly ask for the permission
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }


    @Composable
    fun NotiflyAuthenticatorSection(username: String, password: String) {
        val idToken = remember { mutableStateOf("null") }

        LaunchedEffect(key1 = idToken) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // Call the suspend function getCognitoIdToken
                    val token = NotiflyAuthenticator.getCognitoIdToken(username, password)

                    // Process the token
                    if (token != null) {
                        idToken.value = token
                        println("Cognito ID Token: $token")
                    } else {
                        idToken.value = "ID Token is null"
                        println("Failed to fetch Cognito ID Token")
                    }
                } catch (e: Exception) {
                    idToken.value = "Failed to Fetch Cognito ID Token: ${e.message}"
                    println("Error: $e")
                }
            }
        }

        return Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = "USERNAME: $username",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                text = "PASSWORD: $password",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                text = "ID Token: ${idToken.value}",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }

    @Composable
    fun SampleVerticalList(username: String, password: String) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Notifly", fontSize = 24.sp, fontWeight = FontWeight.Bold)

            NotiflyAuthenticatorSection(username, password)

            Button(
                onClick = { /* Handle button click */ },
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text(text = "Button 1")
            }

            Button(
                onClick = { /* Handle button click */ },
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text(text = "Button 2")
            }

            Button(
                onClick = { /* Handle button click */ },
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text(text = "Button 3")
            }
        }
    }
}