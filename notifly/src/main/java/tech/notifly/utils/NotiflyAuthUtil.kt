package tech.notifly.utils

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import tech.notifly.http.IHttpClient
import tech.notifly.services.NotiflyServiceProvider
import tech.notifly.storage.NotiflyStorage
import tech.notifly.storage.NotiflyStorageItem

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
        val httpClient = NotiflyServiceProvider.getService<IHttpClient>()
        val requestBody = JSONObject().apply {
            put("userName", username)
            put("password", password)
        }

        return withContext(Dispatchers.IO) {
            try {
                val response = httpClient.post(AUTHENTICATOR_URL, requestBody, null)
                if (!response.isSuccess) {
                    Logger.e("[Notifly] Failed to retrieve Cognito ID Token. Response: ${response.statusCode} ${response.payload}")
                    throw response.throwable!!
                }
                val responseJSON = JSONObject(response.payload!!)
                responseJSON.getString("data")
            } catch (e: Exception) {
                throw NullPointerException("[Notifly] Failed to retrieve Cognito ID Token. $e")
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

    /**
     * Invalidates and save [NotiflyStorageItem.COGNITO_ID_TOKEN]
     *
     * @throws IllegalStateException if [NotiflyStorageItem.USERNAME] or [NotiflyStorageItem.PASSWORD] is null
     */
    suspend fun invalidateCognitoIdToken(context: Context): String {
        val username: String =
            NotiflyStorage.get(context, NotiflyStorageItem.USERNAME) ?: throw IllegalStateException(
                "[Notifly] username not found. You should call Notifly.initialize before this."
            )
        val password: String =
            NotiflyStorage.get(context, NotiflyStorageItem.PASSWORD) ?: throw IllegalStateException(
                "[Notifly] password not found. You should call Notifly.initialize before this."
            )

        val newCognitoIdToken = getCognitoIdToken(username, password)
        NotiflyStorage.put(context, NotiflyStorageItem.COGNITO_ID_TOKEN, newCognitoIdToken)
        return newCognitoIdToken
    }
}