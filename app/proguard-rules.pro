# O2 Auto 2GB — ProGuard Rules

# حفظ جميع كلاسات التطبيق
-keep class com.o2.auto2gb.** { *; }

# WorkManager Workers — يجب الحفاظ على constructor المحدد
-keep class * extends androidx.work.Worker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# BroadcastReceivers
-keep class * extends android.content.BroadcastReceiver { *; }

# Kotlin
-dontwarn kotlin.**
-keep class kotlin.Metadata { *; }
