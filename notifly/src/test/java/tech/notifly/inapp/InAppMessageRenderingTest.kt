package tech.notifly.inapp

import android.content.Context
import android.webkit.WebView
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import tech.notifly.R
import tech.notifly.application.IApplicationService
import tech.notifly.inapp.models.Campaign
import tech.notifly.kmp.popup.PopupFactory
import tech.notifly.kmp.popup.PopupRenderTask
import tech.notifly.kmp.popup.PopupRenderer
import tech.notifly.kmp.popup.model.PopupRenderInput
import tech.notifly.kmp.popup.model.PopupRenderOutput
import tech.notifly.sdk.NotiflySdkInfo
import tech.notifly.services.NotiflyServiceProvider
import tech.notifly.storage.NotiflyStorage
import tech.notifly.storage.NotiflyStorageItem
import tech.notifly.utils.NotiflyAuthUtil
import tech.notifly.utils.NotiflyDeviceUtil

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class InAppMessageRenderingTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @After
    fun tearDown() {
        InAppMessageScheduler.descheduleAll()
    }

    @Test
    fun schedule_ssrWithoutRenderContext_doesNotOpenOriginalTemplate() {
        InAppMessageScheduler.schedule(context, popupCampaign("ssr"))
        ShadowLooper.idleMainLooper()

        assertNull(shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity)
    }

    @Test
    fun schedule_missingMode_preservesOriginalUrl() {
        InAppMessageScheduler.schedule(context, popupCampaign(null))
        ShadowLooper.idleMainLooper()

        val intent = shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity
        assertEquals("https://in-app-message.notifly.tech/original.html", intent.getStringExtra("in_app_message_url"))
    }

    @Test
    fun render_invalidConfiguration_realKmpSkipsWithoutNetwork() =
        runBlocking {
            val html =
                withTimeout(3000) {
                    InAppMessageRenderer("", "invalid").render(
                        PopupRenderInput("ssr", "campaign-a", "b".repeat(32), "c".repeat(32), "purchase", emptyMap()),
                    )
                }

            assertNull(html)
        }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class InAppMessageKmpRenderingTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val callbacks = mutableListOf<(PopupRenderOutput) -> Unit>()
    private val inputs = mutableListOf<PopupRenderInput>()
    private val tasks = mutableListOf<PopupRenderTask>()
    private var externalUserId: String? = "user-a"
    private var foreground = true
    private val kmpRenderer = mockk<PopupRenderer>()

    @Before
    fun setup() {
        mockkObject(PopupFactory, NotiflyStorage, NotiflyAuthUtil, NotiflyDeviceUtil)
        every { NotiflyStorage.get(any(), NotiflyStorageItem.PROJECT_ID) } returns "a".repeat(32)
        every { NotiflyStorage.get(any(), NotiflyStorageItem.EXTERNAL_USER_ID) } answers { externalUserId }
        coEvery { NotiflyAuthUtil.getNotiflyUserId(any()) } returns "b".repeat(32)
        coEvery { NotiflyDeviceUtil.getExternalDeviceId(any()) } returns "device-a"
        val applicationService = mockk<IApplicationService>()
        every { applicationService.isInForeground } answers { foreground }
        NotiflyServiceProvider.register(IApplicationService::class.java, applicationService)
        every { PopupFactory.create(any()) } returns kmpRenderer
        every { kmpRenderer.render(any(), any()) } answers {
            inputs.add(firstArg())
            callbacks.add(secondArg())
            mockk<PopupRenderTask>(relaxed = true).also { tasks.add(it) }
        }
        InAppMessageManager.disabled = false
    }

    @After
    fun tearDown() {
        InAppMessageScheduler.descheduleAll()
        InAppMessageManager.disabled = false
        NotiflyServiceProvider.unregister(IApplicationService::class.java)
        unmockkAll()
    }

    @Test
    fun schedule_ssr_waitsForKmpBeforeOpeningActivity() {
        schedule()

        assertEquals(1, callbacks.size)
        assertNull(shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity)
        assertTrue(InAppMessageScheduler.getScheduledCampaignIds().contains("campaign-a"))
        assertEquals("b".repeat(32), inputs.single().notiflyUserId)
        verify {
            PopupFactory.create(
                match {
                    it.projectId == "a".repeat(32) && it.baseUrl == "https://render.notifly.tech" &&
                        it.sdkVersion == "notifly/android/${NotiflySdkInfo.getSdkVersion()}"
                },
            )
        }
    }

    @Test
    fun schedule_delay_doesNotRenderBeforeDeadline() {
        InAppMessageScheduler.schedule(context, popupCampaign("ssr").copy(delay = 5))
        ShadowLooper.idleMainLooper(
            4,
            java
                .util
                .concurrent
                .TimeUnit
                .SECONDS,
        )
        assertTrue(callbacks.isEmpty())

        ShadowLooper.idleMainLooper(
            1,
            java
                .util
                .concurrent
                .TimeUnit
                .SECONDS,
        )
        assertEquals(1, callbacks.size)
    }

    @Test
    fun schedule_userChangedBeforeDeadline_doesNotRender() {
        InAppMessageScheduler.schedule(context, popupCampaign("ssr").copy(delay = 5))
        externalUserId = "user-b"
        ShadowLooper.idleMainLooper(
            5,
            java
                .util
                .concurrent
                .TimeUnit
                .SECONDS,
        )

        assertTrue(callbacks.isEmpty())
        assertNull(shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity)
    }

    @Test
    fun descheduleAll_pendingRender_ignoresLateResult() {
        schedule()
        InAppMessageScheduler.descheduleAll()
        complete("rendered")

        assertNull(shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity)
        assertTrue(InAppMessageScheduler.getScheduledCampaignIds().isEmpty())
    }

    @Test
    fun schedule_triggerContext_preservesNestedEventParams() {
        val params = mapOf("items" to listOf(mapOf("id" to "P1", "quantity" to 2)), "enabled" to true, "empty" to null)
        InAppMessageScheduler.schedule(context, popupCampaign("ssr"), "purchase", params)
        ShadowLooper.idleMainLooper()

        assertEquals(1, inputs.size)
        assertEquals("purchase", inputs.single().eventName)
        assertEquals(params, inputs.single().eventParams)
        assertEquals("campaign-a", inputs.single().campaignId)
        assertEquals(32, inputs.single().deviceId?.length)
    }

    @Test
    fun activity_renderedPopup_loadsHtmlWithOriginalBaseUrl() {
        schedule()
        val html = "<p>개인화 &amp; 상품</p><img src='image.png'>" + " ".repeat(1_100_000)
        complete("rendered", html = html)
        val intent = shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity
        assertTrue(intent.getBooleanExtra("in_app_message_rendered", false))
        assertTrue(intent.extras!!.keySet().none { intent.extras!!.get(it) == html })
        val controller = Robolectric.buildActivity(NotiflyInAppMessageActivity::class.java, intent).create()
        try {
            val webView = controller.get().findViewById<WebView>(R.id.webView)
            val loaded = shadowOf(webView).lastLoadDataWithBaseURL
            assertNotNull(loaded)
            assertEquals(html, loaded.data)
            assertEquals("https://in-app-message.notifly.tech/original.html", loaded.baseUrl)
            assertEquals("UTF-8", loaded.encoding)
            assertEquals("text/html", loaded.mimeType)
            assertNull(shadowOf(webView).lastLoadedUrl)
            assertNotNull(shadowOf(webView).getJavascriptInterface("Android"))
            assertNull(InAppMessageScheduler.consumeRenderedHtml(intent.getStringExtra("notifly_message_id")))
        } finally {
            controller.get().finish()
            controller.destroy()
        }
    }

    @Test
    fun activity_missingRenderedContent_doesNotLoadOriginalTemplate() {
        schedule()
        complete("rendered")
        val intent = shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity
        InAppMessageScheduler.descheduleAll()
        val controller = Robolectric.buildActivity(NotiflyInAppMessageActivity::class.java, intent).create()
        try {
            assertTrue(controller.get().isFinishing)
            val webView = controller.get().findViewById<WebView>(R.id.webView)
            assertNull(shadowOf(webView).lastLoadedUrl)
            assertNull(shadowOf(webView).lastLoadDataWithBaseURL)
        } finally {
            controller.destroy()
        }
    }

    @Test
    fun schedule_failedRender_doesNotFallBackToOriginalTemplate() {
        schedule()
        complete("failed")

        assertNull(shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity)
        assertTrue(InAppMessageScheduler.getScheduledCampaignIds().isEmpty())
    }

    @Test
    fun schedule_skippedRender_doesNotOpenActivity() {
        schedule()
        complete("skipped")

        assertNull(shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity)
    }

    @Test
    fun schedule_staticModes_doNotInvokeKmp() {
        for (mode in listOf(null, "static", "unknown", "SSR", " ssr ")) {
            InAppMessageScheduler.schedule(context, popupCampaign(mode))
            ShadowLooper.idleMainLooper()
            val intent = shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity
            assertEquals("https://in-app-message.notifly.tech/original.html", intent.getStringExtra("in_app_message_url"))
        }

        verify(exactly = 0) { PopupFactory.create(any()) }
    }

    @Test
    fun schedule_cancelledDuringRender_ignoresLateResult() {
        schedule()
        assertEquals(1, tasks.size)
        InAppMessageScheduler.deschedule("campaign-a")
        complete("rendered")

        verify { tasks.single().cancel() }
        assertNull(shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity)
        assertTrue(InAppMessageScheduler.getScheduledCampaignIds().isEmpty())
    }

    @Test
    fun schedule_userChangedDuringRender_doesNotOpenActivity() {
        schedule()
        externalUserId = "user-b"
        complete("rendered")

        assertNull(shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity)
    }

    @Test
    fun schedule_backgroundedDuringRender_doesNotOpenActivity() {
        schedule()
        foreground = false
        complete("rendered")

        assertNull(shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity)
    }

    @Test
    fun schedule_disabledDuringRender_doesNotOpenActivity() {
        schedule()
        InAppMessageManager.disabled = true
        complete("rendered")

        assertNull(shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity)
    }

    @Test
    fun schedule_replacedRender_doesNotPresentOrRemoveNewRequest() {
        schedule()
        schedule()
        assertEquals(2, callbacks.size)
        complete("rendered", index = 0)

        assertNull(shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity)
        assertEquals(listOf("campaign-a"), InAppMessageScheduler.getScheduledCampaignIds())
        complete("rendered", index = 1)
        assertEquals("campaign-a", shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity.getStringExtra("in_app_message_campaign_id"))
    }

    private fun schedule() {
        InAppMessageScheduler.schedule(context, popupCampaign("ssr"))
        ShadowLooper.idleMainLooper()
    }

    private fun complete(
        outcome: String,
        index: Int = 0,
        html: String = "<p>Personalized</p>",
    ) {
        assertTrue("A render request must be pending", callbacks.size > index)
        val output = mockk<PopupRenderOutput>()
        every { output.outcome } returns outcome
        every { output.html } returns html
        every { output.errorCode } returns "template_render_failed"
        callbacks[index](output)
        ShadowLooper.idleMainLooper()
    }
}

internal fun popupCampaign(mode: String?): Campaign =
    Campaign.fromJSONObject(
        JSONObject(
            """
            {
              "id": "campaign-a", "channel": "in-app-message", "updated_at": "2026-09-17",
              "starts": [0], "end": null, "segment_type": "condition", "segment_info": {"groups": []},
              "triggering_conditions": [[{"type": "event_name", "operator": "=", "operand": "purchase"}]],
              "message": {
                "html_url": "https://in-app-message.notifly.tech/original.html",
                "modal_properties": {"template_name": "test-template"}
              }
            }
            """.trimIndent(),
        ).apply { getJSONObject("message").put("template_rendering_mode", mode) },
    )
