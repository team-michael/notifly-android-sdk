package tech.notifly

internal enum class NotiflySdkType {
    NATIVE,
    REACT_NATIVE,
    FLUTTER;

    fun toLowerCaseName(): String {
        return this.name.lowercase()
    }
}
