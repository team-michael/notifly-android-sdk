package tech.notifly.push

enum class NotificationAuthorizationStatus(
    val value: Int,
) {
    DENIED(0),
    AUTHORIZED(1),
}
