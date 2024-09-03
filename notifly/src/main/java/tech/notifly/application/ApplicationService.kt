package tech.notifly.application

import android.app.Activity
import android.app.Application
import android.app.Application.ActivityLifecycleCallbacks
import android.content.ComponentCallbacks
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import tech.notifly.utils.EventProducer
import tech.notifly.utils.Logger
import tech.notifly.utils.OSUtil

class ApplicationService :
    IApplicationService,
    ActivityLifecycleCallbacks,
    OnGlobalLayoutListener {
    private val applicationLifecycleNotifier = EventProducer<IApplicationLifecycleHandler>()

    private var _appContext: Context? = null
    override val appContext: Context
        get() = _appContext!!

    override val isInForeground: Boolean
        get() = entryState.isAppOpen || entryState.isNotificationClick
    override var entryState: ApplicationEntryAction = ApplicationEntryAction.APP_CLOSE

    private var firstStarted: Boolean = true

    private var _current: Activity? = null
    override var current: Activity?
        get() = _current
        set(value) {
            _current = value

            Logger.d("ApplicationService: current activity=$current")

            if (value != null) {
                try {
                    value
                        .window
                        .decorView
                        .viewTreeObserver
                        .addOnGlobalLayoutListener(this)
                } catch (e: RuntimeException) {
                    // Related to Unity Issue #239 on Github
                    // https://github.com/OneSignal/OneSignal-Unity-SDK/issues/239
                    // RuntimeException at ActivityLifecycleHandler.setCurActivity on Android (Unity 2.9.0)
                    e.printStackTrace()
                }
            }
        }

    /** Whether the next resume is due to the first activity or not **/
    private var nextResumeIsFirstActivity: Boolean = false

    /** Used to determine when an app goes in focus and out of focus **/
    private var activityReferences = 0
    private var isActivityChangingConfigurations = false

    fun start(context: Context) {
        _appContext = context

        val application = context.applicationContext as Application
        application.registerActivityLifecycleCallbacks(this)

        val configuration =
            object : ComponentCallbacks {
                override fun onConfigurationChanged(newConfig: Configuration) {
                    // If Activity contains the configChanges orientation flag, re-create the view this way
                    if (current != null &&
                        OSUtil.hasConfigChangeFlag(
                            current!!,
                            ActivityInfo.CONFIG_ORIENTATION,
                        )
                    ) {
                        onOrientationChanged(newConfig.orientation, current!!)
                    }
                }

                override fun onLowMemory() {}
            }

        application.registerComponentCallbacks(configuration)

        val isContextActivity = context is Activity
        val isCurrentActivityNull = current == null

        Logger.d("ApplicationService.start: isContextActivity=$isContextActivity, isCurrentActivityNull=$isCurrentActivityNull")

        if (!isCurrentActivityNull || isContextActivity) {
            entryState = ApplicationEntryAction.APP_OPEN
            if (isCurrentActivityNull && isContextActivity) {
                // If the current activity is null, but the context is an activity, then the app is being opened
                current = context as Activity?
                activityReferences = 1
                nextResumeIsFirstActivity = false

                if (firstStarted) {
                    firstStarted = false
                    applicationLifecycleNotifier.fire { it.onFocus(true) }
                }
            }
        } else {
            nextResumeIsFirstActivity = true
            entryState = ApplicationEntryAction.APP_CLOSE
        }

        Logger.d("ApplicationService.init: entryState=$entryState")
    }

    override fun addApplicationLifecycleHandler(handler: IApplicationLifecycleHandler) {
        applicationLifecycleNotifier.subscribe(handler)
    }

    override fun removeApplicationLifecycleHandler(handler: IApplicationLifecycleHandler) {
        applicationLifecycleNotifier.unsubscribe(handler)
    }

    override fun onActivityCreated(
        activity: Activity,
        bundle: Bundle?,
    ) {
        Logger.d("ApplicationService.onActivityCreated($activityReferences,$entryState): $activity")
    }

    override fun onActivityStarted(activity: Activity) {
        Logger.d(
            "ApplicationService.onActivityStarted($activityReferences,$entryState): $activity",
        )

        if (current == activity) {
            return
        }

        current = activity

        if ((!isInForeground || nextResumeIsFirstActivity) && !isActivityChangingConfigurations) {
            activityReferences = 1
            handleFocus()
        } else {
            activityReferences++
        }
    }

    override fun onActivityResumed(activity: Activity) {
        Logger.d("ApplicationService.onActivityResumed($activityReferences,$entryState): $activity")

        // When an activity has something shown above it, it will be paused allowing
        // the new activity to be started (where current is set).  However when that
        // new activity is finished the original activity is simply resumed (it's
        // already been created).  For this case, we make sure current is set
        // to the now current activity.
        if (current != activity) {
            current = activity
        }

        if ((!isInForeground || nextResumeIsFirstActivity) && !isActivityChangingConfigurations) {
            activityReferences = 1
            handleFocus()
        }
    }

    override fun onActivityPaused(activity: Activity) {
        Logger.d("ApplicationService.onActivityPaused($activityReferences,$entryState): $activity")
    }

    override fun onActivityStopped(activity: Activity) {
        Logger.d("ApplicationService.onActivityStopped($activityReferences,$entryState): $activity")

        isActivityChangingConfigurations = activity.isChangingConfigurations
        if (!isActivityChangingConfigurations && --activityReferences <= 0) {
            current = null
            activityReferences = 0
            handleLostFocus()
        }
    }

    override fun onActivitySaveInstanceState(
        p0: Activity,
        p1: Bundle,
    ) {
        // Intentionally left empty
    }

    override fun onActivityDestroyed(activity: Activity) {
        Logger.d("ApplicationService.onActivityDestroyed($activityReferences,$entryState): $activity")
    }

    override fun onGlobalLayout() {
        // Intentionally left empty
    }

    private fun onOrientationChanged(
        orientation: Int,
        activity: Activity,
    ) {
        // Log device orientation change
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            Logger.d(
                "ApplicationService.onOrientationChanged: Configuration Orientation Change: LANDSCAPE ($orientation) on activity: $activity",
            )
        } else if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            Logger.d(
                "ApplicationService.onOrientationChanged: Configuration Orientation Change: PORTRAIT ($orientation) on activity: $activity",
            )
        }

        handleLostFocus()

        activity
            .window
            .decorView
            .viewTreeObserver
            .addOnGlobalLayoutListener(this)

        handleFocus()
    }

    private fun handleLostFocus() {
        if (isInForeground) {
            Logger.d("ApplicationService.handleLostFocus: application is now out of focus")

            entryState = ApplicationEntryAction.APP_CLOSE

            applicationLifecycleNotifier.fire { it.onUnfocused() }
        } else {
            Logger.d("ApplicationService.handleLostFocus: application already out of focus")
        }
    }

    private fun handleFocus() {
        if (!isInForeground || nextResumeIsFirstActivity) {
            var first: Boolean = false

            if (firstStarted) {
                Logger.d("ApplicationService.handleFocus: application is first started")
                firstStarted = false
                first = true
            }

            Logger.d(
                "ApplicationService.handleFocus: application is now in focus, nextResumeIsFirstActivity=$nextResumeIsFirstActivity",
            )
            nextResumeIsFirstActivity = false

            // We assume we are called *after* the notification module has determined entry due to notification.
            if (entryState != ApplicationEntryAction.NOTIFICATION_CLICK) {
                entryState = ApplicationEntryAction.APP_OPEN
            }

            applicationLifecycleNotifier.fire { it.onFocus(first) }
        } else {
            Logger.d("ApplicationService.handleFocus: application never lost focus")
        }
    }
}
