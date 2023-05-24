package tech.notifly.utils

import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import tech.notifly.Logger

internal object NotiflyFirebaseUtil {

    suspend fun getFcmToken(): String? {
        return try {
            FirebaseMessaging.getInstance().token.await()
        } catch (e: Exception) {
            Logger.e("[Notifly] Failed to get FCM token", e)
            null
        }
    }
}
