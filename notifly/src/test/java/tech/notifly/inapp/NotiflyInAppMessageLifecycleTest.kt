package tech.notifly.inapp

import android.content.Intent
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import tech.notifly.R
import tech.notifly.command.CommandDispatcher
import tech.notifly.command.models.TrackEventCommand
import tech.notifly.inapp.views.NotiflyWebView

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30, 34])
class NotiflyInAppMessageLifecycleTest {
    private val controllers = mutableListOf<ActivityController<NotiflyInAppMessageActivity>>()

    @Before
    fun isolateExternalEffects() {
        mockkObject(CommandDispatcher, InAppMessageManager)
        every { CommandDispatcher.dispatch(any()) } just Runs
        every { InAppMessageManager.dispatchInAppMessageEvent(any(), any()) } just Runs
        every { InAppMessageManager.updateHideUntilData(any(), any()) } just Runs
    }

    @After
    fun cleanup() {
        controllers.asReversed().forEach {
            it.get().finish()
            if (!it.get().isDestroyed) it.pause().stop().destroy()
        }
        unmockkAll()
    }

    private fun create(): ActivityController<NotiflyInAppMessageActivity> {
        val app = RuntimeEnvironment.getApplication()
        val intent =
            Intent(app, NotiflyInAppMessageActivity::class.java)
                .putExtra("in_app_message_url", "https://example.invalid/popup")
                .putExtra("in_app_message_campaign_id", "regression-campaign")
                .putExtra("campaign_re_eligibility_specified", true)
                .putExtra("campaign_re_eligible_unit", "DAY")
                .putExtra("campaign_re_eligible_duration", 1)
        val controller = Robolectric.buildActivity(NotiflyInAppMessageActivity::class.java, intent)
        controllers.add(controller)
        return controller
            .create()
            .start()
            .resume()
            .visible()
    }

    private fun noImpression() {
        verify(exactly = 0) { CommandDispatcher.dispatch(any()) }
        verify(exactly = 0) { InAppMessageManager.dispatchInAppMessageEvent(any(), any()) }
        verify(exactly = 0) { InAppMessageManager.updateHideUntilData(any(), any()) }
    }

    @Test
    fun normalLoadDisplaysAndRecordsImpression() {
        val activity = create().get()
        val view = activity.findViewById<NotiflyWebView>(R.id.webView)
        view.webViewClient.onPageFinished(view, "https://example.invalid/popup")
        assertEquals(View.VISIBLE, view.visibility)
        assertFalse(activity.isFinishing)
        verify(exactly = 1) { CommandDispatcher.dispatch(match { it is TrackEventCommand }) }
        verify(exactly = 1) { InAppMessageManager.dispatchInAppMessageEvent("in_app_message_show", null) }
        verify(exactly = 1) { InAppMessageManager.updateHideUntilData("regression-campaign", any()) }
    }

    @Test
    fun finishingActivityIgnoresLateLoad() {
        val activity = create().get()
        val view = activity.findViewById<NotiflyWebView>(R.id.webView)
        val client = view.webViewClient
        activity.finish()
        client.onPageFinished(view, "https://example.invalid/popup")
        assertNull(shadowOf(view).lastEvaluatedJavascript)
        noImpression()
    }

    @Test
    fun destroyedActivityWithRemovedContentIgnoresCapturedCallback() {
        val controller = create()
        val activity = controller.get()
        val view = activity.findViewById<NotiflyWebView>(R.id.webView)
        val client = view.webViewClient
        controller.pause().stop().destroy()
        activity.findViewById<ViewGroup>(android.R.id.content).removeAllViews()
        client.onPageFinished(view, "https://example.invalid/popup")
        assertNull(shadowOf(view).lastEvaluatedJavascript)
        noImpression()
    }

    @Test
    fun missingLayoutStopsEntireDisplayFlow() {
        val activity = create().get()
        val view = activity.findViewById<NotiflyWebView>(R.id.webView)
        activity.findViewById<ViewGroup>(android.R.id.content).removeAllViews()
        view.webViewClient.onPageFinished(view, "https://example.invalid/popup")
        assertTrue(activity.isFinishing)
        noImpression()
    }

    @Test
    fun detachedWebViewStopsEntireDisplayFlow() {
        val activity = create().get()
        val view = activity.findViewById<NotiflyWebView>(R.id.webView)
        (view.parent as ViewGroup).removeView(view)
        view.webViewClient.onPageFinished(view, "https://example.invalid/popup")
        assertTrue(activity.isFinishing)
        noImpression()
    }

    @Test
    fun destroyedActivityReleasesActiveSlotForNextMessage() {
        val old = create()
        old.pause().stop().destroy()
        assertFalse(NotiflyInAppMessageActivity.isActive)
        val next = create().get()
        assertFalse(next.isFinishing)
        assertNotNull(next.findViewById<View>(R.id.webView))
    }

    @Test
    fun rejectedDuplicateDoesNotClearActiveOwner() {
        val owner = create().get()
        val duplicate = create()
        assertTrue(duplicate.get().isFinishing)
        duplicate.pause().stop().destroy()
        assertTrue(NotiflyInAppMessageActivity.isActive)
        assertFalse(owner.isFinishing)
    }

    @Test
    fun oldActivityFinishAndDestroyDoNotClearNewOwner() {
        val old = create()
        old.get().finish()
        val current = create().get()
        old.get().finish()
        old.pause().stop().destroy()
        assertTrue(NotiflyInAppMessageActivity.isActive)
        assertFalse(current.isFinishing)
    }

    @Test
    fun backgroundFinishWaitsForMainThreadBeforeReleasingActiveSlot() {
        val activity = create().get()
        val bridgeThread = Thread { activity.finish() }
        bridgeThread.start()
        bridgeThread.join()
        assertFalse(activity.isFinishing)
        assertTrue(NotiflyInAppMessageActivity.isActive)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(activity.isFinishing)
        assertFalse(NotiflyInAppMessageActivity.isActive)
    }

    @Test
    fun destructionDetachesAndDestroysWebView() {
        val old = create()
        val view = old.get().findViewById<NotiflyWebView>(R.id.webView)
        old.pause().stop().destroy()
        assertNull(view.parent)
        // Inspect the shadow; production code must never call WebView methods after destroy.
        assertTrue(shadowOf(view).wasDestroyCalled())
    }
}
