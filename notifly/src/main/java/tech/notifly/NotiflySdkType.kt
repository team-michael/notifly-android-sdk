package tech.notifly

internal enum class NotiflySdkType {
    ANDROID,
    REACT_NATIVE,
    FLUTTER;

    fun toLowerCaseName(): String {
        return this.name.lowercase()
    }
}
