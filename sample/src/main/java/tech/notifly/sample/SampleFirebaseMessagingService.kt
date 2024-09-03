package tech.notifly.sample

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class SampleFirebaseMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d("SampleApplication", "onMessageReceived: ${message.data}")
    }

    override fun onNewToken(token: String) {
        Log.d("SampleApplication", "Refreshed token: $token")
    }
}
