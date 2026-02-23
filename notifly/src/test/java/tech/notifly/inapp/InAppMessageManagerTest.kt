package tech.notifly.inapp

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.provider.Settings
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings
import tech.notifly.application.IApplicationService
import tech.notifly.http.HttpResponse
import tech.notifly.http.IHttpClient
import tech.notifly.inapp.models.Campaign
import tech.notifly.inapp.models.Condition
import tech.notifly.inapp.models.EventIntermediateCounts
import tech.notifly.inapp.models.Message
import tech.notifly.inapp.models.Operator
import tech.notifly.inapp.models.SegmentConditionUnitType
import tech.notifly.inapp.models.SegmentInfo
import tech.notifly.inapp.models.TriggeringConditionOperator
import tech.notifly.inapp.models.TriggeringConditionType
import tech.notifly.inapp.models.TriggeringConditionUnit
import tech.notifly.inapp.models.TriggeringConditions
import tech.notifly.inapp.models.TriggeringEventFilters
import tech.notifly.inapp.models.UserData
import tech.notifly.sdk.NotiflySdkState
import tech.notifly.sdk.NotiflySdkStateManager
import tech.notifly.services.NotiflyServiceProvider
import tech.notifly.storage.NotiflyStorage
import tech.notifly.storage.NotiflyStorageItem
import tech.notifly.utils.NotiflySyncStateUtil

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowSettings::class])
class InAppMessageManagerTest {
    private lateinit var context: Context

    @Before
    fun setup() {
        context = mockk<Context>()
        setupPackageInfo()
        setupSharedPreferences()
        setupAndroidId()
        setupNotiflyStorage()
        setupHttpClient()
        setupNotiflyServiceProvider()
    }

    private fun setupPackageInfo() {
        val packageManager = mockk<PackageManager>()
        val packageInfo = mockk<PackageInfo>()
        packageInfo.versionName = "1.0.0"
        every { context.packageManager } returns packageManager
        every { context.packageName } returns "com.example.app"
        every { packageManager.getPackageInfo("com.example.app", 0) } returns packageInfo
    }

    private fun setupSharedPreferences() {
        val sharedPreferences = mockk<SharedPreferences>()
        val editor = mockk<SharedPreferences.Editor>()
        every { context.getSharedPreferences("NotiflyAndroidSDKPlainStorage", 0) } returns sharedPreferences
        every { sharedPreferences.getString(any(), any()) } returns null
        every { sharedPreferences.edit() } returns editor
        setupSharedPreferencesEditor(editor)
    }

    private fun setupSharedPreferencesEditor(editor: SharedPreferences.Editor) {
        every { editor.putString(any(), any()) } returns editor
        every { editor.putInt(any(), any()) } returns editor
        every { editor.putLong(any(), any()) } returns editor
        every { editor.putFloat(any(), any()) } returns editor
        every { editor.putBoolean(any(), any()) } returns editor
        every { editor.remove(any()) } returns editor
        every { editor.clear() } returns editor
        every { editor.apply() } just Runs
    }

    private fun setupAndroidId() {
        val contentResolver = RuntimeEnvironment.application.contentResolver
        every { context.contentResolver } returns contentResolver
        Settings.Secure.putString(contentResolver, Settings.Secure.ANDROID_ID, "test_android_id")
    }

    private fun setupNotiflyStorage() {
        mockkObject(NotiflyStorage)
        every { NotiflyStorage.get(context, NotiflyStorageItem.USERNAME) } returns "testUsername"
        every { NotiflyStorage.get(context, NotiflyStorageItem.PASSWORD) } returns "testPassword"
        every { NotiflyStorage.get(context, NotiflyStorageItem.PROJECT_ID) } returns "testProjectId"
    }

    private fun setupHttpClient() {
        val httpClient = mockk<IHttpClient>()
        setupHttpResponses(httpClient)
        mockkObject(NotiflyServiceProvider)
        every { NotiflyServiceProvider.getService<IHttpClient>() } returns httpClient
    }

