package tech.notifly.sample


import android.app.Application
import tech.notifly.Notifly

// import com.onesignal.OneSignal
// const val ONESIGNAL_APP_ID = "0fb00786-17c7-409a-8210-27fdb0e941a1"


class SampleApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifly.initialize(
            context = applicationContext,
            projectId = "b80c3f0e2fbd5eb986df4f1d32ea2871",
            username = BuildConfig.NOTIFLY_USERNAME,
            password = BuildConfig.NOTIFLY_PASSWORD,
        )

        // OneSignal Initialization
        /*OneSignal.setLogLevel(OneSignal.LOG_LEVEL.VERBOSE, OneSignal.LOG_LEVEL.NONE)
        OneSignal.initWithContext(this)
        OneSignal.setAppId(ONESIGNAL_APP_ID)
        OneSignal.promptForPushNotifications()*/
    }
}
