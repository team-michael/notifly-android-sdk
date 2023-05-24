package tech.notifly.utils

import tech.notifly.BuildConfig

object NotiflySDKInfoUtil {
    private var sdkVersion: String = BuildConfig.VERSION
    private var sdkType: NotiflySdkType = NotiflySdkType.NATIVE

    fun getSdkVersion(): String = sdkVersion

    fun getSdkType(): NotiflySdkType = sdkType

    fun setSdkVersion(v: String) {
        sdkVersion = v
    }

    fun setSdkType(type: NotiflySdkType) {
        sdkType = type
    }
}
