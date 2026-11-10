package tech.notifly.utils

internal object UserPropertyKeyPolicy {
    private val reservedKeys =
        setOf(
            N.KEY_EMAIL_PROPERTY,
            N.KEY_PHONE_NUMBER_PROPERTY,
            N.KEY_TIMEZONE_PROPERTY,
            "\$line_user_id",
            "\$appsflyer_id",
        )
    private val invalidCharacter = Regex("[^A-Za-z0-9_]")

    fun invalidKeys(keys: Collection<String>): List<String> = keys.filter { it !in reservedKeys && (it.isEmpty() || invalidCharacter.containsMatchIn(it)) }.sorted()
}
