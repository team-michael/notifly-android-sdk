package tech.notifly.inapp

import android.content.Context
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import tech.notifly.inapp.models.Campaign
import tech.notifly.inapp.models.Message
import tech.notifly.inapp.models.Operator
import tech.notifly.inapp.models.TriggeringConditionOperator
import tech.notifly.inapp.models.TriggeringConditionType
import tech.notifly.inapp.models.TriggeringConditionUnit
import tech.notifly.inapp.models.TriggeringConditions
import tech.notifly.inapp.models.TriggeringEventFilterUnit
import tech.notifly.inapp.models.TriggeringEventFilters
import tech.notifly.inapp.models.ValueType
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config
class InAppMessageCancellationTest {
    private lateinit var context: Context

    @Before
    fun setup() {
        context = mockk<Context>(relaxed = true)
        // Clear scheduler state via reflection to ensure isolation between tests
        clearSchedulerState()
    }

    @After
    fun tearDown() {
        InAppMessageScheduler.descheduleAll()
        unmockkAll()
    }

    // ──────────────────────────────────────────────────────────
    // Helper: clear InAppMessageScheduler's scheduledCampaigns via reflection
    // ──────────────────────────────────────────────────────────

    private fun clearSchedulerState() {
        val field = InAppMessageScheduler::class.java.getDeclaredField("scheduledCampaigns")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val map = field.get(InAppMessageScheduler) as ConcurrentHashMap<String, Runnable>
        map.clear()
    }

    // ──────────────────────────────────────────────────────────
    // Helper: create a dummy Campaign for testing
    // ──────────────────────────────────────────────────────────

    private fun createDummyCampaign(
        id: String = "test_campaign_id",
        delay: Int? = 5,
        cancellationConditions: TriggeringConditions? = null,
        cancellationEventFilters: TriggeringEventFilters? = null,
    ): Campaign {
        val message = mockk<Message>()
        every { message.url } returns "https://example.com/message"
        every { message.modalProperties } returns """{"key": "value"}"""
        every { message.templateName } returns "test_template"

        val triggeringConditionUnit = mockk<TriggeringConditionUnit>()
        every { triggeringConditionUnit.type } returns TriggeringConditionType.EVENT_NAME
        every { triggeringConditionUnit.operator } returns TriggeringConditionOperator.EQUALS
        every { triggeringConditionUnit.operand } returns "test_event"
        every { triggeringConditionUnit.match(any()) } returns true

        val triggeringConditions = mockk<TriggeringConditions>()
        every { triggeringConditions.conditions } returns listOf(listOf(triggeringConditionUnit))
        every { triggeringConditions.match(any()) } returns true

        return Campaign(
            id = id,
            channel = "in-app-message",
            updatedAt = "2023-09-04T11:35:23Z",
            testing = false,
            whitelist = null,
            start = 1693826123000,
            end = null,
            message = message,
            segmentInfo = null,
            triggeringConditions = triggeringConditions,
            triggeringEventFilters = null,
            delay = delay,
            cancellationConditions = cancellationConditions,
            cancellationEventFilters = cancellationEventFilters,
        )
    }

    // ──────────────────────────────────────────────────────────
    // Helper: set InAppMessageManager.campaigns via reflection
    // ──────────────────────────────────────────────────────────

    private fun setCampaigns(campaigns: MutableList<Campaign>?) {
        val field = InAppMessageManager::class.java.getDeclaredField("campaigns")
        field.isAccessible = true
        field.set(InAppMessageManager, campaigns)
    }

    // ──────────────────────────────────────────────────────────
    // Helper: call InAppMessageManager.checkCancellationConditions via reflection
    // ──────────────────────────────────────────────────────────

    private fun callCheckCancellationConditions(
        eventName: String,
        eventParams: Map<String, Any?>,
    ) {
        val method =
            InAppMessageManager::class.java.getDeclaredMethod(
                "checkCancellationConditions",
                String::class.java,
                Map::class.java,
            )
        method.isAccessible = true
        method.invoke(InAppMessageManager, eventName, eventParams)
    }

