package tech.notifly.utils

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object NotiflyDeviceUtil {

    suspend fun getOsVersion(): String = withContext(Dispatchers.IO) {
        android.os.Build.VERSION.RELEASE
    }

    suspend fun getAppVersion(context: Context): String = withContext(Dispatchers.IO) {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        packageInfo.versionName
    }

    suspend fun getExternalDeviceId(context: Context): String = withContext(Dispatchers.IO) {
        android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        )
    }

    fun getPlatform(): String {
        return NotiflyBaseUtil.PLATFORM
    }
}