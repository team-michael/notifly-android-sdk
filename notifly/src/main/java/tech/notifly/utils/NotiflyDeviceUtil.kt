package tech.notifly.utils

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object NotiflyDeviceUtil {
    private val MODEL_PREFIXES_TO_FORCE_SOFTWARE_RENDERING =
        listOf(
            // Samsung Galaxy S25 Ultra
            "SM-S938",
            // Samsung Galaxy S25+
            "SM-S936",
            // Samsung Galaxy S25
            "SM-S931",
        )

    suspend fun getOsVersion(): String =
        withContext(Dispatchers.IO) {
            android
                .os
                .Build
                .VERSION
                .RELEASE
        }

    @Suppress("DEPRECATION")
    suspend fun getAppVersion(context: Context): String =
        withContext(Dispatchers.IO) {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName
        }

    @SuppressLint("HardwareIds")
    suspend fun getExternalDeviceId(context: Context): String =
        withContext(Dispatchers.IO) {
            android.provider.Settings.Secure.getString(
                context.contentResolver,
                android
                    .provider
                    .Settings
                    .Secure
                    .ANDROID_ID,
            )
        }

    fun shouldForceSoftwareRendering(): Boolean {
        // Currently, Samsung galaxy S25 series suffers from a hardware rendering issue
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL
        return manufacturer.equals("Samsung", ignoreCase = true) &&
            MODEL_PREFIXES_TO_FORCE_SOFTWARE_RENDERING.any {
                model.startsWith(it, ignoreCase = true)
            }
    }

    fun getPlatform(): String = N.PLATFORM

    fun getApiLevel(): Int = Build.VERSION.SDK_INT

    fun getBrand(): String = Build.BRAND

    fun getModel(): String = Build.MODEL
}