    // ══════════════════════════════════════════════════════════
    //  1. InAppMessageScheduler tests
    // ══════════════════════════════════════════════════════════

    @Test
    fun `schedule with delay greater than 0 adds campaign to scheduledCampaigns`() {
        // Given
        val campaign = createDummyCampaign(id = "campaign_delayed", delay = 5)

        // Prevent show() from actually starting an Activity
        mockkObject(NotiflyInAppMessageActivity)
        every { NotiflyInAppMessageActivity.isActive } returns false

        // When
        InAppMessageScheduler.schedule(context, campaign)

        // Then
        val scheduledIds = InAppMessageScheduler.getScheduledCampaignIds()
        assertTrue(
            "scheduledCampaigns should contain the campaign id",
            scheduledIds.contains("campaign_delayed"),
        )
    }

    @Test
    fun `schedule with delay equal to 0 does NOT add to scheduledCampaigns`() {
        // Given: delay = 0 means show immediately
        val campaign = createDummyCampaign(id = "campaign_immediate", delay = 0)

        // Prevent show() from actually starting an Activity
        mockkObject(NotiflyInAppMessageActivity)
        every { NotiflyInAppMessageActivity.isActive } returns true

        // When
        InAppMessageScheduler.schedule(context, campaign)

        // Then
        val scheduledIds = InAppMessageScheduler.getScheduledCampaignIds()
        assertFalse(
            "scheduledCampaigns should NOT contain the campaign id when delay = 0",
            scheduledIds.contains("campaign_immediate"),
        )
    }

    @Test
    fun `schedule with null delay does NOT add to scheduledCampaigns`() {
        // Given: null delay is treated as 0
        val campaign = createDummyCampaign(id = "campaign_null_delay", delay = null)

        mockkObject(NotiflyInAppMessageActivity)
        every { NotiflyInAppMessageActivity.isActive } returns true

        // When
        InAppMessageScheduler.schedule(context, campaign)

        // Then
        val scheduledIds = InAppMessageScheduler.getScheduledCampaignIds()
        assertFalse(
            "scheduledCampaigns should NOT contain the campaign id when delay is null",
            scheduledIds.contains("campaign_null_delay"),
        )
    }

    @Test
    fun `getScheduledCampaignIds returns correct IDs`() {
        // Given
        mockkObject(NotiflyInAppMessageActivity)
        every { NotiflyInAppMessageActivity.isActive } returns false

        val campaign1 = createDummyCampaign(id = "camp_a", delay = 3)
        val campaign2 = createDummyCampaign(id = "camp_b", delay = 5)

        InAppMessageScheduler.schedule(context, campaign1)
        InAppMessageScheduler.schedule(context, campaign2)

        // When
        val ids = InAppMessageScheduler.getScheduledCampaignIds()

        // Then
        assertEquals(2, ids.size)
        assertTrue("Should contain camp_a", ids.contains("camp_a"))
        assertTrue("Should contain camp_b", ids.contains("camp_b"))
    }

    @Test
    fun `deschedule removes campaign from scheduledCampaigns`() {
        // Given
        mockkObject(NotiflyInAppMessageActivity)
        every { NotiflyInAppMessageActivity.isActive } returns false

        val campaign = createDummyCampaign(id = "campaign_to_remove", delay = 10)
        InAppMessageScheduler.schedule(context, campaign)
        assertTrue(
            "Precondition: campaign should be scheduled",
            InAppMessageScheduler.getScheduledCampaignIds().contains("campaign_to_remove"),
        )

        // When
        InAppMessageScheduler.deschedule("campaign_to_remove")

        // Then
        assertFalse(
            "scheduledCampaigns should no longer contain the descheduled campaign",
            InAppMessageScheduler.getScheduledCampaignIds().contains("campaign_to_remove"),
        )
    }

