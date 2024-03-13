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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
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
        private const val PERMISSION_REQUEST_CODE = 101
        private val VALUE_TYPES = listOf("TEXT", "INT", "BOOL", "LIST")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        askNotificationPermission()

        Log.d("SampleApplication", "onCreate")

        setContent {
            NotiflyAndroidSDKTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background
                ) {
                    SampleVerticalList(BuildConfig.NOTIFLY_USERNAME, BuildConfig.NOTIFLY_PASSWORD)
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
            // Show alert dialog
            AlertDialog.Builder(this).setTitle("Notification permission")
                .setMessage("Michael requires notification permission to work properly.")
                .setPositiveButton("OK") { dialog, _ ->
                    dialog.dismiss()
                }.show()
        }
    }

    private fun askNotificationPermission() {
        // This is only necessary for API level >= 33 (TIRAMISU)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                // FCM SDK (and your app) can post notifications.
                Toast.makeText(this, "Notification permission already granted!", Toast.LENGTH_SHORT)
                    .show()
            } else if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                // Display an educational UI explaining to the user the features that will be enabled
                // by them granting the POST_NOTIFICATION permission. This UI should provide the user
                // "OK" and "No thanks" buttons. If the user selects "OK," directly request the permission.
                // If the user selects "No thanks," allow the user to continue without notifications.
                AlertDialog.Builder(this).setTitle("Permission needed")
                    .setMessage("This app needs the notification permission to send you notifications.")
                    .setPositiveButton("ok") { _, _ ->
                        // directly request the permission
                        ActivityCompat.requestPermissions(
                            this,
                            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                            PERMISSION_REQUEST_CODE
                        )
                    }.setNegativeButton("No thanks") { dialog, _ ->
                        dialog.dismiss()
                    }.create().show()
            } else {
                // Directly ask for the permission
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), PERMISSION_REQUEST_CODE
                )
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

        Row(modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = {
                    copyTextToClipboard(context, label, stateText.value)
                })
            }) {
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

        Column(modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = {
                    copyTextToClipboard(context, label, stateText.value)
                })
            }) {
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
                        "tech.notifly.utils.NotiflyAuthUtil", "getCognitoIdToken"
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
                        "tech.notifly.utils.NotiflyFirebaseUtil", "getFcmToken"
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
                        "tech.notifly.utils.NotiflyAuthUtil", "getNotiflyUserId"
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

            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Notifly", fontSize = 20.sp, fontWeight = FontWeight.Bold)

                NotiflyAuthenticatorSection(username, password)

                Button(
                    onClick = {
                        val (instance, function) = reflectObjectFunction(
                            "tech.notifly.inapp.InAppMessageManager", "sync"
                        )
                        CoroutineScope(Dispatchers.IO).launch {
                            function.callSuspend(instance, context, false)
                        }
                    }, modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(text = "Force Sync State")
                }

                Button(
                    onClick = {
                        val (instance, function) = reflectObjectFunction(
                            "tech.notifly.utils.NotiflyTimerUtil", "getTimestampMicros"
                        )
                        val timestampMicros = function.call(instance) as Long
                        Log.v("SampleApplication", "Timestamp Micros: $timestampMicros")
                    }, modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(text = "Get Timestamp Micros")
                }

                TextField(value = userId,
                    onValueChange = { userId = it },
                    label = { Text("User ID") },
                    modifier = Modifier.padding(top = 8.dp)
                )

                Button(
                    onClick = {
                        if (userId.isNotEmpty()) {
                            val (instance, function) = reflectObjectFunction(
                                "tech.notifly.Notifly", "setUserId"
                            )
                            function.call(
                                instance, context, userId
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
                    }, modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(text = "Set User ID")
                }

                Button(
                    onClick = {
                        val (instance, function) = reflectObjectFunction(
                            "tech.notifly.Notifly", "setUserId"
                        )
                        function.call(
                            instance, context, null
                        )
                    }, modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(text = "Remove User ID")
                }
                TrackEventSection()
                SetUserPropertiesSection()
                NavigatorButtonsSection()
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun SetUserPropertiesSection() {
        val context = LocalContext.current

        var propertyName by remember { mutableStateOf("") }
        var propertyValue by remember { mutableStateOf("") }
        var propertyValueTypeSelectionExpanded by remember { mutableStateOf(false) }
        var selectedPropertyValueType by remember { mutableStateOf(VALUE_TYPES[0]) }

        return Column(modifier = Modifier.padding(vertical = 8.dp)) {
            OutlinedTextField(value = propertyName,
                onValueChange = { propertyName = it },
                label = { Text("User Property Name") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(value = propertyValue,
                onValueChange = { propertyValue = it },
                label = { Text("User Property Value") },
                modifier = Modifier.fillMaxWidth()
            )
            ExposedDropdownMenuBox(modifier = Modifier.padding(top = 8.dp),
                expanded = propertyValueTypeSelectionExpanded,
                onExpandedChange = {
                    propertyValueTypeSelectionExpanded = !propertyValueTypeSelectionExpanded
                }) {
                TextField(
                    // The `menuAnchor` modifier must be passed to the text field for correctness.
                    modifier = Modifier.menuAnchor(),
                    readOnly = true,
                    value = selectedPropertyValueType,
                    onValueChange = {},
                    label = { Text("Params Type") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = propertyValueTypeSelectionExpanded) },
                    colors = ExposedDropdownMenuDefaults.textFieldColors(),
                )
                ExposedDropdownMenu(
                    expanded = propertyValueTypeSelectionExpanded,
                    onDismissRequest = { propertyValueTypeSelectionExpanded = false },
                ) {
                    VALUE_TYPES.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type) },
                            onClick = {
                                selectedPropertyValueType = type
                                propertyValueTypeSelectionExpanded = false
                            },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                        )
                    }
                }
            }
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
                        val value: Any? = when (selectedPropertyValueType) {
                            "TEXT" -> propertyValue
                            "INT" -> propertyValue.toIntOrNull()
                            "BOOL" -> propertyValue.toBoolean()
                            "LIST" -> propertyValue.split(",").map { it.trim() }
                            else -> null
                        }
                        Notifly.setUserProperties(
                            context, mapOf(propertyName to value)
                        )
                    }
                }, modifier = Modifier.padding(top = 8.dp)
            ) {
                Text(text = "Set User Property")
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun TrackEventSection() {
        val context = LocalContext.current

        var eventName: String by remember { mutableStateOf("") }
        var eventParamsStringified: String by remember {
            mutableStateOf("{}")
        }

        return Column(modifier = Modifier.padding(vertical = 8.dp)) {
            TextField(value = eventName,
                onValueChange = { eventName = it },
                label = { Text("Event Name") },
                modifier = Modifier.padding(top = 8.dp)
            )
            OutlinedTextField(value = eventParamsStringified,
                onValueChange = { eventParamsStringified = it },
                label = { Text("Event Params (Stringified JSON)") },
                modifier = Modifier.padding(top = 8.dp)
            )
            Button(onClick = {
                try {
                    if (eventName.isEmpty()) {
                        Log.w(TAG, "Empty event name input")
                        // Show alert for empty event name input
                        val builder = AlertDialog.Builder(context)
                        builder.setTitle("Error")
                        builder.setMessage("Please enter an event name.")
                        builder.setPositiveButton("OK", null)
                        val dialog = builder.create()
                        dialog.show()
                        return@Button
                    }
                    val eventParamsJSONObject = JSONObject(eventParamsStringified)
                    val eventParams = mutableMapOf<String, Any?>()
                    eventParamsJSONObject.keys().forEach { key ->
                        eventParams[key] = eventParamsJSONObject.get(key).let { it ->
                            when (it) {
                                is String -> it
                                is Int -> it
                                is Boolean -> it
                                is JSONArray -> {
                                    val list = mutableListOf<Any>()
                                    for (i in 0 until it.length()) {
                                        list.add(it.get(i))
                                    }
                                    list.toList()
                                }

                                JSONObject.NULL -> null
                                else -> throw IllegalArgumentException("Invalid value type")
                            }
                        }
                    }

                    Log.v("SampleApplication", eventParams.toString())
                    eventParams.keys.forEach {
                        Log.v("SampleApplication", "$it: ${eventParams[it]}")
                        Log.v(
                            "SampleApplication",
                            "type of ${eventParams[it]} is ${eventParams[it]?.javaClass?.name ?: "null"}"
                        )
                    }
                    Notifly.trackEvent(
                        context, eventName, eventParams
                    )
                } catch (e: JSONException) {
                    Log.w(TAG, "Error: $e")
                    // Show alert for empty user ID input
                    val builder = AlertDialog.Builder(context)
                    builder.setTitle("Error")
                    builder.setMessage("Please enter a valid JSON string.")
                    builder.setPositiveButton("OK", null)
                    val dialog = builder.create()
                    dialog.show()
                }
            }) {
                Text(text = "Track Event")
            }
        }
    }

    @Composable
    private fun NavigatorButtonsSection() {
        val context = LocalContext.current
        return Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Button(
                onClick = { startActivity(Intent(context, PlaygroundActivity::class.java)) },
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text(text = "Go to Playground page")
            }

            Button(
                onClick = { startActivity(Intent(context, WebViewActivity::class.java)) },
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text(text = "Go to WebView Page")
            }

            Button(
                onClick = { /* Handle button click */ }, modifier = Modifier.padding(top = 8.dp)
            ) {
                Text(text = "Dummy Button")
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
        className: String, functionName: String
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
