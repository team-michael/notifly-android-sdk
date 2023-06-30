package tech.notifly.utils

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import tech.notifly.Logger
import tech.notifly.extensions.await
import tech.notifly.inapp.models.Campaign
import tech.notifly.inapp.models.EventIntermediateCounts
import tech.notifly.inapp.models.UserData
import tech.notifly.storage.NotiflyStorage
import tech.notifly.storage.NotiflyStorageItem
import java.lang.IllegalStateException
import kotlin.jvm.Throws

object NotiflySyncStateUtil {
    data class SyncStateOutput(
        val campaigns: MutableList<Campaign>,
        val eventCounts: MutableList<EventIntermediateCounts>,
        val userData: UserData?,
    )

    private const val SYNC_STATE_BASE_URI = "https://api.notifly.tech/user-state"
    private const val SYNC_STATE_MAX_RETRY_COUNT_ON_401 = 3

    @Throws(NullPointerException::class)
    suspend fun syncState(
        context: Context,
        retryCount: Int = 0,
    ): SyncStateOutput {
        val notiflyCognitoIdToken: String =
            NotiflyStorage.get(context, NotiflyStorageItem.COGNITO_ID_TOKEN)
                ?: invalidateCognitoIdToken(context) // invalidate if not set
        val notiflyUserId: String = NotiflyAuthUtil.getNotiflyUserId(context)
        val notiflyExternalUserId: String? =
            NotiflyStorage.get(context, NotiflyStorageItem.EXTERNAL_USER_ID)

        val notiflyProjectId: String = NotiflyStorage.get(context, NotiflyStorageItem.PROJECT_ID)
            ?: throw IllegalStateException("[Notifly] Required parameter <Project ID> is missing")

        val externalDeviceId: String = NotiflyDeviceUtil.getExternalDeviceId(context)
        val notiflyDeviceId =
            NotiflyIdUtil.generate(NotiflyIdUtil.Namespace.NAMESPACE_DEVICE_ID, externalDeviceId)

        val url =
            "$SYNC_STATE_BASE_URI/$notiflyProjectId/$notiflyUserId?channel=in-app-message&deviceId=$notiflyDeviceId"

        val request =
            Request.Builder().url(url).header("Authorization", "Bearer $notiflyCognitoIdToken")
                .header("Content-Type", "application/json").get().build()

        return withContext(Dispatchers.IO) {
            try {
                val response = N.HTTP_CLIENT.await(request)
                if (!response.isSuccessful) {
                    if (response.code == 401) {
                        // Invalid token
                        if (retryCount < SYNC_STATE_MAX_RETRY_COUNT_ON_401) {
                            Logger.w("[Notifly] Sync state failed with 401. Retrying...")
                            try {
                                invalidateCognitoIdToken(context)
                            } catch (e: Exception) {
                                Logger.e("[Notifly] Failed to invalidate cognito id token", e)
                                throw NullPointerException("[Notifly] Failed to invalidate cognito id token")
                            }
                            return@withContext syncState(context, retryCount + 1)
                        } else {
                            Logger.e("[Notifly] Sync state failed with 401. Max retry count reached.")
                            throw NullPointerException("[Notifly] Sync state failed with 401. Max retry count reached.")
                        }
                    } else {
                        throw NullPointerException("[Notifly] Sync state failed with ${response.code}")
                    }
                }
                val jsonResponse = JSONObject(response.body!!.string())
                val campaignsJsonArray = jsonResponse.getJSONArray("campaignData")
                val campaigns = mutableListOf<Campaign>()

                var failedCount = 0
                for (i in 0 until campaignsJsonArray.length()) {
                    val campaignJsonObject = campaignsJsonArray.getJSONObject(i)
                    val campaign = try {
                        Campaign.fromJSONObject(campaignJsonObject, notiflyExternalUserId)
                            ?: continue
                    } catch (e: JSONException) {
                        Logger.w(
                            "[Notifly] Failed to parse campaign: encountered error while working with JSON",
                            e
                        )
                        failedCount++
                        continue
                    }
                    campaigns.add(campaign)
                }

                if (failedCount > 0) {
                    Logger.w("[Notifly] $failedCount campaigns were not parsed correctly. In app messages may not be correctly displayed.")
                }

                val eventCountsJsonArray = jsonResponse.getJSONArray("eventIntermediateCountsData")
                val eventCounts = mutableListOf<EventIntermediateCounts>()
                for (i in 0 until eventCountsJsonArray.length()) {
                    val eventCountsJsonObject = eventCountsJsonArray.getJSONObject(i)
                    val eventCount =
                        EventIntermediateCounts.fromJSONObject(eventCountsJsonObject) ?: continue
                    eventCounts.add(eventCount)
                }
                if (eventCounts.size != eventCountsJsonArray.length()) {
                    Logger.w("[Notifly] Some event counts were not parsed correctly. In app messages may not be correctly displayed.")
                }

                val userDataJsonObject = jsonResponse.getJSONObject("userData")
                val userData = UserData.fromJSONObject(userDataJsonObject)

                SyncStateOutput(campaigns, eventCounts, userData)
            } catch (e: JSONException) {
                Logger.e(
                    "[Notifly] Failed to sync state: encountered error while working with JSON",
                    e
                )
                throw NullPointerException("[Notifly] Failed to sync state: encountered error while working with JSON")
            }
        }
    }

    /**
     * Invalidates and save [NotiflyStorageItem.COGNITO_ID_TOKEN]
     *
     * @throws IllegalStateException if [NotiflyStorageItem.USERNAME] or [NotiflyStorageItem.PASSWORD] is null
     */
    private suspend fun invalidateCognitoIdToken(context: Context): String {
        val username: String =
            NotiflyStorage.get(context, NotiflyStorageItem.USERNAME) ?: throw IllegalStateException(
                "[Notifly] username not found. You should call Notifly.initialize before this."
            )
        val password: String =
            NotiflyStorage.get(context, NotiflyStorageItem.PASSWORD) ?: throw IllegalStateException(
                "[Notifly] password not found. You should call Notifly.initialize before this."
            )

        val newCognitoIdToken = NotiflyAuthUtil.getCognitoIdToken(username, password)
        NotiflyStorage.put(context, NotiflyStorageItem.COGNITO_ID_TOKEN, newCognitoIdToken)
        return newCognitoIdToken
    }
}
