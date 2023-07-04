package tech.notifly.inapp

import android.content.Context
import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tech.notifly.utils.Logger
import tech.notifly.inapp.models.Campaign
import tech.notifly.inapp.models.Condition
import tech.notifly.inapp.models.EventBasedConditionType
import tech.notifly.inapp.models.EventIntermediateCounts
import tech.notifly.inapp.models.SegmentConditionUnitType
import tech.notifly.inapp.models.SegmentConditionValueType
import tech.notifly.inapp.models.SegmentOperator
import tech.notifly.inapp.models.UserData
import tech.notifly.utils.N
import tech.notifly.utils.NotiflySyncStateUtil
import tech.notifly.utils.OSUtils
import kotlin.math.floor

object InAppMessageManager {
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.R)
    val IS_IN_APP_MESSAGE_SUPPORTED = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    private const val REFRESH_TIMEOUT_MILLIS = 1000L * 5L // 5 seconds

    @Volatile
    private var isInitialized = false

    private lateinit var campaigns: MutableList<Campaign>
    private lateinit var eventCounts: MutableList<EventIntermediateCounts>
    private var userData: UserData? = null

    @Throws(NullPointerException::class)
    suspend fun initialize(context: Context) {
        if (!IS_IN_APP_MESSAGE_SUPPORTED) {
            Logger.i("[Notifly] InAppMessageManager is not supported on this device.")
            return
        }
        sync(context)
        isInitialized = true
    }

    fun refresh(context: Context, timeoutMillis: Long = REFRESH_TIMEOUT_MILLIS) {
        if (!IS_IN_APP_MESSAGE_SUPPORTED) {
            Logger.i("[Notifly] InAppMessageManager is not supported on this device.")
            return
        }

        // Fetch states from server again after timeoutMillis
        if (!isInitialized) {
            Logger.e("[Notifly] InAppMessageManager is not initialized.")
            return
        }

        CoroutineScope(Dispatchers.Main).launch {
            delay(timeoutMillis)
            sync(context)
        }
    }

    fun updateUserData(params: Map<String, Any?>) {
        if (!IS_IN_APP_MESSAGE_SUPPORTED) {
            Logger.i("[Notifly] InAppMessageManager is not supported on this device.")
            return
        }

        if (!isInitialized) {
            Logger.e("[Notifly] InAppMessageManager is not initialized.")
            return
        }

        try {
            if (userData != null && userData!!.userProperties != null) {
                params.forEach {
                    userData!!.userProperties!![it.key] = it.value
                }
                Logger.d("[Notifly] Updating user property to $userData")
            }
        } catch (e: Exception) {
            Logger.e("[Notifly] updateUserData failed", e)
        }
    }

    fun ingestEventAndMaybeScheduleInAppMessages(
        context: Context,
        eventName: String,
        eventParams: Map<String, Any?>,
        isInternalEvent: Boolean,
        segmentationEventParamKeys: List<String>? = null
    ) {
        if (!IS_IN_APP_MESSAGE_SUPPORTED) {
            Logger.i("[Notifly] In app message feature is not supported on this device.")
            return
        }

        if (!isInitialized) {
            Logger.e("[Notifly] InAppMessageManager is not initialized.")
            return
        }

        val sanitizedEventName = sanitizeEventName(eventName, isInternalEvent)
        ingestEventInternal(sanitizedEventName, eventParams, segmentationEventParamKeys)
        if (OSUtils.isAppInForeground(context)) {
            scheduleCampaigns(context, campaigns, sanitizedEventName, eventParams)
        } else {
            Logger.d("[Notifly] App is not in foreground. Not scheduling in app messages.")
        }
    }

    private fun sanitizeEventName(eventName: String, isInternalEvent: Boolean): String {
        return if (isInternalEvent) "${N.INTERNAL_EVENT_PREFIX}$eventName" else eventName
    }

    @Throws(NullPointerException::class)
    private suspend fun sync(context: Context) {
        val syncStateResult = NotiflySyncStateUtil.syncState(context)
        campaigns = syncStateResult.campaigns
        eventCounts = syncStateResult.eventCounts
        userData = syncStateResult.userData

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
        eventName: String,
        eventParams: Map<String, Any?>
    ) {
        getCampaignsToSchedule(campaigns, eventName, eventParams).forEach {
            InAppMessageScheduler.schedule(context, it)
        }
    }

    private fun getCampaignsToSchedule(
        campaigns: List<Campaign>, eventName: String, eventParams: Map<String, Any?>
    ): List<Campaign> {
        return filterCampaignsWithUniqueDelays(campaigns.filter {
            evaluateCampaignVisibility(it, eventName, eventParams)
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
        campaign: Campaign, eventName: String, eventParams: Map<String, Any?>
    ): Boolean {
        if (campaign.triggeringEvent != eventName) {
            return false
        }

        if (userData?.userProperties != null) {
            val templateName = campaign.message.templateName
            if (templateName != null) {
                val userProperties = userData!!.userProperties!!
                if (userProperties["hide_in_app_message_$templateName"] == true) {
                    Logger.d("InAppMessageManager: $templateName is hidden by user property.")
                    return false
                }
            }
        }

        val groups = campaign.segmentInfo?.groups
        if (groups.isNullOrEmpty()) {
            return true
        }

        val now = floor(System.currentTimeMillis().toDouble() / 1000.0).toInt()
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

        val userAttributeValue = userData?.get(unit, condition.attribute) ?: return false
        val value = if (useEventParamsAsConditionValue) {
            val comparisonParameter = condition.comparisonParameter ?: return false
            eventParams[comparisonParameter] ?: return false
        } else {
            condition.value
        }

        return when (operator) {
            SegmentOperator.EQUALS -> UserPropertyBasedConditionComparator.isEqual(
                userAttributeValue, value, valueType
            )

            SegmentOperator.NOT_EQUALS -> UserPropertyBasedConditionComparator.isNotEqual(
                userAttributeValue, value, valueType
            )

            SegmentOperator.GREATER_THAN -> UserPropertyBasedConditionComparator.isGreaterThan(
                userAttributeValue, value, valueType
            )

            SegmentOperator.GREATER_THAN_OR_EQUAL -> UserPropertyBasedConditionComparator.isGreaterThanOrEqual(
                userAttributeValue, value, valueType
            )

            SegmentOperator.LESS_THAN -> UserPropertyBasedConditionComparator.isLessThan(
                userAttributeValue, value, valueType
            )

            SegmentOperator.LESS_THAN_OR_EQUAL -> UserPropertyBasedConditionComparator.isLessThanOrEqual(
                userAttributeValue, value, valueType
            )

            SegmentOperator.CONTAINS -> UserPropertyBasedConditionComparator.contains(
                userAttributeValue, value, valueType
            )
        }
    }

    private fun compareEventBasedCondition(count: Int, op: SegmentOperator, value: Int): Boolean {
        return when (op) {
            SegmentOperator.EQUALS -> count == value
            SegmentOperator.GREATER_THAN -> count > value
            SegmentOperator.GREATER_THAN_OR_EQUAL -> count >= value
            SegmentOperator.LESS_THAN -> count < value
            SegmentOperator.LESS_THAN_OR_EQUAL -> count <= value
            else -> false
        }
    }

    private class UserPropertyBasedConditionComparator {
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

            fun isEqual(a: Any, b: Any, type: SegmentConditionValueType): Boolean {
                return try {
                    when (type) {
                        SegmentConditionValueType.TEXT -> cast(
                            a, type.toString()
                        ) as String == cast(b, type.toString()) as String

                        SegmentConditionValueType.INT -> cast(a, type.toString()) as Int == cast(
                            b, type.toString()
                        ) as Int

                        SegmentConditionValueType.BOOL -> cast(
                            a, type.toString()
                        ) as Boolean == cast(b, type.toString()) as Boolean
                    }
                } catch (error: Exception) {
                    Logger.d("[Notifly] ${error.message}")
                    false
                }
            }

            fun isNotEqual(a: Any, b: Any, type: SegmentConditionValueType): Boolean {
                return try {
                    when (type) {
                        SegmentConditionValueType.TEXT -> cast(
                            a, type.toString()
                        ) as String != cast(b, type.toString()) as String

                        SegmentConditionValueType.INT -> cast(a, type.toString()) as Int != cast(
                            b, type.toString()
                        ) as Int

                        SegmentConditionValueType.BOOL -> cast(
                            a, type.toString()
                        ) as Boolean != cast(b, type.toString()) as Boolean
                    }
                } catch (error: Exception) {
                    Logger.d("[Notifly] ${error.message}")
                    false
                }
            }

            fun isGreaterThan(a: Any, b: Any, type: SegmentConditionValueType): Boolean {
                return try {
                    when (type) {
                        SegmentConditionValueType.TEXT -> cast(a, type.toString()) as String > cast(
                            b, type.toString()
                        ) as String

                        SegmentConditionValueType.INT -> cast(a, type.toString()) as Int > cast(
                            b, type.toString()
                        ) as Int

                        SegmentConditionValueType.BOOL -> cast(
                            a, type.toString()
                        ) as Boolean > cast(b, type.toString()) as Boolean
                    }
                } catch (error: Exception) {
                    Logger.d("[Notifly] ${error.message}")
                    false
                }
            }

            fun isGreaterThanOrEqual(a: Any, b: Any, type: SegmentConditionValueType): Boolean {
                return try {
                    when (type) {
                        SegmentConditionValueType.TEXT -> cast(
                            a, type.toString()
                        ) as String >= cast(b, type.toString()) as String

                        SegmentConditionValueType.INT -> cast(a, type.toString()) as Int >= cast(
                            b, type.toString()
                        ) as Int

                        SegmentConditionValueType.BOOL -> cast(
                            a, type.toString()
                        ) as Boolean >= cast(b, type.toString()) as Boolean
                    }
                } catch (error: Exception) {
                    Logger.d("[Notifly] ${error.message}")
                    false
                }
            }

            fun isLessThan(a: Any, b: Any, type: SegmentConditionValueType): Boolean {
                return try {
                    when (type) {
                        SegmentConditionValueType.TEXT -> (cast(
                            a, type.toString()
                        ) as String) < (cast(b, type.toString()) as String)

                        SegmentConditionValueType.INT -> (cast(
                            a, type.toString()
                        ) as Int) < (cast(b, type.toString()) as Int)

                        SegmentConditionValueType.BOOL -> (cast(
                            a, type.toString()
                        ) as Boolean) < (cast(b, type.toString()) as Boolean)
                    }
                } catch (error: Exception) {
                    Logger.d("[Notifly] ${error.message}")
                    false
                }
            }

            fun isLessThanOrEqual(a: Any, b: Any, type: SegmentConditionValueType): Boolean {
                return try {
                    when (type) {
                        SegmentConditionValueType.TEXT -> (cast(
                            a, type.toString()
                        ) as String) <= (cast(b, type.toString()) as String)

                        SegmentConditionValueType.INT -> (cast(a, type.toString()) as Int) <= (cast(
                            b, type.toString()
                        ) as Int)

                        SegmentConditionValueType.BOOL -> (cast(
                            a, type.toString()
                        ) as Boolean) <= (cast(b, type.toString()) as Boolean)
                    }
                } catch (error: Exception) {
                    Logger.d("[Notifly] ${error.message}")
                    false
                }
            }

            fun contains(a: Any, b: Any, type: SegmentConditionValueType): Boolean {
                return try {
                    val castedA = cast(a, "ARRAY") as List<*>
                    when (type) {
                        SegmentConditionValueType.TEXT -> castedA.contains(
                            cast(
                                b, type.toString()
                            ) as String
                        )

                        SegmentConditionValueType.INT -> castedA.contains(
                            cast(
                                b, type.toString()
                            ) as Int
                        )

                        SegmentConditionValueType.BOOL -> castedA.contains(
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
}
