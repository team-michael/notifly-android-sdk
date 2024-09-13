package tech.notifly.sample

import android.app.Application
import android.content.Intent
import android.graphics.BitmapFactory
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tech.notifly.Notifly
import tech.notifly.push.interfaces.INotificationClickEvent
import tech.notifly.push.interfaces.INotificationClickListener
import tech.notifly.push.interfaces.INotificationInterceptor
import tech.notifly.push.interfaces.IPushNotification
import java.net.URL

class SampleApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        Notifly.setLogLevel(Log.VERBOSE)
        Notifly.preferences.inAppMessage.apply {
            setIntentFlagsForInAppLinkOpening(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            setCampaignRevalidationIntervalMillis(1000 * 60 * 1)
        }

        Notifly.initialize(
            context = applicationContext,
            projectId = "b80c3f0e2fbd5eb986df4f1d32ea2871",
            username = BuildConfig.NOTIFLY_USERNAME,
            password = BuildConfig.NOTIFLY_PASSWORD,
        )

        Notifly.addNotificationClickListener(
            object : INotificationClickListener {
                override fun onClick(event: INotificationClickEvent) {
                    Log.d("SampleApplication", "Notification clicked: ${event.notification}")
                }
            },
        )

        Notifly.addNotificationInterceptor(
            object : INotificationInterceptor {
                override fun postBuild(
                    builder: NotificationCompat.Builder,
                    notification: IPushNotification,
                ): NotificationCompat.Builder {
                    builder.setColor(ContextCompat.getColor(applicationContext, R.color.purple_700))
                    return builder
                }

                override suspend fun postBuildAsync(
                    builder: NotificationCompat.Builder,
                    notification: IPushNotification,
                ): NotificationCompat.Builder =
                    withContext(Dispatchers.IO) {
                        try {
                            val bitmap =
                                URL(
                                    notification.imageUrl,
                                ).openConnection().getInputStream().use(BitmapFactory::decodeStream)
                            builder.setLargeIcon(bitmap)
                            builder
                        } catch (e: Exception) {
                            e.printStackTrace()
                            builder
                        }
                    }
            },
        )

        // OneSignal Initialization
        /*OneSignal.setLogLevel(OneSignal.LOG_LEVEL.VERBOSE, OneSignal.LOG_LEVEL.NONE)
        OneSignal.initWithContext(this)
        OneSignal.setAppId(ONESIGNAL_APP_ID)
        OneSignal.promptForPushNotifications()*/
    }
}