    @Test
    fun `descheduleAll clears all scheduled campaigns`() {
        // Given
        mockkObject(NotiflyInAppMessageActivity)
        every { NotiflyInAppMessageActivity.isActive } returns false

        InAppMessageScheduler.schedule(context, createDummyCampaign(id = "camp_x", delay = 3))
        InAppMessageScheduler.schedule(context, createDummyCampaign(id = "camp_y", delay = 5))
        assertEquals(
            "Precondition: two campaigns should be scheduled",
            2,
            InAppMessageScheduler.getScheduledCampaignIds().size,
        )

        // When
        InAppMessageScheduler.descheduleAll()

        // Then
        assertTrue(
            "scheduledCampaigns should be empty after descheduleAll",
            InAppMessageScheduler.getScheduledCampaignIds().isEmpty(),
        )
    }

    @Test
    fun `duplicate scheduling cancels the previous runnable to prevent ghost messages`() {
        // Given
        mockkObject(NotiflyInAppMessageActivity)
        every { NotiflyInAppMessageActivity.isActive } returns false

        val campaign1 = createDummyCampaign(id = "dup_campaign", delay = 10)
        InAppMessageScheduler.schedule(context, campaign1)

        // Access the underlying map to capture the first Runnable reference
        val field = InAppMessageScheduler::class.java.getDeclaredField("scheduledCampaigns")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val map = field.get(InAppMessageScheduler) as ConcurrentHashMap<String, Runnable>
        val firstRunnable = map["dup_campaign"]

        // When: schedule the same campaign ID again (e.g., with a different delay)
        val campaign2 = createDummyCampaign(id = "dup_campaign", delay = 20)
        InAppMessageScheduler.schedule(context, campaign2)

        // Then: there should still be exactly one entry, and the Runnable should differ
        val ids = InAppMessageScheduler.getScheduledCampaignIds()
        assertEquals(
            "Only one entry for the same campaign id",
            1,
            ids.filter { it == "dup_campaign" }.size,
        )
        val secondRunnable = map["dup_campaign"]
        assertTrue(
            "The Runnable should have been replaced (old one cancelled)",
            firstRunnable !== secondRunnable,
        )
    }

    @Test
    fun `scheduled campaign runnable is removed from map after timer fires`() {
        // Given
        mockkObject(NotiflyInAppMessageActivity)
        // Prevent the Activity from actually launching but allow the runnable to execute
        every { NotiflyInAppMessageActivity.isActive } returns true

        val campaign = createDummyCampaign(id = "timer_campaign", delay = 2)
        InAppMessageScheduler.schedule(context, campaign)
        assertTrue(
            "Precondition: campaign should be in scheduledCampaigns",
            InAppMessageScheduler.getScheduledCampaignIds().contains("timer_campaign"),
        )

        // When: advance the main looper past the delay (2 seconds = 2000ms)
        ShadowLooper.idleMainLooper(2000, TimeUnit.MILLISECONDS)

        // Then: runnable self-removes from scheduledCampaigns
        assertFalse(
            "Campaign should be removed from scheduledCampaigns after timer fires",
            InAppMessageScheduler.getScheduledCampaignIds().contains("timer_campaign"),
        )
    }

    // ══════════════════════════════════════════════════════════
    //  2. checkCancellationConditions tests (via reflection on InAppMessageManager)
    // ══════════════════════════════════════════════════════════

    @Test
    fun `checkCancellationConditions - matching cancellation event deschedules the campaign`() {
        // Given
        mockkObject(InAppMessageScheduler)

        val cancellationConditions = mockk<TriggeringConditions>()
        every { cancellationConditions.match("cancel_event") } returns true

        val campaign =
            createDummyCampaign(
                id = "cancel_me",
                delay = 5,
                cancellationConditions = cancellationConditions,
            )
        setCampaigns(mutableListOf(campaign))

        every { InAppMessageScheduler.getScheduledCampaignIds() } returns listOf("cancel_me")
        every { InAppMessageScheduler.deschedule(any()) } just runs

        // When
        callCheckCancellationConditions("cancel_event", emptyMap())

        // Then
        verify(exactly = 1) { InAppMessageScheduler.deschedule("cancel_me") }
    }

