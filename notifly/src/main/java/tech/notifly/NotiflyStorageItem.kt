package tech.notifly

@Suppress("ClassName")
sealed class NotiflyStorageItem<T> {

    abstract val key: String
    abstract val default: T

    object PROJECT_ID : NotiflyStorageItem<String?>() {
        override val key: String = "notifly_project_id"
        override val default: String? = null
    }
    object USERNAME : NotiflyStorageItem<String?>() {
        override val key: String = "notifly_username"
        override val default: String? = null
    }
    object PASSWORD : NotiflyStorageItem<String?>() {
        override val key: String = "notifly_password"
        override val default: String? = null
    }
    object COGNITO_ID_TOKEN : NotiflyStorageItem<String?>() {
        override val key: String = "notifly_cognito_id_token"
        override val default: String? = null
    }
    object USER_ID : NotiflyStorageItem<String?>() {
        override val key: String = "notifly_user_id"
        override val default: String? = null
    }
    object EXTERNAL_USER_ID : NotiflyStorageItem<String?>() {
        override val key: String = "notifly_external_user_id"
        override val default: String? = null
    }
}
