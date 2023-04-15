package tech.notifly

import android.content.Context
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object NotiflyUtils {


    private const val AUTHENTICATOR_URL = "https://cognito-idp.ap-northeast-2.amazonaws.com"
    private const val AUTHENTICATOR_CLIENT_ID = "2pc5pce21ec53csf8chafknqve"

    suspend fun getCognitoIdToken(username: String, password: String): String? {
        val requestBody = JSONObject().apply {
            put("AuthFlow", "USER_PASSWORD_AUTH")
            put("AuthParameters", JSONObject().apply {
                put("USERNAME", username)
                put("PASSWORD", password)
            })
            put("ClientId", AUTHENTICATOR_CLIENT_ID)
        }

        val request = Request.Builder()
            .url(AUTHENTICATOR_URL)
            .addHeader("X-Amz-Target", "AWSCognitoIdentityProviderService.InitiateAuth")
            .post(requestBody.toString().toRequestBody("application/x-amz-json-1.1".toMediaType()))
            .build()

        return withContext(Dispatchers.IO) {
            try {
                val response = NotiflyHttpClient.HTTP_CLIENT.await(request)
                val jsonResponse = JSONObject(response.body!!.string())
                val authenticationResult = jsonResponse.getJSONObject("AuthenticationResult")
                authenticationResult.getString("IdToken")
            } catch (e: Exception) {
                Log.e(Notifly.TAG, "Authentication Failed", e)
                null
            }
        }
    }


    suspend fun getNotiflyUserId(context: Context): String {
        // Get cached value if exists
        val encodedUserId: String? = NotiflySharedPreferences.get(context, "notiflyUserId", null)
        if (encodedUserId != null) return encodedUserId

        // Retrieve user id
        val projectId: String? = NotiflySharedPreferences.get(context, "notiflyProjectId", null)
        val externalUserId: String? = NotiflySharedPreferences.get(context, "notiflyExternalUserId", null)
        val notiflyUserUUID = if (externalUserId != null) {
            UUIDv5.generate(UUIDv5.Namespace.NAMESPACE_REGISTERED_USER_ID, "${projectId}${externalUserId}")
        } else {
            UUIDv5.generate(UUIDv5.Namespace.NAMESPACE_UNREGISTERED_USER_ID, "${projectId}${getFcmToken()}")
        }

        val notiflyUserId = notiflyUserUUID.toString().replace("-", "")
        return notiflyUserId.also { NotiflySharedPreferences.put(context, "notiflyUserId", notiflyUserId) }
    }

    suspend fun getFcmToken(): String? {
        return try {
            FirebaseMessaging.getInstance().token.await()
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getSystemVersion(): String = withContext(Dispatchers.IO) {
        android.os.Build.VERSION.RELEASE
    }

    suspend fun getAppVersion(context: Context): String = withContext(Dispatchers.IO) {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        packageInfo.versionName
    }

    suspend fun getUniqueId(context: Context): String = withContext(Dispatchers.IO) {
        android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        )
    }

    fun getPlatform(): String {
        return Notifly.PLATFORM
    }
}