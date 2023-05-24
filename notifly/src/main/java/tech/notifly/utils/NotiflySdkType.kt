package tech.notifly.utils

enum class NotiflySdkType {
    NATIVE,
    REACT_NATIVE,
    FLUTTER;

    fun toLowerCaseName(): String {
        return this.name.lowercase()
    }
}
