package tech.notifly.inapp.models

import org.json.JSONException
import org.json.JSONObject

data class Campaign(
    val id: String,
    val channel: String,
    val lastUpdatedTimestamp: Long,
    val start: Long,
    val end: Long?,
    val message: Message,
    val segmentInfo: SegmentInfo?,
    val triggeringEvent: String,
    val delay: Int?
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
        fun fromJSONObject(from: JSONObject, externalUserId: String?): Campaign? {
            /**
             * Check if this campaign is for testing first.
             * If it is, check if this campaign is whitelisted for this user.
             * If it is not, return null.
             */
            val testing = if (from.has("testing")) from.getBoolean("testing") else false
            if (testing) {
                if (externalUserId == null) {
                    return null
                }
                if (from.has("whitelist")) {
                    val whitelist = from.getJSONArray("whitelist")
                    var isWhitelisted = false
                    for (i in 0 until whitelist.length()) {
                        if (whitelist.getString(i) == externalUserId) {
                            isWhitelisted = true
                            break
                        }
                    }
                    if (!isWhitelisted) {
                        return null
                    }
                } else {
                    return null
                }
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
            val delay = if (from.has("delay")) from.getInt("delay") else 0

            return Campaign(
                id,
                channel,
                lastUpdatedTimestamp,
                start,
                end,
                message,
                segmentInfo,
                triggeringEvent,
                delay
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
    val groups: List<Group>, val groupOperator: GroupOperator
) {
    companion object {
        @Throws(JSONException::class)
        fun fromJSONObject(segmentInfo: JSONObject): SegmentInfo? {
            try {
                val groups = if (segmentInfo.has("groups")) {
                    val groupsArray = segmentInfo.getJSONArray("groups")
                    val groups = mutableListOf<Group>()
                    for (i in 0 until groupsArray.length()) {
                        val groupObject = groupsArray.getJSONObject(i)
                        val group = Group.fromJSONObject(groupObject) ?: return null
                        groups.add(group)
                    }
                    groups
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

data class Group(
    val conditions: List<Condition>, val conditionOperator: ConditionOperator
) {
    companion object {
        @Throws(JSONException::class, ClassCastException::class)
        fun fromJSONObject(groupObject: JSONObject): Group? {
            val conditionsArray = groupObject.getJSONArray("conditions")
            val conditionOperator =
                if (conditionsArray.length() > 1) ConditionOperator.AND else ConditionOperator.NULL
            val conditions = mutableListOf<Condition>()
            for (i in 0 until conditionsArray.length()) {
                val conditionObject = conditionsArray.getJSONObject(i)
                val condition = Condition.fromJSONObject(conditionObject) ?: return null
                conditions.add(condition)
            }
            return Group(conditions, conditionOperator)
        }
    }
}

data class Condition(
    val unit: SegmentConditionUnitType,
    val operator: SegmentOperator,
    val value: Any,
    val attribute: String?, // Only for user and device
    val event: String?, // Only for event
    val eventConditionType: EventBasedConditionType?, // Only for event
    val secondaryValue: Int?, // Only for event
    val valueType: SegmentConditionValueType?, // Only for user and device
    val comparisonParameter: String?, // Only for user and device - used only when useEventParamsAsConditionValue is true
    val useEventParamsAsConditionValue: Boolean? // Only for user and device
) {
    companion object {
        @Throws(JSONException::class, ClassCastException::class)
        fun fromJSONObject(conditionObject: JSONObject): Condition? {
            val unit = when (conditionObject.getString("unit")) {
                "event" -> SegmentConditionUnitType.EVENT
                "user" -> SegmentConditionUnitType.USER
                "device" -> SegmentConditionUnitType.DEVICE
                else -> return null
            }

            val operator =
                getSegmentOperator(unit, conditionObject.getString("operator")) ?: return null
            val value = conditionObject.get("value")
            val valueType = when (conditionObject.optString("valueType")) {
                "TEXT" -> SegmentConditionValueType.TEXT
                "INT" -> SegmentConditionValueType.INT
                "BOOL" -> SegmentConditionValueType.BOOL
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
        ): SegmentOperator? {
            when (unit) {
                SegmentConditionUnitType.EVENT -> {
                    return when (segmentOperatorString) {
                        "=" -> SegmentOperator.EQUALS
                        ">" -> SegmentOperator.GREATER_THAN
                        ">=" -> SegmentOperator.GREATER_THAN_OR_EQUAL
                        "<" -> SegmentOperator.LESS_THAN
                        "<=" -> SegmentOperator.LESS_THAN_OR_EQUAL
                        else -> null
                    }
                }

                else -> {
                    return when (segmentOperatorString) {
                        "=" -> SegmentOperator.EQUALS
                        ">" -> SegmentOperator.GREATER_THAN
                        ">=" -> SegmentOperator.GREATER_THAN_OR_EQUAL
                        "<" -> SegmentOperator.LESS_THAN
                        "<=" -> SegmentOperator.LESS_THAN_OR_EQUAL
                        "<>" -> SegmentOperator.NOT_EQUALS
                        "@>" -> SegmentOperator.CONTAINS
                        else -> null
                    }
                }
            }
        }
    }
}
