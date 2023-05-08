@file:Suppress("DEPRECATION")

package tech.notifly

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.legacy.content.WakefulBroadcastReceiver
import org.json.JSONException
import org.json.JSONObject

class FCMBroadcastReceiver : WakefulBroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Thread {
            try {
                showNotification(context, intent)
            } catch (e: Exception) {
                Log.e(Notifly.TAG, "onReceive failed", e)
            }
        }.start()
    }

    @Throws(Exception::class)
    private fun showNotification(context: Context, intent: Intent) {
        val extras = intent.extras ?: run {
            Log.e(Notifly.TAG, "intent extras is NULL")
            return
        }

        // console log extras
        val keys = extras.keySet()
        for (key in keys) {
            Log.d(Notifly.TAG, "FCMBroadcastReceiver intent key: $key, value: " + extras.get(key))
        }

        val jsonObject = bundleAsJSONObject(extras)
        // check if jsonObject has notifly key
        if (!jsonObject.has("notifly")) {
            Log.d(Notifly.TAG, "FCMBroadcastReceiver intent extras does not have notifly key")
            return
        }
        val notiflyString = jsonObject.getString("notifly")
        val notiflyJSONObject = JSONObject(notiflyString)

        val notiflyNotification = Notification(notiflyJSONObject)
        val notificationId = 1 // Any Unique ID TODO: use notificationId from server

        val url = notiflyNotification.url
        val launchIntent = if (url != null) {
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } else {
            context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        launchIntent?.putExtra("title", notiflyNotification.title)
        launchIntent?.putExtra("body", notiflyNotification.body)

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelName = "Notifly Notification Channel"
            val channelDescription = "This is the Notifly Notification Channel"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel =
                NotificationChannel(Notifly.NOTIFICATION_CHANNEL_ID, channelName, importance)
            channel.description = channelDescription

            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }

        val builder = NotificationCompat.Builder(context)
            .setSmallIcon(android.R.drawable.ic_delete) // TODO: replace with a default icon
            .setContentText(notiflyNotification.body)
            .setContentTitle(
                notiflyNotification.title
                    ?: context.applicationInfo.loadLabel(context.packageManager).toString()
            )
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        // builder.setStyle(NotificationCompat.BigTextStyle().bigText(notiflyNotification.body))

        val notification = builder.build()
        // log
        Log.d(Notifly.TAG, "FCMBroadcastReceiver notification: $notification")

        // Show the notification
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            // log
            Log.d(Notifly.TAG, "FCMBroadcastReceiver permission granted")
            // TODO(minyong): figure out why notification is not showing
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } else {
            Log.w(Notifly.TAG, "POST_NOTIFICATIONS permission is not granted")
        }
    }

    private fun bundleAsJSONObject(bundle: Bundle): JSONObject {
        val json = JSONObject()
        val keys = bundle.keySet()

        for (key in keys) {
            try {
                json.put(key, bundle.get(key))
            } catch (e: JSONException) {
                Log.e(Notifly.TAG, "Failed to convert bundle to json", e)
            }
        }
        return json
    }

}
