package tech.notifly.utils

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build

object OSUtils {
    fun isAppInForeground(context: Context): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val appProcesses = activityManager.runningAppProcesses
        if (appProcesses != null) {
            val packageName = context.packageName
            for (appProcess in appProcesses) {
                if (appProcess.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND && appProcess.processName == packageName) {
                    return true
                }
            }
        }
        // It's not straightforward to distinguish between background and quit
        // so we treat quit state as background
        return false
    }

    fun hasConfigChangeFlag(
        activity: Activity,
        configChangeFlag: Int,
    ): Boolean {
        var hasFlag = false
        try {
            val configChanges = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                activity.packageManager.getActivityInfo(
                    activity.componentName, PackageManager.ComponentInfoFlags.of(0)
                ).configChanges
            } else {
                @Suppress("DEPRECATION") activity.packageManager.getActivityInfo(
                    activity.componentName, 0
                ).configChanges
            }
            val flagInt = configChanges and configChangeFlag
            hasFlag = flagInt != 0
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }
        return hasFlag
    }

    fun openURLInBrowserIntent(uri: Uri, flags: Int? = null): Intent {
        var uri = uri
        var type = if (uri.scheme != null) SchemaType.fromString(uri.scheme) else null

        if (type == null) {
            type = SchemaType.HTTP
            if (!uri.toString().contains("://")) {
                uri = Uri.parse("http://$uri")
            }
        }
        val intent: Intent
        when (type) {
            SchemaType.DATA -> {
                intent =
                    Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER)
                intent.data = uri
            }

            SchemaType.HTTPS, SchemaType.HTTP -> intent =
                Intent(Intent.ACTION_VIEW, uri).addCategory(
                    Intent.CATEGORY_BROWSABLE
                )
        }

        intent.addFlags(
            flags ?: Intent.FLAG_ACTIVITY_NEW_TASK,
        )
        return intent
    }

    fun isInAppLink(uri: Uri): Boolean {
        val type = SchemaType.fromString(uri.scheme)
        return type == null
    }

    enum class SchemaType(private val text: String) {
        DATA("data"), HTTPS("https"), HTTP("http"), ;

        companion object {
            fun fromString(text: String?): SchemaType? {
                for (type in SchemaType.values()) {
                    if (type.text.equals(text, ignoreCase = true)) {
                        return type
                    }
                }
                return null
            }
        }
    }
}
