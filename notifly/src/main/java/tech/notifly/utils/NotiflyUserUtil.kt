package tech.notifly.utils

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
import tech.notifly.Notifly
import tech.notifly.extensions.await
import tech.notifly.storage.NotiflyStorage
import tech.notifly.storage.NotiflyStorageItem
import java.lang.IllegalStateException
import kotlin.jvm.Throws

internal object NotiflyUserUtil {

    private const val AUTHENTICATOR_URL = "https://cognito-idp.ap-northeast-2.amazonaws.com"
    private const val AUTHENTICATOR_CLIENT_ID = "2pc5pce21ec53csf8chafknqve"

    /**
     * Retrieves CognitoIdToken with given [username] and [password].
     * This function never returns `null` even if
     *
     * @return Cognito ID Token by given [username] and [password]
     * @throws NullPointerException if failed to retrieve Cognito ID Token.
     */
    @Throws(NullPointerException::class)
    suspend fun getCognitoIdToken(username: String, password: String): String {
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
                val response = NotiflyBaseUtil.HTTP_CLIENT.await(request)
                val jsonResponse = JSONObject(response.body!!.string())
                val authenticationResult = jsonResponse.getJSONObject("AuthenticationResult")
                authenticationResult.getString("IdToken")
            } catch (e: Exception) {
                Log.e(Notifly.TAG, "Authentication Failed", e)
                throw NullPointerException("Failed to Get Cognito ID Token")
            }
        }
    }


    /**
     * @throws IllegalStateException is thrown if <Project ID> not found.
     */
    @Throws(IllegalStateException::class)
    suspend fun getNotiflyUserId(context: Context): String {
        // early-return cached value if exists
        val encodedUserId: String? = NotiflyStorage.get(context, NotiflyStorageItem.USER_ID)
        if (encodedUserId != null) return encodedUserId

        // retrieve and put new user-id
        val projectId: String = NotiflyStorage.get(context, NotiflyStorageItem.PROJECT_ID)
            ?: throw IllegalStateException("[Notifly] <Project ID> not found. You should call Notifly.initialize first")
        val externalUserId: String? = NotiflyStorage.get(context, NotiflyStorageItem.EXTERNAL_USER_ID)

        val notiflyUserId = when {
            externalUserId != null -> NotiflyIdUtil.generate(
                NotiflyIdUtil.Namespace.NAMESPACE_REGISTERED_USER_ID,
                "${projectId}${externalUserId}"
            )
            else -> NotiflyIdUtil.generate(
                NotiflyIdUtil.Namespace.NAMESPACE_UNREGISTERED_USER_ID,
                "${projectId}${getFcmToken()}"
            )
        }

        NotiflyStorage.put(context, NotiflyStorageItem.USER_ID, notiflyUserId)
        return notiflyUserId
    }

    suspend fun getFcmToken(): String? {
        return try {
            FirebaseMessaging.getInstance().token.await()
        } catch (e: Exception) {
            null
        }
    }
}