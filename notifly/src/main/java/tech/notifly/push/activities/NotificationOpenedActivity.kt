package tech.notifly.push.activities

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import tech.notifly.Notifly
import tech.notifly.application.ApplicationEntryAction
import tech.notifly.application.IApplicationService
import tech.notifly.push.PushNotificationManager
import tech.notifly.push.interfaces.IPushNotification
import tech.notifly.services.NotiflyServiceProvider
import tech.notifly.utils.Logger
import tech.notifly.utils.NotiflyLogUtil
import tech.notifly.utils.OSUtil

class NotificationOpenedActivity : AppCompatActivity() {
    companion object {
        /** 광고 푸시 "수신거부" 액션에서 이동할 URL을 전달하는 인텐트 extra 키 */
        const val EXTRA_UNSUBSCRIBE_URL = "notifly_unsubscribe_url"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Notifly.initializeWithContext(this)) {
            return
        }
        handleIntent(intent)
        finish()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (!Notifly.initializeWithContext(this)) {
            return
        }
        intent?.let { handleIntent(it) }
        finish()
    }

    private fun handleIntent(intent: Intent) {
        val notification =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getSerializableExtra("notification", IPushNotification::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getSerializableExtra("notification")
                    as? IPushNotification
            }

        if (notification == null) {
            finish()
            return
        }

        // 광고 푸시 "수신거부" 액션: 본문 클릭과 분리한다.
        // 일반 클릭 트래킹(push_click)·notificationOpened 콜백을 발화하지 않고
        // (앱이 본문 클릭으로 오인해 중복 처리하는 것을 방지), 알림 제거 + unsubscribe_url 이동만 수행.
        val unsubscribeUrl = intent.getStringExtra(EXTRA_UNSUBSCRIBE_URL)
        if (unsubscribeUrl != null) {
            // 액션 버튼 탭은 setAutoCancel 대상이 아니므로 알림을 직접 제거한다.
            NotificationManagerCompat.from(this).cancel(notification.androidNotificationId)
            navigateTo(unsubscribeUrl)
            return
        }

        val url = notification.url
        val campaignId = notification.campaignId
        val notiflyMessageId = notification.notiflyMessageId
        val wasAppInForeground = intent.getBooleanExtra("was_app_in_foreground", false)

        // Log the push click event
        NotiflyLogUtil.logEventNonBlocking(
            this,
            "push_click",
            mapOf(
                "type" to "message_event",
                "channel" to "push-notification",
                "campaign_id" to campaignId,
                "notifly_message_id" to notiflyMessageId,
                "status" to if (wasAppInForeground) "foreground" else "background",
            ),
            listOf(),
            true,
        )

        // Fire callbacks for push click event
        PushNotificationManager.notificationOpened(notification)

        navigateTo(url)
    }

    private fun navigateTo(url: String?) {
        try {
            val applicationService = NotiflyServiceProvider.getService<IApplicationService>()
            if (!applicationService.isInForeground) {
                applicationService.entryState = ApplicationEntryAction.NOTIFICATION_CLICK
            }
            // Open the URL or launch the app
            val destinationIntent = getIntent(url)
            if (destinationIntent != null) {
                startActivity(destinationIntent)
            }
        } catch (e: Exception) {
            Logger.w("Failed to open URL or launch app", e)
        }
    }

    private fun getIntent(url: String?): Intent? {
        val uri =
            if (url != null) {
                Uri.parse(url.trim { it <= ' ' })
            } else {
                null
            }

        return if (uri != null) {
            OSUtil.openURLInBrowserIntent(uri)
        } else {
            packageManager.getLaunchIntentForPackage(packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }
}
