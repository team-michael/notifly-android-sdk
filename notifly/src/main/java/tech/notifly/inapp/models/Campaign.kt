package tech.notifly.inapp.models

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import tech.notifly.utils.Logger

data class Campaign(
    val id: String,
    val channel: String,
    val lastUpdatedTimestamp: Long,
    val testing: Boolean,
    val whitelist: List<String>?,
    val start: Long,
    val end: Long?,
    val message: Message,
    val segmentInfo: SegmentInfo?,
    val triggeringEvent: String,
    val triggeringEventFilters: TriggeringEventFilters?,
    val delay: Int?,
    val reEligibleCondition: ReEligibleCondition? = null
) : Comparable<Campaign> {
    override fun compareTo(other: Campaign): Int {
        // Compare based on age
        val thisDelay = delay ?: 0
        val otherDelay = other.delay ?: 0

        return when {
            thisDelay < otherDelay -> -1
            thisDelay > otherDelay -> 1
            else -> if (this.lastUpdatedTimestamp > other.lastUpdatedTimestamp) -1 else 1
        }
    }

    companion object {
        /**
         * Parses [JSONObject] and loads data to [Campaign] data class.
         * @return [Campaign] object if successfully parsed, `null` otherwise.
         */
        @Throws(JSONException::class)
        fun fromJSONObject(from: JSONObject): Campaign? {
            val testing = if (from.has("testing")) from.getBoolean("testing") else false
            val whitelist = if (testing) {
                if (from.has("whitelist")) {
                    val whitelistJSONArray = from.getJSONArray("whitelist")
                    val whitelist = mutableListOf<String>()
                    for (i in 0 until whitelistJSONArray.length()) {
                        whitelist.add(whitelistJSONArray.getString(i))
                    }
                    whitelist
                } else {
                    Logger.w("If campaign is configured as testing campaign, whitelist field should be specified.")
                    return null
                }
            } else {
                null
            }

            val id = from.getString("id") // Campaign ID
            val channel =
                from.getString("channel") // Channel (this should always be in-app-message)
            if (channel != "in-app-message") {
                return null
            }
            val lastUpdatedTimestamp = from.getLong("last_updated_timestamp")
            if (from.getString("segment_type") != "condition") {
                return null
            }
            val start = from.getJSONArray("starts").getLong(0)
            val end = from.get("end").let {
                if (it == JSONObject.NULL) null else (it as Int).toLong()
            }

            val messageObject = from.getJSONObject("message")
            val url = messageObject.getString("html_url")
            val modalPropertiesObject = messageObject.getJSONObject("modal_properties")
            val templateName =
                if (modalPropertiesObject.has("template_name")) modalPropertiesObject.getString("template_name") else null
            val message = Message(url, modalPropertiesObject.toString(), templateName)

            val segmentInfoObject = from.getJSONObject("segment_info")
            val segmentInfo = SegmentInfo.fromJSONObject(segmentInfoObject) ?: return null

            val triggeringEvent = from.getString("triggering_event")
            val triggeringEventFilters = if (from.has("triggering_event_filters")) {
                val triggeringEventFiltersJSONArray = from.get("triggering_event_filters")
                if (triggeringEventFiltersJSONArray == JSONObject.NULL) {
                    null
                } else {
                    TriggeringEventFilters.fromJSONObject(triggeringEventFiltersJSONArray as JSONArray)
                }
            } else {
                null
            }
            val delay = if (from.has("delay")) from.getInt("delay") else 0

            val reEligibleCondition = if (from.has("re_eligible_condition")) {
                val value = from.get("re_eligible_condition")
                if (value == JSONObject.NULL) null else ReEligibleCondition.fromJSONObject(
                    value as JSONObject
                )
            } else null

            return Campaign(
                id = id,
                channel = channel,
                lastUpdatedTimestamp = lastUpdatedTimestamp,
                testing = testing,
                whitelist = whitelist,
                start = start,
                end = end,
                message = message,
                segmentInfo = segmentInfo,
                triggeringEvent = triggeringEvent,
                triggeringEventFilters = triggeringEventFilters,
                delay = delay,
                reEligibleCondition = reEligibleCondition
            )
        }
    }
}

data class Message(
    val url: String,
    val modalProperties: String, // JSON-stringified string. InAppMessageActivity will handle this
    val templateName: String?
)