    private fun setupHttpResponses(httpClient: IHttpClient) {
        val httpResponse =
            HttpResponse(
                statusCode = 200,
                payload = """{"data":"testCognitoIdToken"}""",
                throwable = null,
            )
        coEvery { httpClient.post(any(), any(), any()) } returns httpResponse

        val campaign = createDummyCampaign()
        val getUserStateResponse = createGetUserStateResponse(campaign)
        coEvery {
            httpClient.get(
                url = match { it.startsWith("https://api.notifly.tech/user-state/") },
                headers = any(),
            )
        } returns getUserStateResponse

        mockkObject(Campaign.Companion)
        every { Campaign.fromJSONObject(any()) } returns campaign
    }

    private fun createDummyCampaign(
        id: String = "test_campaign_id",
        channel: String = "in-app-message",
        updatedAt: String = "2023-09-04T11:35:23Z",
        testing: Boolean = false,
        whitelist: List<String>? = null,
        start: Long = 1693826123000,
        end: Long? = null,
        messageUrl: String = "https://example.com/message",
        modalProperties: String = """{"key": "value"}""",
        templateName: String = "test_template",
        triggeringConditionType: TriggeringConditionType = TriggeringConditionType.EVENT_NAME,
        triggeringConditionOperator: TriggeringConditionOperator = TriggeringConditionOperator.EQUALS,
        triggeringConditionOperand: String = "test_event",
        segmentInfo: SegmentInfo? = null,
        triggeringEventFilters: TriggeringEventFilters? = null,
        delay: Int = 0,
    ): Campaign {
        val message = mockk<Message>()
        every { message.url } returns messageUrl
        every { message.modalProperties } returns modalProperties
        every { message.templateName } returns templateName

        val triggeringConditionUnit = mockk<TriggeringConditionUnit>()
        every { triggeringConditionUnit.type } returns triggeringConditionType
        every { triggeringConditionUnit.operator } returns triggeringConditionOperator
        every { triggeringConditionUnit.operand } returns triggeringConditionOperand
        every { triggeringConditionUnit.match(any()) } returns true

        val triggeringConditions = mockk<TriggeringConditions>()
        every { triggeringConditions.conditions } returns listOf(listOf(triggeringConditionUnit))
        every { triggeringConditions.match(any()) } returns true

        return Campaign(
            id = id,
            channel = channel,
            updatedAt = updatedAt,
            testing = testing,
            whitelist = whitelist,
            start = start,
            end = end,
            message = message,
            segmentInfo = segmentInfo,
            triggeringConditions = triggeringConditions,
            triggeringEventFilters = triggeringEventFilters,
            delay = delay,
        )
    }

    private fun setupNotiflyServiceProvider() {
        val applicationService = mockk<IApplicationService>()
        every { applicationService.isInForeground } returns true
        every { NotiflyServiceProvider.getService<IApplicationService>() } returns applicationService
    }

    private fun createGetUserStateResponse(campaign: Campaign): HttpResponse =
        HttpResponse(
            statusCode = 200,
            payload =
                """
                {
                    "campaignData": [
                        {
                            "id": "${campaign.id}",
                            "channel": "${campaign.channel}",
                            "updated_at": "${campaign.updatedAt}",
                            "testing": ${campaign.testing},
                            "segment_type": "condition",
                            "starts": [${campaign.start}],
                            "end": ${campaign.end},
                            "message": {
                                "html_url": "${campaign.message.url}",
                                "modal_properties": ${campaign.message.modalProperties},
                                "template_name": "${campaign.message.templateName}"
                            },
                            "segment_info": ${campaign.segmentInfo},
                            "triggering_conditions": [
                                [
                                    {
                                        "type": "event_name",
                                        "operator": "=",
                                        "operand": "test_event"
                                    }
                                ]
                            ],
                            "triggering_event_filters": ${campaign.triggeringEventFilters},
                            "delay": ${campaign.delay}
                        }
                    ],
                    "eventIntermediateCountsData": [
                        {
                            "date": "2023-09-04",
                            "eventName": "test_event",
                            "count": 1,
                            "properties": {}
                        }
                    ],
                    "userData": {
                        "userId": "test_user_id",
                        "userProperties": {}
                    }
                }
                """.trimIndent(),
            throwable = null,
        )

    /**
     * Helper: sets the private `userData` field on InAppMessageManager via reflection.
     */
    private fun setUserData(userData: UserData) {
        val field = InAppMessageManager::class.java.getDeclaredField("userData")
        field.isAccessible = true
        field.set(InAppMessageManager, userData)
    }

