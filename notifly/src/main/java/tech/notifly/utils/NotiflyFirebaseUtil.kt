package tech.notifly.utils

import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

internal object NotiflyFirebaseUtil {
    suspend fun getFcmToken(): String? {
        Logger.d("[NotiflyFirebaseUtil] Attempting to fetch FCM token")

        return try {
            val token = FirebaseMessaging.getInstance().token.await()
            Logger.d("[NotiflyFirebaseUtil] FCM token retrieved: $token")
            token
        } catch (e: Exception) {
            Logger.e("[NotiflyFirebaseUtil] Failed to get FCM token", e) // 기존
            null
        }
    }
}
