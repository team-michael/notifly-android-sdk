package tech.notifly.utils

object NotiflyUtil {
    fun isValidProjectId(projectId: String): Boolean {
        val regex = Regex("^(?:[0-9a-fA-F]{32})\$")
        return regex.matches(projectId)
    }
}