    @Test
    fun `checkCancellationConditions - non-matching cancellation event does NOT deschedule`() {
        // Given
        mockkObject(InAppMessageScheduler)

        val cancellationConditions = mockk<TriggeringConditions>()
        every { cancellationConditions.match("some_other_event") } returns false

        val campaign =
            createDummyCampaign(
                id = "keep_me",
                delay = 5,
                cancellationConditions = cancellationConditions,
            )
        setCampaigns(mutableListOf(campaign))

        every { InAppMessageScheduler.getScheduledCampaignIds() } returns listOf("keep_me")
        every { InAppMessageScheduler.deschedule(any()) } just runs

        // When
        callCheckCancellationConditions("some_other_event", emptyMap())

        // Then
        verify(exactly = 0) { InAppMessageScheduler.deschedule(any()) }
    }

    @Test
    fun `checkCancellationConditions - campaign without cancellationConditions is skipped`() {
        // Given
        mockkObject(InAppMessageScheduler)

        // Campaign has no cancellationConditions (null)
        val campaign =
            createDummyCampaign(
                id = "no_cancellation",
                delay = 5,
                cancellationConditions = null,
            )
        setCampaigns(mutableListOf(campaign))

        every { InAppMessageScheduler.getScheduledCampaignIds() } returns listOf("no_cancellation")
        every { InAppMessageScheduler.deschedule(any()) } just runs

        // When
        callCheckCancellationConditions("any_event", emptyMap())

        // Then
        verify(exactly = 0) { InAppMessageScheduler.deschedule(any()) }
    }

    @Test
    fun `checkCancellationConditions - matching cancellationEventFilters deschedules campaign`() {
        // Given
        mockkObject(InAppMessageScheduler)

        val cancellationConditions = mockk<TriggeringConditions>()
        every { cancellationConditions.match("cancel_event") } returns true

        val cancellationEventFilters = mockk<TriggeringEventFilters>()
        // The matchTriggeringEventFilters checks filters.filters.any { group.all { unit matches } }
        // We mock the TriggeringEventFilters to have a filter that matches
        every { cancellationEventFilters.filters } returns listOf(emptyList())

        val campaign =
            createDummyCampaign(
                id = "filtered_cancel",
                delay = 5,
                cancellationConditions = cancellationConditions,
                cancellationEventFilters = cancellationEventFilters,
            )
        setCampaigns(mutableListOf(campaign))

        every { InAppMessageScheduler.getScheduledCampaignIds() } returns listOf("filtered_cancel")
        every { InAppMessageScheduler.deschedule(any()) } just runs

        // When: event params match the filter (empty filter group = all match vacuously true)
        callCheckCancellationConditions("cancel_event", mapOf("key" to "value"))

        // Then: an empty filter group means all() returns true, so the campaign should be descheduled
        verify(exactly = 1) { InAppMessageScheduler.deschedule("filtered_cancel") }
    }

    @Test
    fun `checkCancellationConditions - non-matching cancellationEventFilters does NOT deschedule`() {
        // Given
        mockkObject(InAppMessageScheduler)

        val cancellationConditions = mockk<TriggeringConditions>()
        every { cancellationConditions.match("cancel_event") } returns true

        // Build a real TriggeringEventFilters with a filter that won't match
        val filterUnit =
            TriggeringEventFilterUnit(
                key = "status",
                operator = Operator.EQUALS,
                value = "cancelled",
                valueType = ValueType.TEXT,
            )
        val cancellationEventFilters =
            TriggeringEventFilters(
                filters = listOf(listOf(filterUnit)),
            )

        val campaign =
            createDummyCampaign(
                id = "filtered_no_cancel",
                delay = 5,
                cancellationConditions = cancellationConditions,
                cancellationEventFilters = cancellationEventFilters,
            )
        setCampaigns(mutableListOf(campaign))

        every { InAppMessageScheduler.getScheduledCampaignIds() } returns listOf("filtered_no_cancel")
        every { InAppMessageScheduler.deschedule(any()) } just runs

        // When: event params do NOT match the filter (status is "active", not "cancelled")
        callCheckCancellationConditions("cancel_event", mapOf("status" to "active"))

        // Then
        verify(exactly = 0) { InAppMessageScheduler.deschedule(any()) }
    }

