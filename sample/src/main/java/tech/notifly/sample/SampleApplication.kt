package tech.notifly.sample


import android.app.Application
import android.util.Log
import tech.notifly.Notifly
import tech.notifly.push.interfaces.INotificationClickEvent
import tech.notifly.push.interfaces.INotificationClickListener

class SampleApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        Notifly.setLogLevel(Log.VERBOSE)

        Notifly.initialize(
            context = applicationContext,
            projectId = "b80c3f0e2fbd5eb986df4f1d32ea2871",
            username = BuildConfig.NOTIFLY_USERNAME,
            password = BuildConfig.NOTIFLY_PASSWORD,
        )

        Notifly.addNotificationClickListener(object : INotificationClickListener {
            override fun onClick(event: INotificationClickEvent) {
                Log.d("SampleApplication", "Notification clicked: ${event.notification.toString()}")
            }
        })

        // OneSignal Initialization
        /*OneSignal.setLogLevel(OneSignal.LOG_LEVEL.VERBOSE, OneSignal.LOG_LEVEL.NONE)
        OneSignal.initWithContext(this)
        OneSignal.setAppId(ONESIGNAL_APP_ID)
        OneSignal.promptForPushNotifications()*/
    }
}
