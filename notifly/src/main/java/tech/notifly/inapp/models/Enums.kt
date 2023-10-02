package tech.notifly.inapp.models

enum class SegmentConditionUnitType {
    USER, DEVICE, EVENT
}

enum class SegmentConditionValueType {
    INT, TEXT, BOOL
}

enum class GroupOperator {
    OR, NULL
}

enum class ConditionOperator {
    AND, NULL
}

enum class SegmentOperator {
    EQUALS, NOT_EQUALS, GREATER_THAN, GREATER_THAN_OR_EQUAL, LESS_THAN, LESS_THAN_OR_EQUAL, CONTAINS,
}

enum class EventBasedConditionType {
    COUNT_X, COUNT_X_IN_Y_DAYS
}

enum class ReEligibleConditionUnitType {
    HOUR, DAY, WEEK, MONTH, INFINITE
}
