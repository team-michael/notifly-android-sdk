# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# If you want to remove debug logs in the release, uncomment the following
#-assumenosideeffects class android.util.Log {
#    # show Log.w and Log.e only in release builds
#    public static boolean isLoggable(java.lang.String, int);
#    public static int v(...);
#    public static int d(...);
#    public static int i(...);
#}

# To keep method names in your stack trace, add this line
-keepclassmembers class tech.notifly.Notifly.** { *; }

# To keep methods in Notifly
-keep class tech.notifly.Notifly { *; }
-keep class tech.notifly.NotiflySdkType { *; }
-keep class tech.notifly.NotiflyControlToken { *; }

-keep class tech.notifly.push.interfaces.** { *; }

# These 2 methods are called with reflection
-keep class tech.notifly.utils.NotiflyAuthUtil { *; }
-keep class tech.notifly.utils.NotiflyFirebaseUtil { *; }
