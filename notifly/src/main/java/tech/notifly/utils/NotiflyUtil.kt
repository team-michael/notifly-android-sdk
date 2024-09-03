package tech.notifly.utils

import java.util.TimeZone

object NotiflyUtil {
    fun isValidProjectId(projectId: String): Boolean {
        val regex = Regex("^(?:[0-9a-fA-F]{32})\$")
        return regex.matches(projectId)
    }

    fun isValidTimezoneId(timezoneId: String): Boolean = TimeZone.getAvailableIDs().contains(timezoneId)

    fun getCurrentTimezone(): String = TimeZone.getDefault().id
}
