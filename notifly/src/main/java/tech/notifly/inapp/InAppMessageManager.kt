package tech.notifly.inapp

import android.content.Context
import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import tech.notifly.inapp.models.Campaign
import tech.notifly.inapp.models.Condition
import tech.notifly.inapp.models.EventBasedConditionType
import tech.notifly.inapp.models.EventIntermediateCounts
import tech.notifly.inapp.models.SegmentConditionUnitType
import tech.notifly.inapp.models.ValueType
import tech.notifly.inapp.models.Operator
import tech.notifly.inapp.models.UserData
import tech.notifly.utils.Logger
import tech.notifly.utils.N
import tech.notifly.utils.NotiflySyncStateUtil
import tech.notifly.utils.NotiflyUserUtil
import tech.notifly.utils.OSUtils
import kotlin.math.floor

object InAppMessageManager {
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.R)
    val IS_IN_APP_MESSAGE_SUPPORTED = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    var disabled: Boolean = false

    @Volatile
    private var isInitialized = false

    private lateinit var campaigns: MutableList<Campaign>
    private lateinit var eventCounts: MutableList<EventIntermediateCounts>
    private var userData: UserData? = null

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
        sync(context, false)
        isInitialized = true
    }

    @Throws(NullPointerException::class)
    suspend fun refresh(context: Context, shouldMergeData: Boolean = false) {
        if (disabled) {
            Logger.i("[Notifly] InAppMessage feature is disabled.")
            return
        }

        if (!IS_IN_APP_MESSAGE_SUPPORTED) {
            Logger.i("[Notifly] InAppMessageManager is not supported on this device.")
            return
        }

        if (!isInitialized) {
            Logger.e("[Notifly] InAppMessageManager is not initialized.")
            return
        }

        try {
            sync(context, shouldMergeData)
        } catch (e: Exception) {
            Logger.e("[Notifly] InAppMessageManager refresh failed", e)
            throw e
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
            Logger.e("[Notifly] InAppMessageManager is not initialized.")
            return
        }


        userData!!.userProperties!! += params
        Logger.v("[Notifly] Updated user data to $userData")
    }

    private fun isReEligibleCampaign(campaignId: String, delay: Int?, now: Int): Boolean {
        if (userData == null) return false

        val hiddenUntil = userData!!.campaignHiddenUntil[campaignId]
        if (hiddenUntil != null && hiddenUntil < 0) {
            return false // Infinitely hidden
        }

        val sanitizedDelay = delay ?: 0
        val displayTime = now + sanitizedDelay

        return (hiddenUntil == null) || (displayTime >= hiddenUntil)
    }

    private fun isTemplateHiddenByUser(templateName: String, delay: Int?, now: Int): Boolean {
        val userProperties = userData!!.userProperties!!

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

    fun updateHideUntilData(campaignId: String, hideUntil: Int) {
        if (disabled) {
            Logger.i("[Notifly] InAppMessage feature is disabled.")
            return
        }

        if (!IS_IN_APP_MESSAGE_SUPPORTED) {
            Logger.i("[Notifly] InAppMessageManager is not supported on this device.")
            return
        }

        if (!isInitialized) {
            Logger.e("[Notifly] InAppMessageManager is not initialized.")
            return
        }

        try {
            userData!!.campaignHiddenUntil[campaignId] = hideUntil
            Logger.d("[Notifly] Updating hideUntil to $userData")
        } catch (e: Exception) {
            Logger.e("[Notifly] updateHideUntilData failed", e)
        }
    }

    fun maybeScheduleInWebMessagesAndIngestEvent(
        context: Context,
        eventName: String,
        externalUserId: String?,
        eventParams: Map<String, Any?>,
        isInternalEvent: Boolean,
        segmentationEventParamKeys: List<String>? = null
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
            Logger.e("[Notifly] InAppMessageManager is not initialized.")
            return
        }

        val sanitizedEventName = sanitizeEventName(eventName, isInternalEvent)
        if (OSUtils.isAppInForeground(context)) {
            scheduleCampaigns(context, campaigns, externalUserId, sanitizedEventName, eventParams)
        } else {
            Logger.d("[Notifly] App is not in foreground. Not scheduling in app messages.")
        }
        ingestEventInternal(sanitizedEventName, eventParams, segmentationEventParamKeys)
    }

    fun clearUserState() {
        eventCounts = mutableListOf()
        userData.apply {
            this?.userProperties?.clear()
            this?.campaignHiddenUntil?.clear()
        }
    }

    private fun sanitizeEventName(eventName: String, isInternalEvent: Boolean): String {
        return if (isInternalEvent) "${N.INTERNAL_EVENT_PREFIX}$eventName" else eventName
    }

    @Throws(NullPointerException::class)
    private suspend fun sync(context: Context, shouldMergeData: Boolean) {
        val syncStateResult = NotiflySyncStateUtil.syncState(context)

        campaigns = syncStateResult.campaigns
        eventCounts = if (shouldMergeData) {
            NotiflyUserUtil.mergeEventCounts(eventCounts, syncStateResult.eventCounts)
        } else {
            syncStateResult.eventCounts
        }
        userData = if (shouldMergeData) {
            userData?.merge(syncStateResult.userData) ?: syncStateResult.userData
        } else {
            syncStateResult.userData
        }

        Logger.d("InAppMessageManager fetched user state successfully.")
        Logger.d("campaigns: $campaigns")
        Logger.d("eventCounts: $eventCounts")
        Logger.d("userData: $userData")
    }

    private fun ingestEventInternal(
        eventName: String,
        eventParams: Map<String, Any?>,
        segmentationEventParamKeys: List<String>? = null
    ) {
        val formattedDate = InAppMessageUtils.getKSTCalendarDateString()
        val keyField = segmentationEventParamKeys?.getOrNull(0)
        val valueField = keyField?.let { eventParams[keyField] }

        val predicate: (EventIntermediateCounts) -> Boolean = { row ->
            if (keyField != null && valueField != null) {
                row.dt == formattedDate && row.name == eventName && row.event_params[keyField] == valueField
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
                    dt = formattedDate, name = eventName, count = 1, event_params = eventParams
                )
            )
        }
    }

    private fun scheduleCampaigns(
        context: Context,
        campaigns: List<Campaign>,
        externalUserId: String?,
        eventName: String,
        eventParams: Map<String, Any?>
    ) {
        getCampaignsToSchedule(campaigns, externalUserId, eventName, eventParams).forEach {
            InAppMessageScheduler.schedule(context, it)
        }
    }

    private fun getCampaignsToSchedule(
        campaigns: List<Campaign>,
        externalUserId: String?,
        eventName: String,
        eventParams: Map<String, Any?>
    ): List<Campaign> {
        return filterCampaignsWithUniqueDelays(campaigns.filter {
            evaluateCampaignVisibility(it, externalUserId, eventName, eventParams)
        })
    }

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
        campaign: Campaign,
        externalUserId: String?,
        eventName: String,
        eventParams: Map<String, Any?>
    ): Boolean {
        if (campaign.triggeringEvent != eventName) {
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

        val now = floor(System.currentTimeMillis().toDouble() / 1000.0).toInt()
        if (!isReEligibleCampaign(campaign.id, campaign.delay, now)) {
            return false
        }

        val templateName = campaign.message.templateName ?: return false
        if (isTemplateHiddenByUser(templateName, campaign.delay, now)) {
            return false
        }

        val groups = campaign.segmentInfo?.groups
        if (groups.isNullOrEmpty()) {
            return true
        }

        if (campaign.start > now) {
            return false
        } else {
            if (campaign.end != null && campaign.end < now) {
                return false
            }
        }

        if (groups.any { it.conditions.isEmpty() }) return false
        return groups.any {
            it.conditions.all { condition ->
                matchCondition(condition, eventParams)
            }
        }
    }

    private fun matchCondition(condition: Condition, eventParams: Map<String, Any?>): Boolean {
        return when (condition.unit) {
            SegmentConditionUnitType.EVENT -> matchEventBasedCondition(condition)
            else -> matchUserPropertyBasedCondition(condition, eventParams)
        }
    }

    private fun matchEventBasedCondition(condition: Condition): Boolean {
        val event = condition.event!!
        val eventConditionType = condition.eventConditionType!!
        val operator = condition.operator
        val value = condition.value.let {
            if (it !is Int) {
                Logger.w("[Notifly] Malformed condition: value is not an integer.")
                return false
            } else {
                it
            }
        }

        val totalCount: Int = when (eventConditionType) {
            EventBasedConditionType.COUNT_X -> {
                eventCounts.filter { it.name == event }.sumOf { it.count }
            }

            EventBasedConditionType.COUNT_X_IN_Y_DAYS -> {
                val secondaryValue = condition.secondaryValue ?: return false
                val start = InAppMessageUtils.getKSTCalendarDateString(-secondaryValue)
                val end = InAppMessageUtils.getKSTCalendarDateString()
                eventCounts.filter { it.name == event }.filter { it.dt in start..end }
                    .sumOf { it.count }
            }
        }

        return compareEventBasedCondition(totalCount, operator, value)
    }

    private fun matchUserPropertyBasedCondition(
        condition: Condition, eventParams: Map<String, Any?>
    ): Boolean {
        val unit = condition.unit
        val operator = condition.operator
        val valueType = condition.valueType ?: return false
        val useEventParamsAsConditionValue = condition.useEventParamsAsConditionValue ?: false

        val userAttributeValue = userData?.get(unit, condition.attribute)
        val value = if (useEventParamsAsConditionValue) {
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
            Operator.EQUALS -> Comparator.isEqual(
                userAttributeValue, value, valueType
            )

            Operator.NOT_EQUALS -> Comparator.isNotEqual(
                userAttributeValue, value, valueType
            )

            Operator.GREATER_THAN -> Comparator.isGreaterThan(
                userAttributeValue, value, valueType
            )

            Operator.GREATER_THAN_OR_EQUAL -> Comparator.isGreaterThanOrEqual(
                userAttributeValue, value, valueType
            )

            Operator.LESS_THAN -> Comparator.isLessThan(
                userAttributeValue, value, valueType
            )

            Operator.LESS_THAN_OR_EQUAL -> Comparator.isLessThanOrEqual(
                userAttributeValue, value, valueType
            )

            Operator.CONTAINS -> Comparator.contains(
                userAttributeValue, value, valueType
            )

            else -> false // Should never happen
        }
    }

    private fun compareEventBasedCondition(count: Int, op: Operator, value: Int): Boolean {
        return when (op) {
            Operator.EQUALS -> count == value
            Operator.GREATER_THAN -> count > value
            Operator.GREATER_THAN_OR_EQUAL -> count >= value
            Operator.LESS_THAN -> count < value
            Operator.LESS_THAN_OR_EQUAL -> count <= value
            else -> false
        }
    }

    private class Comparator {
        companion object {
            @Throws(ClassCastException::class, NumberFormatException::class)
            private fun cast(value: Any, type: String): Any {
                return when (type) {
                    "TEXT" -> value as? String ?: value.toString()
                    "INT" -> {
                        when (value) {
                            is Int -> value
                            is String -> value.toIntOrNull() ?: throw NumberFormatException()
                            else -> throw ClassCastException()
                        }
                    }

                    "BOOL" -> {
                        when (value) {
                            is Boolean -> value
                            is String -> {
                                when (value) {
                                    "true" -> true
                                    "false" -> false
                                    else -> throw ClassCastException()
                                }
                            }

                            else -> throw ClassCastException()
                        }
                    }

                    "ARRAY" -> value as? List<*> ?: throw ClassCastException()
                    else -> throw ClassCastException("Unrecoverable type mismatch: invalid type $type")
                }
            }

            fun isEqual(a: Any, b: Any, type: ValueType): Boolean {
                return try {
                    when (type) {
                        ValueType.TEXT -> cast(
                            a, type.toString()
                        ) as String == cast(b, type.toString()) as String

                        ValueType.INT -> cast(a, type.toString()) as Int == cast(
                            b, type.toString()
                        ) as Int

                        ValueType.BOOL -> cast(
                            a, type.toString()
                        ) as Boolean == cast(b, type.toString()) as Boolean
                    }
                } catch (error: Exception) {
                    Logger.d("[Notifly] ${error.message}")
                    false
                }
            }

            fun isNotEqual(a: Any, b: Any, type: ValueType): Boolean {
                return try {
                    when (type) {
                        ValueType.TEXT -> cast(
                            a, type.toString()
                        ) as String != cast(b, type.toString()) as String

                        ValueType.INT -> cast(a, type.toString()) as Int != cast(
                            b, type.toString()
                        ) as Int

                        ValueType.BOOL -> cast(
                            a, type.toString()
                        ) as Boolean != cast(b, type.toString()) as Boolean
                    }
                } catch (error: Exception) {
                    Logger.d("[Notifly] ${error.message}")
                    false
                }
            }

            fun isGreaterThan(a: Any, b: Any, type: ValueType): Boolean {
                return try {
                    when (type) {
                        ValueType.TEXT -> cast(a, type.toString()) as String > cast(
                            b, type.toString()
                        ) as String

                        ValueType.INT -> cast(a, type.toString()) as Int > cast(
                            b, type.toString()
                        ) as Int

                        ValueType.BOOL -> cast(
                            a, type.toString()
                        ) as Boolean > cast(b, type.toString()) as Boolean
                    }
                } catch (error: Exception) {
                    Logger.d("[Notifly] ${error.message}")
                    false
                }
            }

            fun isGreaterThanOrEqual(a: Any, b: Any, type: ValueType): Boolean {
                return try {
                    when (type) {
                        ValueType.TEXT -> cast(
                            a, type.toString()
                        ) as String >= cast(b, type.toString()) as String

                        ValueType.INT -> cast(a, type.toString()) as Int >= cast(
                            b, type.toString()
                        ) as Int

                        ValueType.BOOL -> cast(
                            a, type.toString()
                        ) as Boolean >= cast(b, type.toString()) as Boolean
                    }
                } catch (error: Exception) {
                    Logger.d("[Notifly] ${error.message}")
                    false
                }
            }

            fun isLessThan(a: Any, b: Any, type: ValueType): Boolean {
                return try {
                    when (type) {
                        ValueType.TEXT -> (cast(
                            a, type.toString()
                        ) as String) < (cast(b, type.toString()) as String)

                        ValueType.INT -> (cast(
                            a, type.toString()
                        ) as Int) < (cast(b, type.toString()) as Int)

                        ValueType.BOOL -> (cast(
                            a, type.toString()
                        ) as Boolean) < (cast(b, type.toString()) as Boolean)
                    }
                } catch (error: Exception) {
                    Logger.d("[Notifly] ${error.message}")
                    false
                }
            }

            fun isLessThanOrEqual(a: Any, b: Any, type: ValueType): Boolean {
                return try {
                    when (type) {
                        ValueType.TEXT -> (cast(
                            a, type.toString()
                        ) as String) <= (cast(b, type.toString()) as String)

                        ValueType.INT -> (cast(a, type.toString()) as Int) <= (cast(
                            b, type.toString()
                        ) as Int)

                        ValueType.BOOL -> (cast(
                            a, type.toString()
                        ) as Boolean) <= (cast(b, type.toString()) as Boolean)
                    }
                } catch (error: Exception) {
                    Logger.d("[Notifly] ${error.message}")
                    false
                }
            }

            fun contains(a: Any, b: Any, type: ValueType): Boolean {
                return try {
                    val castedA = cast(a, "ARRAY") as List<*>
                    when (type) {
                        ValueType.TEXT -> castedA.contains(
                            cast(
                                b, type.toString()
                            ) as String
                        )

                        ValueType.INT -> castedA.contains(
                            cast(
                                b, type.toString()
                            ) as Int
                        )

                        ValueType.BOOL -> castedA.contains(
                            cast(
                                b, type.toString()
                            ) as Boolean
                        )
                    }
                } catch (error: Exception) {
                    Logger.d("[Notifly] ${error.message}")
                    false
                }
            }
        }
    }

    private fun isValuePresent(value: Any?): Boolean {
        return when (value) {
            null -> false
            is String -> value.isNotEmpty()
            else -> false
        }
    }
}
