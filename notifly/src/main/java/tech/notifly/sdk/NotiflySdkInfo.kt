package tech.notifly.sdk

import tech.notifly.BuildConfig

object NotiflySdkInfo {
    private var sdkVersion = BuildConfig.VERSION

    fun getSdkVersion(): String = sdkVersion
    fun getSdkType(): String = "native"
}