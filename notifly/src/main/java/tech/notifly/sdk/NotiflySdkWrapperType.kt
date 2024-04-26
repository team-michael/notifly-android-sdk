package tech.notifly.sdk

enum class NotiflySdkWrapperType {
    REACT_NATIVE,
    FLUTTER;

    fun toLowerCaseName(): String {
        return this.name.lowercase()
    }
}
