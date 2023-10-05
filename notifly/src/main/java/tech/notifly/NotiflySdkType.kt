package tech.notifly

enum class NotiflySdkType {
    NATIVE,
    REACT_NATIVE,
    FLUTTER;

    fun toLowerCaseName(): String {
        return this.name.lowercase()
    }
}
