package tech.notifly

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object NotiflyAuthenticator {

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
}