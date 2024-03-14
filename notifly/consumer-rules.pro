-keepclassmembers class tech.notifly.Notifly.** { *; }

# These 2 methods are called with reflection
-keep class tech.notifly.utils.NotiflyAuthUtil { *; }
-keep class tech.notifly.utils.NotiflyFirebaseUtil { *; }

# Proguard ends up removing this class even if it is used in AndroidManifest.xml so force keeping it.
-keep public class tech.notifly.push.FCMBroadcastReceiver { *; }
-keep public class tech.notifly.push.activities.NotificationOpenedActivity { *; }
-keep public class tech.notifly.inapp.NotiflyInAppMessageActivity { *; }
