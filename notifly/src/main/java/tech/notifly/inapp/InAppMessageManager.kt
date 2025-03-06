package tech.notifly.inapp

import android.content.Context
import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import org.json.JSONObject
import tech.notifly.application.IApplicationService
import tech.notifly.inapp.models.Campaign
import tech.notifly.inapp.models.Condition
import tech.notifly.inapp.models.EventBasedConditionType
import tech.notifly.inapp.models.EventIntermediateCounts
import tech.notifly.inapp.models.Operator
import tech.notifly.inapp.models.SegmentConditionUnitType
import tech.notifly.inapp.models.TriggeringEventFilterGroup
import tech.notifly.inapp.models.TriggeringEventFilterUnit
import tech.notifly.inapp.models.TriggeringEventFilters
import tech.notifly.inapp.models.UserData
import tech.notifly.inapp.models.ValueType
import tech.notifly.push.interfaces.IInAppMessageEventListener
import tech.notifly.sdk.NotiflySdkState
import tech.notifly.sdk.NotiflySdkStateManager
import tech.notifly.services.NotiflyServiceProvider
import tech.notifly.utils.Logger
import tech.notifly.utils.N
import tech.notifly.utils.NotiflySyncStateUtil
import tech.notifly.utils.NotiflyTimerUtil
import tech.notifly.utils.NotiflyUserUtil

