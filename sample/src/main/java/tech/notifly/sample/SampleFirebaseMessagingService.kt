package tech.notifly.sample

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import tech.notifly.Notifly

class SampleFirebaseMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d("Sample", "onMessageReceived: $message")

        val inAppMessageHandled = Notifly.handleInAppMessage(this, message)
        if (inAppMessageHandled) return

        val isPushNotificationHandled = Notifly.handlePushNotification(this, message)
        if (isPushNotificationHandled) return
    }

    override fun onNewToken(token: String) {
        Log.d("Sample", "Refreshed token: $token")
    }
}