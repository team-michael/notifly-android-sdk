package tech.notifly.sdk

object NotiflySdkWrapperInfo {
    private var sdkVersion: String? = null
    private var sdkType: NotiflySdkWrapperType? = null

    fun getSdkVersion(): String? = sdkVersion

    fun getSdkType(): NotiflySdkWrapperType? = sdkType

    fun setSdkVersion(v: String) {
        sdkVersion = v
    }

    fun setSdkType(type: NotiflySdkWrapperType) {
        sdkType = type
    }
}