data class SegmentInfo(
    val conditionGroup: List<ConditionGroup>, val groupOperator: GroupOperator
) {
    companion object {
        @Throws(JSONException::class)
        fun fromJSONObject(segmentInfo: JSONObject): SegmentInfo? {
            try {
                val groups = if (segmentInfo.has("groups")) {
                    val groupsArray = segmentInfo.getJSONArray("groups")
                    val conditionGroups = mutableListOf<ConditionGroup>()
                    for (i in 0 until groupsArray.length()) {
                        val groupObject = groupsArray.getJSONObject(i)
                        val conditionGroup =
                            ConditionGroup.fromJSONObject(groupObject) ?: return null
                        conditionGroups.add(conditionGroup)
                    }
                    conditionGroups
                } else {
                    return null
                }

                val groupOperator = if (groups.size > 1) GroupOperator.OR else GroupOperator.NULL
                return SegmentInfo(groups, groupOperator)
            } catch (e: ClassCastException) {
                return null
            }
        }
    }
}

data class ConditionGroup(
    val conditions: List<Condition>, val conditionOperator: ConditionOperator
) {
    companion object {
        @Throws(JSONException::class, ClassCastException::class)
        fun fromJSONObject(groupObject: JSONObject): ConditionGroup? {
            val conditionsArray = groupObject.getJSONArray("conditions")
            val conditionOperator =
                if (conditionsArray.length() > 1) ConditionOperator.AND else ConditionOperator.NULL
            val conditions = mutableListOf<Condition>()
            for (i in 0 until conditionsArray.length()) {
                val conditionObject = conditionsArray.getJSONObject(i)
                val condition = Condition.fromJSONObject(conditionObject) ?: return null
                conditions.add(condition)
            }
            return ConditionGroup(conditions, conditionOperator)
        }
    }
}

data class Condition(
    val unit: SegmentConditionUnitType,
    val operator: Operator,
    val value: Any?,
    val attribute: String?, // Only for user and device
    val event: String?, // Only for event
    val eventConditionType: EventBasedConditionType?, // Only for event
    val secondaryValue: Int?, // Only for event
    val valueType: ValueType?, // Only for user and device
    val comparisonParameter: String?, // Only for user and device - used only when useEventParamsAsConditionValue is true
    val useEventParamsAsConditionValue: Boolean? // Only for user and device
) {
    companion object {
        @Throws(JSONException::class, ClassCastException::class)
        fun fromJSONObject(conditionObject: JSONObject): Condition? {
            val unit = when (conditionObject.getString("unit")) {
                "event" -> SegmentConditionUnitType.EVENT
                "user" -> SegmentConditionUnitType.USER
                "user_metadata" -> SegmentConditionUnitType.USER_METADATA
                "device" -> SegmentConditionUnitType.DEVICE
                else -> return null
            }

            val operator =
                getSegmentOperator(unit, conditionObject.getString("operator")) ?: return null
            val value = if (conditionObject.has("value")) {
                conditionObject.get("value").let {
                    if (it == JSONObject.NULL) null else it
                }
            } else {
                null
            }
            val valueType = when (conditionObject.optString("valueType")) {
                "TEXT" -> ValueType.TEXT
                "INT" -> ValueType.INT
                "BOOL" -> ValueType.BOOL
                else -> null
            }
            val event = if (conditionObject.has("event")) {
                conditionObject.getString("event")
            } else {
                null
            }
            val attribute = if (conditionObject.has("attribute")) {
                conditionObject.getString("attribute")
            } else {
                null
            }
            val eventConditionType = if (conditionObject.has("event_condition_type")) {
                when (conditionObject.getString("event_condition_type")) {
                    "count X" -> EventBasedConditionType.COUNT_X
                    "count X in Y days" -> EventBasedConditionType.COUNT_X_IN_Y_DAYS
                    else -> return null
                }
            } else {
                null
            }
            val secondaryValue =
                if (eventConditionType == EventBasedConditionType.COUNT_X_IN_Y_DAYS) {
                    conditionObject.getInt("secondary_value")
                } else {
                    null
                }
            val useEventParamsAsConditionValue = if (unit == SegmentConditionUnitType.EVENT) {
                null
            } else {
                if (conditionObject.has("useEventParamsAsConditionValue")) {
                    conditionObject.getBoolean("useEventParamsAsConditionValue")
                } else {
                    null
                }
            }
            val comparisonParameter =
                if (useEventParamsAsConditionValue !== null && useEventParamsAsConditionValue) {
                    if (conditionObject.has("comparison_parameter")) {
                        conditionObject.getString("comparison_parameter")
                    } else {
                        null
                    }
                } else {
                    null
                }

            return Condition(
                unit,
                operator,
                value,
                attribute,
                event,
                eventConditionType,
                secondaryValue,
                valueType,
                comparisonParameter,
                useEventParamsAsConditionValue
            )
        }

        private fun getSegmentOperator(
            unit: SegmentConditionUnitType, segmentOperatorString: String
        ): Operator? {
            when (unit) {
                SegmentConditionUnitType.EVENT -> {
                    return when (segmentOperatorString) {
                        "=" -> Operator.EQUALS
                        ">" -> Operator.GREATER_THAN
                        ">=" -> Operator.GREATER_THAN_OR_EQUAL
                        "<" -> Operator.LESS_THAN
                        "<=" -> Operator.LESS_THAN_OR_EQUAL
                        else -> null
                    }
                }

                else -> {
                    return when (segmentOperatorString) {
                        "IS_NULL" -> Operator.IS_NULL
                        "IS_NOT_NULL" -> Operator.IS_NOT_NULL
                        "=" -> Operator.EQUALS
                        ">" -> Operator.GREATER_THAN
                        ">=" -> Operator.GREATER_THAN_OR_EQUAL
                        "<" -> Operator.LESS_THAN
                        "<=" -> Operator.LESS_THAN_OR_EQUAL
                        "<>" -> Operator.NOT_EQUALS
                        "@>" -> Operator.CONTAINS
                        else -> null
                    }
                }
            }
        }
    }
}

