-keepclassmembers class tech.notifly.** { *; }

# These classes are used by reflection
-keep class tech.notifly.utils.NotiflyAuthUtil { *; }
-keep class tech.notifly.utils.NotiflyFirebaseUtil { *; }
-keep class tech.notifly.push.interfaces.IPushNotification { *; }
-keep class tech.notifly.push.impl.PushNotification { *; }

# Proguard ends up removing this class even if it is used in AndroidManifest.xml so force keeping it.
-keep class tech.notifly.push.FCMBroadcastReceiver { *; }
-keep class tech.notifly.push.activities.NotificationOpenedActivity { *; }
-keep class tech.notifly.inapp.NotiflyInAppMessageActivity { *; }