    /**
     * Helper: calls the private `matchUserPropertyBasedCondition` via reflection.
     */
    private fun callMatchUserPropertyBasedCondition(
        context: Context,
        condition: Condition,
        eventParams: Map<String, Any?> = emptyMap(),
    ): Boolean {
        val method =
            InAppMessageManager::class.java.getDeclaredMethod(
                "matchUserPropertyBasedCondition",
                Context::class.java,
                Condition::class.java,
                Map::class.java,
            )
        method.isAccessible = true
        return method.invoke(InAppMessageManager, context, condition, eventParams) as Boolean
    }

    // ── IS_NULL / IS_NOT_NULL bug reproduction tests ──

    @Test
    fun `IS_NULL operator should return true when user property is absent and valueType is null`() {
        // Given: user has no "membership" property, condition uses IS_NULL without valueType
        val userData =
            UserData(
                platform = "android",
                osVersion = "13",
                appVersion = "1.0.0",
                sdkVersion = "1.12.0",
                sdkType = "native",
                randomBucketNumber = null,
                deviceExternalUserId = null,
                updatedAt = null,
                userProperties = mutableMapOf(),
                campaignHiddenUntil = mutableMapOf(),
            )
        setUserData(userData)

        // IS_NULL conditions don't have valueType from server
        val condition =
            Condition(
                unit = SegmentConditionUnitType.USER,
                operator = Operator.IS_NULL,
                value = null,
                attribute = "membership",
                event = null,
                eventConditionType = null,
                secondaryValue = null,
                valueType = null,
                comparisonParameter = null,
                useEventParamsAsConditionValue = null,
            )

        // When
        val result = callMatchUserPropertyBasedCondition(context, condition)

        // Then: should be true because "membership" is absent (null)
        assertTrue(
            "IS_NULL should return true when the user property is absent",
            result,
        )
    }

    @Test
    fun `IS_NOT_NULL operator should return true when user property exists and valueType is null`() {
        // Given: user has "membership" property set
        val userData =
            UserData(
                platform = "android",
                osVersion = "13",
                appVersion = "1.0.0",
                sdkVersion = "1.12.0",
                sdkType = "native",
                randomBucketNumber = null,
                deviceExternalUserId = null,
                updatedAt = null,
                userProperties = mutableMapOf("membership" to "gold"),
                campaignHiddenUntil = mutableMapOf(),
            )
        setUserData(userData)

        // IS_NOT_NULL conditions don't have valueType from server
        val condition =
            Condition(
                unit = SegmentConditionUnitType.USER,
                operator = Operator.IS_NOT_NULL,
                value = null,
                attribute = "membership",
                event = null,
                eventConditionType = null,
                secondaryValue = null,
                valueType = null,
                comparisonParameter = null,
                useEventParamsAsConditionValue = null,
            )

        // When
        val result = callMatchUserPropertyBasedCondition(context, condition)

        // Then: should be true because "membership" exists with value "gold"
        assertTrue(
            "IS_NOT_NULL should return true when the user property exists",
            result,
        )
    }

    @Test
    fun `IS_NULL operator should return false when user property exists and valueType is null`() {
        // Given: user has "membership" property
        val userData =
            UserData(
                platform = "android",
                osVersion = "13",
                appVersion = "1.0.0",
                sdkVersion = "1.12.0",
                sdkType = "native",
                randomBucketNumber = null,
                deviceExternalUserId = null,
                updatedAt = null,
                userProperties = mutableMapOf("membership" to "gold"),
                campaignHiddenUntil = mutableMapOf(),
            )
        setUserData(userData)

        val condition =
            Condition(
                unit = SegmentConditionUnitType.USER,
                operator = Operator.IS_NULL,
                value = null,
                attribute = "membership",
                event = null,
                eventConditionType = null,
                secondaryValue = null,
                valueType = null,
                comparisonParameter = null,
                useEventParamsAsConditionValue = null,
            )

        // When
        val result = callMatchUserPropertyBasedCondition(context, condition)

        // Then: should be false because "membership" exists
        assertFalse(
            "IS_NULL should return false when the user property exists",
            result,
        )
    }

    // ── isValuePresent non-String type tests ──

