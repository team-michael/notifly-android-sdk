# okhttp3 dontwarn rules
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.*
-dontwarn org.openjsse.**

-keepclassmembers class tech.notifly.** { *; }

# Proguard ends up removing this class even if it is used in AndroidManifest.xml so force keeping it.
-keep class tech.notifly.push.FCMBroadcastReceiver { *; }
-keep class tech.notifly.push.activities.NotificationOpenedActivity { *; }
-keep class tech.notifly.inapp.NotiflyInAppMessageActivity { *; }
