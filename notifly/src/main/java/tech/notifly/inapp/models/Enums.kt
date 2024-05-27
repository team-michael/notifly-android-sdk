package tech.notifly.inapp.models

enum class SegmentConditionUnitType {
    USER, USER_METADATA, DEVICE, EVENT
}

enum class GroupOperator {
    OR, NULL
}

enum class ConditionOperator {
    AND, NULL
}

enum class Operator {
    IS_NULL, IS_NOT_NULL, EQUALS, NOT_EQUALS, GREATER_THAN, GREATER_THAN_OR_EQUAL, LESS_THAN, LESS_THAN_OR_EQUAL, CONTAINS,
}

enum class TriggeringConditionType {
    EVENT_NAME,
}

enum class TriggeringConditionOperator {
    EQUALS, NOT_EQUALS, STARTS_WITH, DOES_NOT_START_WITH, ENDS_WITH, DOES_NOT_END_WITH, CONTAINS, DOES_NOT_CONTAIN, MATCHES_REGEX, DOES_NOT_MATCH_REGEX,
}

enum class ValueType {
    INT, TEXT, BOOL
}

enum class EventBasedConditionType {
    COUNT_X, COUNT_X_IN_Y_DAYS
}

enum class ReEligibleConditionUnitType {
    HOUR, DAY, WEEK, MONTH, INFINITE
}