    @Test
    fun `IS_NOT_NULL operator should return true when user property is Int`() {
        val userData =
            UserData(
                platform = "android",
                osVersion = "13",
                appVersion = "1.0.0",
                sdkVersion = "1.12.0",
                sdkType = "native",
                randomBucketNumber = null,
                deviceExternalUserId = null,
                updatedAt = null,
                userProperties = mutableMapOf("purchase_count" to 5),
                campaignHiddenUntil = mutableMapOf(),
            )
        setUserData(userData)

        val condition =
            Condition(
                unit = SegmentConditionUnitType.USER,
                operator = Operator.IS_NOT_NULL,
                value = null,
                attribute = "purchase_count",
                event = null,
                eventConditionType = null,
                secondaryValue = null,
                valueType = null,
                comparisonParameter = null,
                useEventParamsAsConditionValue = null,
            )

        val result = callMatchUserPropertyBasedCondition(context, condition)

        assertTrue(
            "IS_NOT_NULL should return true when user property is Int(5)",
            result,
        )
    }

    @Test
    fun `IS_NOT_NULL operator should return true when user property is Boolean`() {
        val userData =
            UserData(
                platform = "android",
                osVersion = "13",
                appVersion = "1.0.0",
                sdkVersion = "1.12.0",
                sdkType = "native",
                randomBucketNumber = null,
                deviceExternalUserId = null,
                updatedAt = null,
                userProperties = mutableMapOf("is_premium" to true),
                campaignHiddenUntil = mutableMapOf(),
            )
        setUserData(userData)

        val condition =
            Condition(
                unit = SegmentConditionUnitType.USER,
                operator = Operator.IS_NOT_NULL,
                value = null,
                attribute = "is_premium",
                event = null,
                eventConditionType = null,
                secondaryValue = null,
                valueType = null,
                comparisonParameter = null,
                useEventParamsAsConditionValue = null,
            )

        val result = callMatchUserPropertyBasedCondition(context, condition)

        assertTrue(
            "IS_NOT_NULL should return true when user property is Boolean(true)",
            result,
        )
    }

    @Test
    fun `IS_NULL operator should return true when user property is absent for Int field`() {
        val userData =
            UserData(
                platform = "android",
                osVersion = "13",
                appVersion = "1.0.0",
                sdkVersion = "1.12.0",
                sdkType = "native",
                randomBucketNumber = null,
                deviceExternalUserId = null,
                updatedAt = null,
                userProperties = mutableMapOf(),
                campaignHiddenUntil = mutableMapOf(),
            )
        setUserData(userData)

        val condition =
            Condition(
                unit = SegmentConditionUnitType.USER,
                operator = Operator.IS_NULL,
                value = null,
                attribute = "purchase_count",
                event = null,
                eventConditionType = null,
                secondaryValue = null,
                valueType = null,
                comparisonParameter = null,
                useEventParamsAsConditionValue = null,
            )

        val result = callMatchUserPropertyBasedCondition(context, condition)

        assertTrue(
            "IS_NULL should return true when user property is absent",
            result,
        )
    }

    @Test
    fun `initialize should call setState in order`() =
        runTest {
            // Given
            val eventCounts = listOf(EventIntermediateCounts("2023-06-08", "test_event", 5, mapOf()))
            val userData = UserData.getSkeleton(context)
            val campaigns = listOf(createDummyCampaign())

            val fetchStateOutput =
                NotiflySyncStateUtil.FetchStateOutput(
                    campaigns = campaigns.toMutableList(),
                    eventCounts = eventCounts.toMutableList(),
                    userData = userData,
                )

            mockkObject(NotiflySyncStateUtil)
            coEvery { NotiflySyncStateUtil.fetchState(context) } returns fetchStateOutput

            mockkObject(InAppMessageScheduler)
            every { InAppMessageScheduler.schedule(context, campaigns[0]) } just runs

            mockkObject(NotiflySdkStateManager)
            every { NotiflySdkStateManager.setState(any()) } answers { callOriginal() }

            // When
            InAppMessageManager.initialize(context)

            // Then
            verify(exactly = 1) { NotiflySdkStateManager.setState(NotiflySdkState.REFRESHING) }
            verify(exactly = 1) { NotiflySdkStateManager.setState(NotiflySdkState.READY) }
        }
}
