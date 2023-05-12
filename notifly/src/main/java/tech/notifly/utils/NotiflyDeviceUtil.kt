package tech.notifly.utils

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object NotiflyDeviceUtil {

    suspend fun getOsVersion(): String = withContext(Dispatchers.IO) {
        android.os.Build.VERSION.RELEASE
    }

    @Suppress("DEPRECATION")

    suspend fun getAppVersion(context: Context): String = withContext(Dispatchers.IO) {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        packageInfo.versionName
    }

    @SuppressLint("HardwareIds")
    suspend fun getExternalDeviceId(context: Context): String = withContext(Dispatchers.IO) {
        android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        )
    }

    fun getPlatform(): String {
        return N.PLATFORM
    }

    fun getApiLevel(): Int {
        return Build.VERSION.SDK_INT
    }

    fun getBrand(): String {
        return Build.BRAND
    }

    fun getModel(): String {
        return Build.MODEL
    }
}