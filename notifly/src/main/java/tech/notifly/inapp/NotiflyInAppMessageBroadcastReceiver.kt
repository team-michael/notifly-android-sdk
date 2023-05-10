package tech.notifly.inapp

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.util.Log
import tech.notifly.Notifly

class NotiflyInAppMessageBroadcastReceiver : BroadcastReceiver() {

    private val inAppMessageHandler = InAppMessageHandler()

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(Notifly.TAG, "NotiflyInAppMessageBroadcastReceiver onReceive")
        val activity = getActivity(context)
        activity?.let {
            // Currently this code is not reachable since intent comes from WakefulBroadcastReceiver.
            Log.d(Notifly.TAG, "NotiflyInAppMessageBroadcastReceiver onReceive activity: $activity")
            inAppMessageHandler.handleInAppMessage(it, intent)
        }
    }

    // TODO: This does not work since intent comes from WakefulBroadcastReceiver.
    // Figure out a way to get the current activity of the app.
    private fun getActivity(context: Context): Activity? {
        return if (context is Activity) {
            context
        } else {
            (context as ContextWrapper).baseContext as? Activity
        }
    }
}
