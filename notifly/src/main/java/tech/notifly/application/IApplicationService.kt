package tech.notifly.application

import android.app.Activity
import android.content.Context

interface IApplicationService {
    /**
     * The application context
     */
    val appContext: Context

    /**
     * The current activity for the application. When null the application has no
     * active activity, it is in the background.
     */
    val current: Activity?

    /**
     * Whether the application is currently in the foreground.
     */
    val isInForeground: Boolean

    /**
     * How the application was entered.  This is writeable to allow for the setting
     * to [AppEntryAction.NOTIFICATION_CLICK] when it is determined a notification
     * drove the app entry.
     */
    var entryState: ApplicationEntryAction

    fun addApplicationLifecycleHandler(handler: IApplicationLifecycleHandler)
    fun removeApplicationLifecycleHandler(handler: IApplicationLifecycleHandler)
}
