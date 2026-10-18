# Add project specific ProGuard rules here.
# This app is a single WebView loading local assets, so no special rules are needed
# for that part. minifyEnabled is currently false, so none of this is active yet —
# but WorkManager's daily-reminder Worker is instantiated via reflection, so if
# minification is ever turned on later it needs an explicit keep rule or it will
# silently fail to run.
-keep class com.example.ezgerman.ReminderWorker { <init>(android.content.Context, androidx.work.WorkerParameters); }
