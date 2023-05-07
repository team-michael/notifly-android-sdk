package tech.notifly.sample

import android.Manifest
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import tech.notifly.Notifly
import tech.notifly.sample.ui.theme.NotiflyAndroidSDKTheme
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.callSuspend
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.jvm.isAccessible

class SampleActivity : ComponentActivity() {

    companion object {
        const val TAG = "NotiflySample"
    }

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
    fun LabelTextRow(label: String, text: String) {
        val context = LocalContext.current
        val stateText = remember { mutableStateOf(text) }

        LaunchedEffect(text) {
            stateText.value = text
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .pointerInput(Unit) {
                    detectTapGestures(onLongPress = {
                        copyTextToClipboard(context, label, stateText.value)
                    })
                }
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = text,
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.weight(2f),
                textAlign = TextAlign.End,
            )
        }
    }

    @Composable
    fun LabelTextColumn(label: String, text: String) {
        val context = LocalContext.current
        val stateText = remember { mutableStateOf(text) }

        LaunchedEffect(text) {
            stateText.value = text
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .pointerInput(Unit) {
                    detectTapGestures(onLongPress = {
                        copyTextToClipboard(context, label, stateText.value)
                    })
                }
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = text,
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        }
    }

    @Composable
    fun NotiflyAuthenticatorSection(username: String, password: String) {
        val context = LocalContext.current
        val idToken = remember { mutableStateOf("loading...") }
        val fcmToken = remember { mutableStateOf("loading...") }
        val notiflyUserId = remember { mutableStateOf("loading...") }

        LaunchedEffect(key1 = idToken) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val (instance, function) = reflectObjectFunction(
                        "tech.notifly.utils.NotiflyAuthUtil",
                        "getCognitoIdToken"
                    )
                    val token = function.callSuspend(instance, username, password) as String?

                    if (token != null) {
                        idToken.value = token
                        Log.d(TAG, "Cognito ID Token: $token")
                    } else {
                        idToken.value = "null"
                        Log.d(TAG, "Cognito ID Token: null")
                    }
                } catch (e: Exception) {
                    idToken.value = "Error: ${e.message}"
                    Log.d(TAG, "Error: $e")
                }
            }
        }

        LaunchedEffect(key1 = fcmToken) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val (instance, function) = reflectObjectFunction(
                        "tech.notifly.utils.NotiflyFirebaseUtil",
                        "getFcmToken"
                    )
                    val token = function.callSuspend(instance) as String?

                    if (token != null) {
                        fcmToken.value = token
                        Log.d(TAG, "FCM Token: $token")
                    } else {
                        fcmToken.value = "null"
                        Log.d(TAG, "FCM Token: null")
                    }
                } catch (e: Exception) {
                    fcmToken.value = "Error: ${e.message}"
                    Log.d(TAG, "Error: $e")
                }
            }
        }

        LaunchedEffect(key1 = notiflyUserId) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val (instance, function) = reflectObjectFunction(
                        "tech.notifly.utils.NotiflyAuthUtil",
                        "getNotiflyUserId"
                    )
                    val userId = function.callSuspend(instance, context) as String

                    notiflyUserId.value = userId
                    Log.d(TAG, "Notifly User ID: $userId")
                } catch (e: Exception) {
                    notiflyUserId.value = "Error: ${e.message}"
                    Log.d(TAG, "Error: $e")
                }
            }
        }

        return Column(modifier = Modifier.padding(vertical = 8.dp)) {
            LabelTextRow("USERNAME", username)
            LabelTextRow("PASSWORD", password)
            LabelTextColumn("ID Token", idToken.value)
            LabelTextColumn("FCM Token", fcmToken.value)
            LabelTextColumn("Notifly User ID", notiflyUserId.value)
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun SampleVerticalList(username: String, password: String) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            val context = LocalContext.current
            var userId: String by remember { mutableStateOf("") }
            var propertyName by remember { mutableStateOf("") }
            var propertyValue by remember { mutableStateOf("") }

            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Notifly", fontSize = 20.sp, fontWeight = FontWeight.Bold)

                NotiflyAuthenticatorSection(username, password)

                Button(
                    onClick = {
                        val (instance, function) = reflectObjectFunction(
                            "tech.notifly.utils.NotiflyLogUtil",
                            "logEvent"
                        )
                        function.call(
                            instance,
                            context,
                            "click_button_1",
                            mapOf(
                                "keyString" to "value1",
                                "keyBoolean" to true,
                                "keyInt" to 100,
                            ),
                            listOf<String>(),
                            true
                        )
                    },
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    Text(text = "click_button_1 (logEvent)")
                }

                TextField(
                    value = userId,
                    onValueChange = { userId = it },
                    label = { Text("User ID") },
                    modifier = Modifier.padding(top = 8.dp)
                )

                Button(
                    onClick = {
                        if (userId.isNotEmpty()) {
                            val (instance, function) = reflectObjectFunction(
                                "tech.notifly.Notifly",
                                "setUserId"
                            )
                            function.call(
                                instance,
                                context,
                                userId
                            )
                        } else {
                            Log.w(TAG, "Empty user ID input")
                            // Show alert for empty user ID input
                            val builder = AlertDialog.Builder(context)
                            builder.setTitle("Error")
                            builder.setMessage("Please enter a user ID.")
                            builder.setPositiveButton("OK", null)
                            val dialog = builder.create()
                            dialog.show()
                        }
                    },
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(text = "set user id")
                }

                OutlinedTextField(
                    value = propertyName,
                    onValueChange = { propertyName = it },
                    label = { Text("User Property Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = propertyValue,
                    onValueChange = { propertyValue = it },
                    label = { Text("User Property Value") },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        if (propertyName.isEmpty() || propertyValue.isEmpty()) {
                            Log.w(TAG, "Empty user property input")
                            // Show alert for empty user property input
                            val builder = AlertDialog.Builder(context)
                            builder.setTitle("Error")
                            builder.setMessage("Please enter a user property name and value.")
                            builder.setPositiveButton("OK", null)
                            val dialog = builder.create()
                            dialog.show()
                        } else {
                            Notifly.setUserProperties(
                                context,
                                mapOf(propertyName to propertyValue)
                            )
                        }
                    },
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(text = "Set User Property")
                }

                Button(
                    onClick = { startActivity(Intent(context, PlaygroundActivity::class.java)) },
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(text = "Go to Playground page")
                }

                Button(
                    onClick = { /* Handle button click */ },
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(text = "Dummy Button")
                }
            }
        }
    }

    private fun copyTextToClipboard(context: Context, label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Copied <$label> to ClipBoard", LENGTH_SHORT).show()
    }

    private fun reflectObjectFunction(
        className: String,
        functionName: String
    ): Pair<Any, KFunction<*>> {
        val objectClass: KClass<out Any> = Class.forName(className).kotlin
        val objectInstance: Any = objectClass.objectInstance
            ?: throw NullPointerException("Instance Not Found for $className")
        val objectFunction: KFunction<*> =
            objectClass.memberFunctions.find { it.name == functionName }
                ?: throw NullPointerException("Function Not Found for $functionName")
        objectFunction.isAccessible = true

        return objectInstance to objectFunction
    }
}