data class ReEligibleCondition(
    val unit: ReEligibleConditionUnitType,
    val duration: Int,
) {
    companion object {
        @Throws(JSONException::class)
        fun fromJSONObject(input: JSONObject): ReEligibleCondition {
            val unit = when (input.getString("unit")) {
                "h" -> ReEligibleConditionUnitType.HOUR
                "d" -> ReEligibleConditionUnitType.DAY
                "w" -> ReEligibleConditionUnitType.WEEK
                "m" -> ReEligibleConditionUnitType.MONTH
                "infinite" -> ReEligibleConditionUnitType.INFINITE
                else -> throw JSONException("Invalid unit type")
            }

            return ReEligibleCondition(unit, input.getInt("value"))
        }
    }
}

data class TriggeringEventFilters(
    val filters: List<TriggeringEventFilterGroup>
) {
    companion object {
        @Throws(JSONException::class)
        fun fromJSONObject(input: JSONArray): TriggeringEventFilters {
            val filters = mutableListOf<TriggeringEventFilterGroup>()
            for (i in 0 until input.length()) {
                val filterGroup = input.getJSONArray(i)
                val filterGroupList = mutableListOf<TriggeringEventFilterUnit>()

                for (j in 0 until filterGroup.length()) {
                    val filterUnit = filterGroup.getJSONObject(j)
                    val key = filterUnit.getString("key")
                    val operator = when (filterUnit.getString("operator")) {
                        "IS_NULL" -> Operator.IS_NULL
                        "IS_NOT_NULL" -> Operator.IS_NOT_NULL
                        "=" -> Operator.EQUALS
                        ">" -> Operator.GREATER_THAN
                        ">=" -> Operator.GREATER_THAN_OR_EQUAL
                        "<" -> Operator.LESS_THAN
                        "<=" -> Operator.LESS_THAN_OR_EQUAL
                        "<>" -> Operator.NOT_EQUALS
                        "@>" -> Operator.CONTAINS
                        else -> throw JSONException("Invalid operator")
                    }
                    val value = if (filterUnit.has("value")) {
                        filterUnit.get("value").let {
                            if (it == JSONObject.NULL) null else it
                        }
                    } else {
                        null
                    }
                    val valueType = if (filterUnit.has("value_type")) {
                        val type = filterUnit.get("value_type")
                        if (type == JSONObject.NULL || type !is String ) {
                            null
                        } else {
                            when (type) {
                                "TEXT" -> ValueType.TEXT
                                "INT" -> ValueType.INT
                                "BOOL" -> ValueType.BOOL
                                else -> null
                            }
                        }
                    } else {
                        null
                    }

                    filterGroupList.add(TriggeringEventFilterUnit(key, operator, value, valueType))
                }
                filters.add(filterGroupList)
            }
            return TriggeringEventFilters(filters)
        }
    }
}

typealias TriggeringEventFilterGroup = List<TriggeringEventFilterUnit>

data class TriggeringEventFilterUnit(
    val key: String, val operator: Operator, val value: Any?, val valueType: ValueType?
)
