# Keep all SMS and telephony classes
-keep class android.telephony.** { *; }
-keep class com.android.internal.telephony.** { *; }

# Keep WorkManager workers (referenced by class name)
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Keep our app classes
-keep class com.o2.auto2gb.** { *; }

# Keep BroadcastReceiver and Service subclasses
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.app.Service

# Prevent stripping of AppPrefs (used via reflection by boot/alarm)
-keepclassmembers class com.o2.auto2gb.AppPrefs { *; }

# WorkManager
-keepnames class androidx.work.** { *; }
-keep class androidx.work.impl.** { *; }

# Keep R classes
-keepclassmembers class **.R$* { public static <fields>; }