object InAppMessageManager {
    private const val MINIMUM_CAMPAIGN_REVALIDATION_INTERVAL_MILLIS = 1000 * 60 * 3L // 3 minutes

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.R)
    val IS_IN_APP_MESSAGE_SUPPORTED = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    var disabled: Boolean = false

    @Volatile
    private var isInitialized = false

    private var campaigns: MutableList<Campaign>? = null
        set(value) =
            run {
                field = value
                campaignLastFetchedAt = NotiflyTimerUtil.getTimestampMillis()
            }

    private lateinit var eventCounts: MutableList<EventIntermediateCounts>
    private lateinit var userData: UserData

    var campaignRevalidationIntervalMillis: Long = 10 * 60 * 1000L // 10 minutes
        set(value) =
            run {
                if (value < MINIMUM_CAMPAIGN_REVALIDATION_INTERVAL_MILLIS) {
                    Logger.w(
                        "[Notifly] Campaign revalidation interval is less than the minimum value of $MINIMUM_CAMPAIGN_REVALIDATION_INTERVAL_MILLIS. Ignoring.",
                    )
                } else {
                    field = value
                }
            }
    private var campaignLastFetchedAt: Long? = null
    private val shouldRevalidateCampaign: Boolean
        get() =
            campaignLastFetchedAt == null ||
                NotiflyTimerUtil.getTimestampMillis() - campaignLastFetchedAt!! > campaignRevalidationIntervalMillis

    fun disable() {
        disabled = true
    }

    fun enable() {
        disabled = false
    }

    @Throws(NullPointerException::class)
    suspend fun initialize(context: Context) {
        if (disabled) {
            Logger.i("[Notifly] InAppMessage feature is disabled.")
            return
        }

        if (!IS_IN_APP_MESSAGE_SUPPORTED) {
            Logger.i("[Notifly] InAppMessageManager is not supported on this device.")
            return
        }

        try {
            NotiflySdkStateManager.setState(NotiflySdkState.REFRESHING)

            userData = UserData.getSkeleton(context)
            sync(context, false)
            isInitialized = true

            NotiflySdkStateManager.setState(NotiflySdkState.READY)
        } catch (e: Exception) {
            NotiflySdkStateManager.setState(NotiflySdkState.FAILED)
        }
    }

    @Throws(NullPointerException::class)
    suspend fun refresh(
        context: Context,
        shouldMergeData: Boolean = false,
    ) {
        if (disabled) {
            Logger.i("[Notifly] InAppMessage feature is disabled.")
            return
        }

        if (!IS_IN_APP_MESSAGE_SUPPORTED) {
            Logger.i("[Notifly] InAppMessageManager is not supported on this device.")
            return
        }

        if (!isInitialized) {
            Logger.w("[Notifly] InAppMessageManager is not initialized.")
            return
        }

        sync(context, shouldMergeData)
    }

    suspend fun maybeRevalidateCampaigns(context: Context) {
        if (disabled) {
            Logger.i("[Notifly] InAppMessage feature is disabled.")
            return
        }

        if (!IS_IN_APP_MESSAGE_SUPPORTED) {
            Logger.i("[Notifly] InAppMessageManager is not supported on this device.")
            return
        }

        if (!isInitialized) {
            Logger.w("[Notifly] InAppMessageManager is not initialized.")
            return
        }

        if (shouldRevalidateCampaign) {
            syncCampaigns(context)
            campaignLastFetchedAt = NotiflyTimerUtil.getTimestampMillis()
        } else {
            Logger.v("[Notifly] Not revalidating campaigns since the interval has not passed.")
        }
    }

    fun updateUserProperties(params: Map<String, Any?>) {
        if (disabled) {
            Logger.i("[Notifly] InAppMessage feature is disabled.")
            return
        }

        if (!IS_IN_APP_MESSAGE_SUPPORTED) {
            Logger.i("[Notifly] InAppMessageManager is not supported on this device.")
            return
        }

        if (!isInitialized) {
            Logger.w("[Notifly] InAppMessageManager is not initialized.")
            return
        }

        userData.userProperties += params
        Logger.v("[Notifly] Updated user data to $userData")
    }

    private fun isReEligibleCampaign(
        campaignId: String,
        delay: Int?,
        now: Int,
    ): Boolean {
        val hiddenUntil = userData.campaignHiddenUntil[campaignId]
        if (hiddenUntil != null && hiddenUntil < 0) {
            return false // Infinitely hidden
        }

        val sanitizedDelay = delay ?: 0
        val displayTime = now + sanitizedDelay

        return (hiddenUntil == null) || (displayTime >= hiddenUntil)
    }

    private fun isTemplateHiddenByUser(
        templateName: String,
        delay: Int?,
        now: Int,
    ): Boolean {
        val userProperties = userData.userProperties

        // Legacy
        if (userProperties["hide_in_app_message_$templateName"] == true) {
            Logger.d("InAppMessageManager: $templateName is hidden by user property.")
            return true
        }

        // New
        userProperties["hide_in_app_message_until_$templateName"]?.let {
            if (it is Int) {
                val sanitizedDelay = delay ?: 0
                val displayTime = now + sanitizedDelay

                if (it < 0) return true // Infinitely hidden
                if (displayTime < it) return true // Hidden until a certain time
            }
        }

        return false
    }

    fun updateHideUntilData(
        campaignId: String,
        hideUntil: Int,
    ) {
        if (disabled) {
            Logger.i("[Notifly] InAppMessage feature is disabled.")
            return
        }

        if (!IS_IN_APP_MESSAGE_SUPPORTED) {
            Logger.i("[Notifly] InAppMessageManager is not supported on this device.")
            return
        }

        if (!isInitialized) {
            Logger.w("[Notifly] InAppMessageManager is not initialized.")
            return
        }

        try {
            userData.campaignHiddenUntil[campaignId] = hideUntil
            Logger.d("[Notifly] Updating hideUntil to $userData")
        } catch (e: Exception) {
            Logger.e("[Notifly] updateHideUntilData failed", e)
        }
    }

    fun maybeScheduleInAppMessagesAndIngestEvent(
        context: Context,
        eventName: String,
        externalUserId: String?,
        eventParams: Map<String, Any?>,
        isInternalEvent: Boolean,
        segmentationEventParamKeys: List<String>? = null,
    ) {
        if (disabled) {
            Logger.i("[Notifly] InAppMessage feature is disabled.")
            return
        }

        if (!IS_IN_APP_MESSAGE_SUPPORTED) {
            Logger.i("[Notifly] In app message feature is not supported on this device.")
            return
        }

        if (!isInitialized) {
            Logger.w("[Notifly] InAppMessageManager is not initialized.")
            return
        }

        Logger.v(
            "[Notifly] maybeScheduleInAppMessagesAndIngestEvent called with $eventName, $externalUserId, $eventParams, $isInternalEvent, $segmentationEventParamKeys",
        )

        val applicationService = NotiflyServiceProvider.getService<IApplicationService>()
        val sanitizedEventName = sanitizeEventName(eventName, isInternalEvent)
        if (applicationService.isInForeground) {
            Logger.v("[Notifly] App is in foreground. Scheduling in app messages.")
            scheduleCampaigns(context, campaigns!!, externalUserId, sanitizedEventName, eventParams)
        }
        ingestEventInternal(sanitizedEventName, eventParams, segmentationEventParamKeys)
    }

    fun clearUserState() {
        eventCounts = mutableListOf()
        userData.apply {
            this.userProperties.clear()
            this.campaignHiddenUntil.clear()
        }
    }

    private fun sanitizeEventName(
        eventName: String,
        isInternalEvent: Boolean,
    ): String = if (isInternalEvent) "${N.INTERNAL_EVENT_PREFIX}$eventName" else eventName

    @Throws(NullPointerException::class)
    private suspend fun sync(
        context: Context,
        shouldMergeData: Boolean,
    ) {
        val syncStateResult = NotiflySyncStateUtil.fetchState(context)

        campaigns = syncStateResult.campaigns
        eventCounts =
            if (shouldMergeData) {
                NotiflyUserUtil.mergeEventCounts(eventCounts, syncStateResult.eventCounts)
            } else {
                syncStateResult.eventCounts
            }
        userData =
            if (shouldMergeData) {
                userData.merge(syncStateResult.userData)
            } else {
                syncStateResult.userData
            }

        Logger.d("InAppMessageManager fetched user state successfully.")
        Logger.d("campaigns: $campaigns")
        Logger.d("eventCounts: $eventCounts")
        Logger.d("userData: $userData")
    }

    private suspend fun syncCampaigns(context: Context) {
        try {
            campaigns = NotiflySyncStateUtil.fetchCampaigns(context)
        } catch (e: Exception) {
            Logger.e("Failed to fetch campaigns", e)
            NotiflySdkStateManager.setState(NotiflySdkState.FAILED)
        }
    }

    private fun ingestEventInternal(
        eventName: String,
        eventParams: Map<String, Any?>,
        segmentationEventParamKeys: List<String>? = null,
    ) {
        val formattedDate = InAppMessageUtils.getKSTCalendarDateString()
        val keyField = segmentationEventParamKeys?.getOrNull(0)
        val valueField = keyField?.let { eventParams[keyField] }

        val predicate: (EventIntermediateCounts) -> Boolean = { row ->
            if (keyField != null && valueField != null) {
                row.dt == formattedDate && row.name == eventName && row.eventParams[keyField] == valueField
            } else {
                row.dt == formattedDate && row.name == eventName
            }
        }
        val index = eventCounts.indexOfFirst(predicate)
        if (index != -1) {
            // If an existing row is found, increase the count by 1
            val row = eventCounts[index]
            eventCounts[index] = row.copy(count = row.count + 1)
        } else {
            // If no existing row is found, create a new entry
            eventCounts.add(
                EventIntermediateCounts(
                    dt = formattedDate,
                    name = eventName,
                    count = 1,
                    eventParams = eventParams,
                ),
            )
        }
    }

    private fun scheduleCampaigns(
        context: Context,
        campaigns: List<Campaign>,
        externalUserId: String?,
        eventName: String,
        eventParams: Map<String, Any?>,
    ) {
        getCampaignsToSchedule(context, campaigns, externalUserId, eventName, eventParams).forEach {
            Logger.v("[Notifly] Scheduling campaign: $it")
            InAppMessageScheduler.schedule(context, it)
        }
    }

    private fun getCampaignsToSchedule(
        context: Context,
        campaigns: List<Campaign>,
        externalUserId: String?,
        eventName: String,
        eventParams: Map<String, Any?>,
    ): List<Campaign> =
        filterCampaignsWithUniqueDelays(
            campaigns.filter {
                evaluateCampaignVisibility(context, it, externalUserId, eventName, eventParams)
            },
        )

    private fun filterCampaignsWithUniqueDelays(campaigns: List<Campaign>): List<Campaign> {
        if (campaigns.size <= 1) {
            return campaigns
        }

        val sortedCampaigns = campaigns.sorted()
        val result = mutableListOf(sortedCampaigns[0])

        var seenDelay = sortedCampaigns[0].delay ?: 0
        for (idx in 1 until sortedCampaigns.size) {
            val campaign = sortedCampaigns[idx]
            val delay = campaign.delay ?: 0

            if (delay != seenDelay) {
                result.add(campaign)
                seenDelay = delay
            }
        }

        return result
    }

    private fun evaluateCampaignVisibility(
        context: Context,
        campaign: Campaign,
        externalUserId: String?,
        eventName: String,
        eventParams: Map<String, Any?>,
    ): Boolean {
        val now = NotiflyTimerUtil.getTimestampSeconds()
        val templateName = campaign.message.templateName ?: return false
        val groups = campaign.segmentInfo?.conditionGroup

        if (!campaign.triggeringConditions.match(eventName)) {
            return false
        }

        if (campaign.start > now) {
            return false
        } else {
            if (campaign.end != null && campaign.end < now) {
                return false
            }
        }

        if (campaign.triggeringEventFilters != null &&
            !matchTriggeringEventFilters(
                eventParams,
                campaign.triggeringEventFilters,
            )
        ) {
            return false
        }

        if (campaign.testing) {
            if (externalUserId == null) {
                return false
            }
            val whitelist = campaign.whitelist!!
            if (whitelist.indexOf(externalUserId) == -1) {
                return false
            }
        }

        if (!isReEligibleCampaign(campaign.id, campaign.delay, now)) {
            return false
        }

        if (isTemplateHiddenByUser(templateName, campaign.delay, now)) {
            return false
        }

        if (groups.isNullOrEmpty()) {
            return true
        }

        if (groups.any { it.conditions.isEmpty() }) return false
        return groups.any {
            it.conditions.all { condition ->
                matchCondition(context, condition, eventParams)
            }
        }
    }

    private fun matchCondition(
        context: Context,
        condition: Condition,
        eventParams: Map<String, Any?>,
    ): Boolean =
        when (condition.unit) {
            SegmentConditionUnitType.EVENT -> matchEventBasedCondition(condition)
            else -> matchUserPropertyBasedCondition(context, condition, eventParams)
        }

    private fun matchEventBasedCondition(condition: Condition): Boolean {
        val event = condition.event!!
        val eventConditionType = condition.eventConditionType!!
        val operator = condition.operator
        val value =
            condition.value.let {
                if (it !is Int) {
                    Logger.w("[Notifly] Malformed condition: value is not an integer.")
                    return false
                } else {
                    it
                }
            }

        val totalCount: Int =
            when (eventConditionType) {
                EventBasedConditionType.COUNT_X -> {
                    eventCounts.filter { it.name == event }.sumOf { it.count }
                }

                EventBasedConditionType.COUNT_X_IN_Y_DAYS -> {
                    val secondaryValue = condition.secondaryValue ?: return false
                    val start = InAppMessageUtils.getKSTCalendarDateString(-secondaryValue)
                    val end = InAppMessageUtils.getKSTCalendarDateString()
                    eventCounts
                        .filter { it.name == event }
                        .filter { it.dt in start..end }
                        .sumOf { it.count }
                }
            }

        return compareEventBasedCondition(totalCount, operator, value)
    }

    private fun matchUserPropertyBasedCondition(
        context: Context,
        condition: Condition,
        eventParams: Map<String, Any?>,
    ): Boolean {
        val unit = condition.unit
        val operator = condition.operator
        val valueType = condition.valueType ?: return false
        val useEventParamsAsConditionValue = condition.useEventParamsAsConditionValue ?: false

        val userAttributeValue = userData.get(context, unit, condition.attribute)
        val value =
            if (useEventParamsAsConditionValue) {
                val comparisonParameter = condition.comparisonParameter ?: return false
                eventParams[comparisonParameter] ?: return false
            } else {
                condition.value
            }

        if (operator == Operator.IS_NULL || operator == Operator.IS_NOT_NULL) {
            return when (operator) {
                Operator.IS_NULL -> !isValuePresent(userAttributeValue)
                Operator.IS_NOT_NULL -> isValuePresent(userAttributeValue)
                else -> false // Should never happen
            }
        }

        if (userAttributeValue == null || value == null) {
            return false
        }

        return when (operator) {
            Operator.EQUALS -> {
                Comparator.isEqual(
                    userAttributeValue,
                    value,
                    valueType,
                )
            }

            Operator.NOT_EQUALS -> {
                Comparator.isNotEqual(
                    userAttributeValue,
                    value,
                    valueType,
                )
            }

            Operator.GREATER_THAN -> {
                Comparator.isGreaterThan(
                    userAttributeValue,
                    value,
                    valueType,
                )
            }

            Operator.GREATER_THAN_OR_EQUAL -> {
                Comparator.isGreaterThanOrEqual(
                    userAttributeValue,
                    value,
                    valueType,
                )
            }

            Operator.LESS_THAN -> {
                Comparator.isLessThan(
                    userAttributeValue,
                    value,
                    valueType,
                )
            }

            Operator.LESS_THAN_OR_EQUAL -> {
                Comparator.isLessThanOrEqual(
                    userAttributeValue,
                    value,
                    valueType,
                )
            }

            Operator.CONTAINS -> {
                Comparator.contains(
                    userAttributeValue,
                    value,
                    valueType,
                )
            }

            else -> {
                false
            } // Should never happen
        }
    }

    private fun compareEventBasedCondition(
        count: Int,
        op: Operator,
        value: Int,
    ): Boolean =
        when (op) {
            Operator.EQUALS -> count == value
            Operator.GREATER_THAN -> count > value
            Operator.GREATER_THAN_OR_EQUAL -> count >= value
            Operator.LESS_THAN -> count < value
            Operator.LESS_THAN_OR_EQUAL -> count <= value
            else -> false
        }

    private fun matchTriggeringEventFilters(
        eventParams: Map<String, Any?>,
        filters: TriggeringEventFilters,
    ): Boolean =
        filters.filters.any {
            matchTriggeringEventFilterGroup(eventParams, it)
        }

    private fun matchTriggeringEventFilterGroup(
        eventParams: Map<String, Any?>,
        group: TriggeringEventFilterGroup,
    ): Boolean = group.all { matchTriggeringEventFilter(eventParams, it) }

    private fun matchTriggeringEventFilter(
        eventParams: Map<String, Any?>,
        filter: TriggeringEventFilterUnit,
    ): Boolean {
        val keyName = filter.key
        val operator = filter.operator
        val valueType = filter.valueType
        val value = filter.value

        val eventParamValue = eventParams[keyName]

        if (operator == Operator.IS_NULL || operator == Operator.IS_NOT_NULL) {
            return when (operator) {
                Operator.IS_NULL -> !isValuePresent(eventParamValue)
                Operator.IS_NOT_NULL -> isValuePresent(eventParamValue)
                else -> false // Should never happen
            }
        }

        if (eventParamValue == null || value == null || valueType == null) {
            return false
        }

        return when (operator) {
            Operator.EQUALS -> {
                Comparator.isEqual(
                    eventParamValue,
                    value,
                    valueType,
                )
            }

            Operator.NOT_EQUALS -> {
                Comparator.isNotEqual(
                    eventParamValue,
                    value,
                    valueType,
                )
            }

            Operator.GREATER_THAN -> {
                Comparator.isGreaterThan(
                    eventParamValue,
                    value,
                    valueType,
                )
            }

            Operator.GREATER_THAN_OR_EQUAL -> {
                Comparator.isGreaterThanOrEqual(
                    eventParamValue,
                    value,
                    valueType,
                )
            }

            Operator.LESS_THAN -> {
                Comparator.isLessThan(
                    eventParamValue,
                    value,
                    valueType,
                )
            }

            Operator.LESS_THAN_OR_EQUAL -> {
                Comparator.isLessThanOrEqual(
                    eventParamValue,
                    value,
                    valueType,
                )
            }

            Operator.CONTAINS -> {
                Comparator.contains(
                    eventParamValue,
                    value,
                    valueType,
                )
            }

            else -> {
                false
            } // Should never happen
        }
    }

    private class Comparator {
        companion object {
            @Throws(ClassCastException::class, NumberFormatException::class)
            private fun cast(
                value: Any,
                type: String,
            ): Any =
                when (type) {
                    "TEXT" -> {
                        value as? String ?: value.toString()
                    }

                    "INT" -> {
                        when (value) {
                            is Int -> value
                            is String -> value.toIntOrNull() ?: throw NumberFormatException()
                            else -> throw ClassCastException()
                        }
                    }

                    "BOOL" -> {
                        when (value) {
                            is Boolean -> {
                                value
                            }

                            is String -> {
                                when (value) {
                                    "true" -> true
                                    "false" -> false
                                    else -> throw ClassCastException()
                                }
                            }

                            else -> {
                                throw ClassCastException()
                            }
                        }
                    }

                    "ARRAY" -> {
                        value as? List<*> ?: throw ClassCastException()
                    }

                    else -> {
                        throw ClassCastException("Unrecoverable type mismatch: invalid type $type")
                    }
                }

            fun isEqual(
                a: Any,
                b: Any,
                type: ValueType,
            ): Boolean =
                try {
                    when (type) {
                        ValueType.TEXT -> {
                            cast(
                                a,
                                type.toString(),
                            ) as String == cast(b, type.toString()) as String
                        }

                        ValueType.INT -> {
                            cast(a, type.toString()) as Int ==
                                cast(
                                    b,
                                    type.toString(),
                                ) as Int
                        }

                        ValueType.BOOL -> {
                            cast(
                                a,
                                type.toString(),
                            ) as Boolean == cast(b, type.toString()) as Boolean
                        }
                    }
                } catch (error: Exception) {
                    Logger.d("[Notifly] ${error.message}")
                    false
                }

            fun isNotEqual(
                a: Any,
                b: Any,
                type: ValueType,
            ): Boolean =
                try {
                    when (type) {
                        ValueType.TEXT -> {
                            cast(
                                a,
                                type.toString(),
                            ) as String != cast(b, type.toString()) as String
                        }

                        ValueType.INT -> {
                            cast(a, type.toString()) as Int !=
                                cast(
                                    b,
                                    type.toString(),
                                ) as Int
                        }

                        ValueType.BOOL -> {
                            cast(
                                a,
                                type.toString(),
                            ) as Boolean != cast(b, type.toString()) as Boolean
                        }
                    }
                } catch (error: Exception) {
                    Logger.d("[Notifly] ${error.message}")
                    false
                }

            fun isGreaterThan(
                a: Any,
                b: Any,
                type: ValueType,
            ): Boolean =
                try {
                    when (type) {
                        ValueType.TEXT -> {
                            cast(a, type.toString()) as String >
                                cast(
                                    b,
                                    type.toString(),
                                ) as String
                        }

                        ValueType.INT -> {
                            cast(a, type.toString()) as Int >
                                cast(
                                    b,
                                    type.toString(),
                                ) as Int
                        }

                        ValueType.BOOL -> {
                            cast(
                                a,
                                type.toString(),
                            ) as Boolean > cast(b, type.toString()) as Boolean
                        }
                    }
                } catch (error: Exception) {
                    Logger.d("[Notifly] ${error.message}")
                    false
                }

            fun isGreaterThanOrEqual(
                a: Any,
                b: Any,
                type: ValueType,
            ): Boolean =
                try {
                    when (type) {
                        ValueType.TEXT -> {
                            cast(
                                a,
                                type.toString(),
                            ) as String >= cast(b, type.toString()) as String
                        }

                        ValueType.INT -> {
                            cast(a, type.toString()) as Int >=
                                cast(
                                    b,
                                    type.toString(),
                                ) as Int
                        }

                        ValueType.BOOL -> {
                            cast(
                                a,
                                type.toString(),
                            ) as Boolean >= cast(b, type.toString()) as Boolean
                        }
                    }
                } catch (error: Exception) {
                    Logger.d("[Notifly] ${error.message}")
                    false
                }

            fun isLessThan(
                a: Any,
                b: Any,
                type: ValueType,
            ): Boolean =
                try {
                    when (type) {
                        ValueType.TEXT -> {
                            (
                                cast(
                                    a,
                                    type.toString(),
                                ) as String
                            ) < (cast(b, type.toString()) as String)
                        }

                        ValueType.INT -> {
                            (
                                cast(
                                    a,
                                    type.toString(),
                                ) as Int
                            ) < (cast(b, type.toString()) as Int)
                        }

                        ValueType.BOOL -> {
                            (
                                cast(
                                    a,
                                    type.toString(),
                                ) as Boolean
                            ) < (cast(b, type.toString()) as Boolean)
                        }
                    }
                } catch (error: Exception) {
                    Logger.d("[Notifly] ${error.message}")
                    false
                }

            fun isLessThanOrEqual(
                a: Any,
                b: Any,
                type: ValueType,
            ): Boolean =
                try {
                    when (type) {
                        ValueType.TEXT -> {
                            (
                                cast(
                                    a,
                                    type.toString(),
                                ) as String
                            ) <= (cast(b, type.toString()) as String)
                        }

                        ValueType.INT -> {
                            (cast(a, type.toString()) as Int) <= (
                                cast(
                                    b,
                                    type.toString(),
                                ) as Int
                            )
                        }

                        ValueType.BOOL -> {
                            (
                                cast(
                                    a,
                                    type.toString(),
                                ) as Boolean
                            ) <= (cast(b, type.toString()) as Boolean)
                        }
                    }
                } catch (error: Exception) {
                    Logger.d("[Notifly] ${error.message}")
                    false
                }

            fun contains(
                a: Any,
                b: Any,
                type: ValueType,
            ): Boolean =
                try {
                    val castedA = cast(a, "ARRAY") as List<*>
                    when (type) {
                        ValueType.TEXT -> {
                            castedA.contains(
                                cast(
                                    b,
                                    type.toString(),
                                ) as String,
                            )
                        }

                        ValueType.INT -> {
                            castedA.contains(
                                cast(
                                    b,
                                    type.toString(),
                                ) as Int,
                            )
                        }

                        ValueType.BOOL -> {
                            castedA.contains(
                                cast(
                                    b,
                                    type.toString(),
                                ) as Boolean,
                            )
                        }
                    }
                } catch (error: Exception) {
                    Logger.d("[Notifly] ${error.message}")
                    false
                }
        }
    }

    private fun isValuePresent(value: Any?): Boolean =
        when (value) {
            null -> false
            is String -> value.isNotEmpty()
            else -> false
        }

    private val eventListeners = mutableListOf<IInAppMessageEventListener>()

    fun addEventListener(listener: IInAppMessageEventListener) {
        eventListeners.add(listener)
    }

    fun removeEventListener(listener: IInAppMessageEventListener) = eventListeners.remove(listener)

    internal fun dispatchInAppMessageEvent(
        eventName: String,
        elementName: String?,
        extraData: JSONObject?,
    ) {
        eventListeners.forEach {
            it.handleEvent(eventName, elementName, extraData)
        }
    }
}
