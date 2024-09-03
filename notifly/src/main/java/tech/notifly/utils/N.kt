package tech.notifly.utils

/**
 * Static values used internally
 */
internal object N {
    /**
     * Regardless of [NotiflySdkType], Platform Information is fixed to "Android"
     */
    const val PLATFORM = "android"

    /**
     * Reserved Key for backward compatibility based on User Property or Event Logging.
     */
    const val KEY_EXTERNAL_USER_ID = "external_user_id"

    /**
     * Reserved Key for backward compatibility based on User Property or Event Logging.
     */
    const val KEY_PREVIOUS_NOTIFLY_USER_ID = "previous_notifly_user_id"

    /**
     * Reserved Key for backward compatibility based on User Property or Event Logging.
     */
    const val KEY_PREVIOUS_EXTERNAL_USER_ID = "previous_external_user_id"

    /**
     * Prefix of internal event
     */
    const val INTERNAL_EVENT_PREFIX = "notifly__"

    /**
     * Reserved key for special user properties
     */
    const val KEY_TIMEZONE_PROPERTY = "\$timezone"
    const val KEY_PHONE_NUMBER_PROPERTY = "\$phone_number"
    const val KEY_EMAIL_PROPERTY = "\$email"
}