    @Test
    fun `checkCancellationConditions - no scheduled campaigns is a no-op`() {
        // Given
        mockkObject(InAppMessageScheduler)

        every { InAppMessageScheduler.getScheduledCampaignIds() } returns emptyList()

        val cancellationConditions = mockk<TriggeringConditions>()
        val campaign =
            createDummyCampaign(
                id = "orphan_campaign",
                delay = 5,
                cancellationConditions = cancellationConditions,
            )
        setCampaigns(mutableListOf(campaign))

        // When
        callCheckCancellationConditions("cancel_event", emptyMap())

        // Then: deschedule should never be called (early return)
        verify(exactly = 0) { InAppMessageScheduler.deschedule(any()) }
        // Also, cancellationConditions.match should never be called
        verify(exactly = 0) { cancellationConditions.match(any()) }
    }

    @Test
    fun `checkCancellationConditions - campaigns is null is a no-op`() {
        // Given
        mockkObject(InAppMessageScheduler)

        every { InAppMessageScheduler.getScheduledCampaignIds() } returns listOf("some_id")

        // Set campaigns to null
        setCampaigns(null)

        // When
        callCheckCancellationConditions("cancel_event", emptyMap())

        // Then: deschedule should not be called since campaigns is null
        verify(exactly = 0) { InAppMessageScheduler.deschedule(any()) }
    }

    @Test
    fun `checkCancellationConditions - only matching campaign is descheduled among multiple`() {
        // Given
        mockkObject(InAppMessageScheduler)

        val matchingCancellation = mockk<TriggeringConditions>()
        every { matchingCancellation.match("cancel_event") } returns true

        val nonMatchingCancellation = mockk<TriggeringConditions>()
        every { nonMatchingCancellation.match("cancel_event") } returns false

        val campaignA =
            createDummyCampaign(
                id = "campaign_a",
                delay = 5,
                cancellationConditions = matchingCancellation,
            )
        val campaignB =
            createDummyCampaign(
                id = "campaign_b",
                delay = 10,
                cancellationConditions = nonMatchingCancellation,
            )
        val campaignC =
            createDummyCampaign(
                id = "campaign_c",
                delay = 3,
                cancellationConditions = null,
            )
        setCampaigns(mutableListOf(campaignA, campaignB, campaignC))

        every { InAppMessageScheduler.getScheduledCampaignIds() } returns
            listOf(
                "campaign_a",
                "campaign_b",
                "campaign_c",
            )
        every { InAppMessageScheduler.deschedule(any()) } just runs

        // When
        callCheckCancellationConditions("cancel_event", emptyMap())

        // Then: only campaign_a should be descheduled
        verify(exactly = 1) { InAppMessageScheduler.deschedule("campaign_a") }
        verify(exactly = 0) { InAppMessageScheduler.deschedule("campaign_b") }
        verify(exactly = 0) { InAppMessageScheduler.deschedule("campaign_c") }
    }

    @Test
    fun `checkCancellationConditions - scheduled campaign not in campaigns list is skipped`() {
        // Given
        mockkObject(InAppMessageScheduler)

        val campaign =
            createDummyCampaign(
                id = "existing_campaign",
                delay = 5,
                cancellationConditions = null,
            )
        setCampaigns(mutableListOf(campaign))

        // Scheduler reports a campaign id that doesn't exist in the campaigns list
        every { InAppMessageScheduler.getScheduledCampaignIds() } returns listOf("nonexistent_campaign")
        every { InAppMessageScheduler.deschedule(any()) } just runs

        // When
        callCheckCancellationConditions("cancel_event", emptyMap())

        // Then
        verify(exactly = 0) { InAppMessageScheduler.deschedule(any()) }
    }
}
