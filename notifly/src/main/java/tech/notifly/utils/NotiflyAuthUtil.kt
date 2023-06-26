package tech.notifly.utils

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import tech.notifly.Logger
import tech.notifly.extensions.await
import tech.notifly.storage.NotiflyStorage
import tech.notifly.storage.NotiflyStorageItem
import java.lang.IllegalStateException
import kotlin.jvm.Throws

internal object NotiflyAuthUtil {

    private const val AUTHENTICATOR_URL = "https://api.notifly.tech/authorize"

    /**
     * Retrieves CognitoIdToken with given [username] and [password].
     * This function never returns `null` even if failed to retrieve Cognito ID Token.
     *
     * @return Cognito ID Token by given [username] and [password]
     * @throws NullPointerException if failed to retrieve Cognito ID Token.
     */
    @Throws(NullPointerException::class)
    suspend fun getCognitoIdToken(username: String, password: String): String {
        val requestBody = JSONObject().apply {
            put("userName", username)
            put("password", password)
        }

        val request = Request.Builder()
            .url(AUTHENTICATOR_URL)
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return withContext(Dispatchers.IO) {
            try {
                val response = N.HTTP_CLIENT.await(request)
                val jsonResponse = JSONObject(response.body!!.string())
                jsonResponse.getString("data")
            } catch (e: Exception) {
                Logger.e("Authentication Failed", e)
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
        val externalUserId: String? =
            NotiflyStorage.get(context, NotiflyStorageItem.EXTERNAL_USER_ID)

        val notiflyUserId = when {
            externalUserId != null -> NotiflyIdUtil.generate(
                NotiflyIdUtil.Namespace.NAMESPACE_REGISTERED_USER_ID,
                "${projectId}${externalUserId}"
            )

            else -> NotiflyIdUtil.generate(
                NotiflyIdUtil.Namespace.NAMESPACE_UNREGISTERED_USER_ID,
                "${projectId}${NotiflyFirebaseUtil.getFcmToken()}"
            )
        }

        NotiflyStorage.put(context, NotiflyStorageItem.USER_ID, notiflyUserId)
        return notiflyUserId
    }
}