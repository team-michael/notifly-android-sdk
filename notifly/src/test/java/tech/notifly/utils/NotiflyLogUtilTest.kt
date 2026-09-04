package tech.notifly.utils

import android.content.Context
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import tech.notifly.application.IApplicationService
import tech.notifly.http.HttpResponse
import tech.notifly.http.IHttpClient
import tech.notifly.services.NotiflyServiceProvider
import tech.notifly.storage.NotiflyStorage
import tech.notifly.storage.NotiflyStorageItem

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class NotiflyLogUtilTest {
    private lateinit var context: Context
    private lateinit var httpClient: IHttpClient
    private lateinit var requestBodies: MutableList<JSONObject>
    private lateinit var requestBodySnapshots: MutableList<String>

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        httpClient = mockk()
        requestBodies = mutableListOf()
        requestBodySnapshots = mutableListOf()

        mockkObject(NotiflyStorage)
        every {
            NotiflyStorage.get(context, NotiflyStorageItem.COGNITO_ID_TOKEN)
        } returns COGNITO_ID_TOKEN
        every {
            NotiflyStorage.get(context, NotiflyStorageItem.EXTERNAL_USER_ID)
        } returns EXTERNAL_USER_ID
        every {
            NotiflyStorage.get(context, NotiflyStorageItem.PROJECT_ID)
        } returns PROJECT_ID

        mockkObject(NotiflyAuthUtil)
        coEvery { NotiflyAuthUtil.getNotiflyUserId(context) } returns NOTIFLY_USER_ID

        mockkObject(NotiflyDeviceUtil)
        coEvery { NotiflyDeviceUtil.getExternalDeviceId(context) } returns EXTERNAL_DEVICE_ID
        coEvery { NotiflyDeviceUtil.getOsVersion() } returns OS_VERSION
        coEvery { NotiflyDeviceUtil.getAppVersion(context) } returns APP_VERSION
        every { NotiflyDeviceUtil.getPlatform() } returns PLATFORM

        mockkObject(NotiflyFirebaseUtil)
        coEvery { NotiflyFirebaseUtil.getFcmToken() } returns FCM_TOKEN

        mockkObject(NotiflyTimerUtil)
        every { NotiflyTimerUtil.getTimestampMillis() } returns TIMESTAMP_MILLIS
        every { NotiflyTimerUtil.getTimestampMicros() } returns TIMESTAMP_MICROS

        val applicationService = mockk<IApplicationService>()
        every { applicationService.current } returns null

        mockkObject(NotiflyServiceProvider)
        every {
            NotiflyServiceProvider.getService<IApplicationService>()
        } returns applicationService
        every {
            NotiflyServiceProvider.getService<IHttpClient>()
        } returns httpClient
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `retry reuses the original request payload`() =
        runTest {
            coEvery { httpClient.post(any(), any(), any()) } answers {
                captureRequestBody(secondArg())
                if (requestBodies.size == 1) {
                    HttpResponse(statusCode = 500, payload = null)
                } else {
                    HttpResponse(statusCode = 200, payload = "{}")
                }
            }

            NotiflyLogUtil.logEvent(
                context = context,
                eventName = EVENT_NAME,
                eventParams = mapOf("amount" to 100),
            )

            assertEquals(2, requestBodies.size)
            assertSame(requestBodies[0], requestBodies[1])
            assertEquals(requestBodySnapshots[0], requestBodySnapshots[1])

            val firstData = eventData(requestBodies[0])
            val retryData = eventData(requestBodies[1])
            assertEquals(firstData.getString("id"), retryData.getString("id"))
            assertEquals(firstData.getLong("time"), retryData.getLong("time"))
        }

    @Test
    fun `separate events get different IDs at the same timestamp`() =
        runTest {
            coEvery { httpClient.post(any(), any(), any()) } answers {
                captureRequestBody(secondArg())
                HttpResponse(statusCode = 200, payload = "{}")
            }

            NotiflyLogUtil.logEvent(context, EVENT_NAME)
            NotiflyLogUtil.logEvent(context, EVENT_NAME)

            assertEquals(2, requestBodies.size)
            val firstData = eventData(requestBodies[0])
            val secondData = eventData(requestBodies[1])

            assertEquals(firstData.getLong("time"), secondData.getLong("time"))
            assertNotEquals(firstData.getString("id"), secondData.getString("id"))
        }

    @Test
    fun `retry stops after three attempts and keeps the original payload`() =
        runTest {
            coEvery { httpClient.post(any(), any(), any()) } answers {
                captureRequestBody(secondArg())
                HttpResponse(statusCode = 500, payload = null)
            }

            NotiflyLogUtil.logEvent(context, EVENT_NAME)

            assertEquals(4, requestBodies.size)
            requestBodies.drop(1).forEach { retryBody ->
                assertSame(requestBodies.first(), retryBody)
            }
            requestBodySnapshots.drop(1).forEach { retrySnapshot ->
                assertEquals(requestBodySnapshots.first(), retrySnapshot)
            }
        }

    private fun captureRequestBody(body: JSONObject) {
        requestBodies += body
        requestBodySnapshots += body.toString()
    }

    private fun eventData(body: JSONObject): JSONObject =
        JSONObject(
            body
                .getJSONArray("records")
                .getJSONObject(0)
                .getString("data"),
        )

    companion object {
        private const val COGNITO_ID_TOKEN = "cognito-token"
        private const val EXTERNAL_USER_ID = "external-user-id"
        private const val PROJECT_ID = "0123456789abcdef0123456789abcdef"
        private const val NOTIFLY_USER_ID = "abcdef0123456789abcdef0123456789"
        private const val EXTERNAL_DEVICE_ID = "external-device-id"
        private const val OS_VERSION = "14"
        private const val APP_VERSION = "1.0.0"
        private const val PLATFORM = "android"
        private const val FCM_TOKEN = "fcm-token"
        private const val TIMESTAMP_MILLIS = 1_725_438_000_000L
        private const val TIMESTAMP_MICROS = 1_725_438_000_000_000L
        private const val EVENT_NAME = "purchase"
    }
}
