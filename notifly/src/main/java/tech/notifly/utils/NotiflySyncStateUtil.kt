package tech.notifly.utils

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import tech.notifly.http.IHttpClient
import tech.notifly.inapp.models.Campaign
import tech.notifly.inapp.models.EventIntermediateCounts
import tech.notifly.inapp.models.UserData
import tech.notifly.services.NotiflyServiceProvider
import tech.notifly.storage.NotiflyStorage
import tech.notifly.storage.NotiflyStorageItem
import java.net.HttpURLConnection

object NotiflySyncStateUtil {
    data class SyncStateOutput(
        val campaigns: MutableList<Campaign>,
        val eventCounts: MutableList<EventIntermediateCounts>,
        val userData: UserData,
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
                ?: NotiflyAuthUtil.invalidateCognitoIdToken(context) // invalidate if not set
        val notiflyUserId: String = NotiflyAuthUtil.getNotiflyUserId(context)

        val notiflyProjectId: String = NotiflyStorage.get(context, NotiflyStorageItem.PROJECT_ID)
            ?: throw IllegalStateException("[Notifly] Required parameter <Project ID> is missing")

        val externalDeviceId: String = NotiflyDeviceUtil.getExternalDeviceId(context)
        val notiflyDeviceId =
            NotiflyIdUtil.generate(NotiflyIdUtil.Namespace.NAMESPACE_DEVICE_ID, externalDeviceId)

        val url =
            "$SYNC_STATE_BASE_URI/$notiflyProjectId/$notiflyUserId?channel=in-app-message&deviceId=$notiflyDeviceId"

        val httpClient = NotiflyServiceProvider.getService<IHttpClient>()

        return withContext(Dispatchers.IO) {
            try {
                val response = httpClient.get(
                    url, mapOf(
                        "Authorization" to "Bearer $notiflyCognitoIdToken"
                    )
                )
                if (!response.isSuccess) {
                    if (response.statusCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
                        // Invalid token
                        if (retryCount < SYNC_STATE_MAX_RETRY_COUNT_ON_401) {
                            Logger.d("[NotiflyStateSynchronizer] Sync state failed with 401. Retrying...")
                            try {
                                NotiflyAuthUtil.invalidateCognitoIdToken(context)
                            } catch (e: Exception) {
                                Logger.e(
                                    "[NotiflyStateSynchronizer] Failed to invalidate cognito id token",
                                    e
                                )
                                throw NullPointerException("[NotiflyStateSynchronizer] Failed to invalidate cognito id token")
                            }
                            return@withContext syncState(context, retryCount + 1)
                        } else {
                            Logger.e("[NotiflyStateSynchronizer] Sync state failed with 401. Max retry count reached.")
                            throw NullPointerException("[NotiflyStateSynchronizer] Sync state failed. Response: ${response.payload} ${response.statusCode}")
                        }
                    } else {
                        throw NullPointerException("[NotiflyStateSynchronizer] Sync state failed. Response: ${response.payload} ${response.statusCode}")
                    }
                }

                val jsonResponse = try {
                    JSONObject(response.payload!!)
                } catch (e: JSONException) {
                    Logger.e(
                        "[NotiflyStateSynchronizer] Failed to sync state: encountered error while working with JSON",
                        e
                    )
                    throw NullPointerException("[NotiflyStateSynchronizer] Failed to sync state: response is not a valid JSON")
                }

                val campaignsJsonArray = try {
                    jsonResponse.getJSONArray("campaignData")
                } catch (e: JSONException) {
                    Logger.e(
                        "[NotiflyStateSynchronizer] Failed to sync state: encountered error while working with JSON",
                        e
                    )
                    throw NullPointerException("[NotiflyStateSynchronizer] Failed to sync state: campaign data is not a valid JSON array")
                }
                val campaigns = mutableListOf<Campaign>()

                var failedCount = 0
                for (i in 0 until campaignsJsonArray.length()) {
                    try {
                        val campaignJsonObject = campaignsJsonArray.getJSONObject(i)
                        val campaign = Campaign.fromJSONObject(campaignJsonObject)
                        campaigns.add(campaign)
                    } catch (e: JSONException) {
                        Logger.w(
                            "[Notifly] Failed to parse campaign: encountered error while working with JSON",
                            e
                        )
                        failedCount++
                        continue
                    }
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
                val userData = UserData.fromJSONObject(context, userDataJsonObject)

                SyncStateOutput(campaigns, eventCounts, userData)
            } catch (e: JSONException) {
                Logger.e(
                    "[Notifly] Failed to sync state: encountered error while working with JSON", e
                )
                throw NullPointerException("[Notifly] Failed to sync state: encountered error while working with JSON")
            }
        }
    }
}
