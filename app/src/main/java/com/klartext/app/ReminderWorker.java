package com.klartext.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/**
 * Fires once a day — scheduled from the web layer's Profile screen reminder
 * toggle via MainActivity's ReminderBridge (see the AndroidReminder
 * JS-bridge object in assets/index.html, following the same
 * addJavascriptInterface pattern as AndroidTTS) — and posts a local
 * notification nudging the user back into a session.
 *
 * If the web layer had a fresh due-review count at the moment the reminder
 * was (re)scheduled, the notification references it directly (e.g. "38
 * words are waiting for review"). That count is only as fresh as the last
 * time schedule() was called (app open, or the toggle/time being changed on
 * the Profile screen) — not live at notification-fire-time, since this
 * Worker runs without the WebView/JS layer. That's an accepted first-pass
 * limitation per the feature spec: a slightly-stale count (or the generic
 * fallback message below, if no count was ever supplied) beats blocking the
 * whole feature on live cross-layer data passing.
 */
public class ReminderWorker extends Worker {
    public static final String CHANNEL_ID = "klartext_daily_reminder";
    public static final String KEY_DUE_COUNT = "due_count";
    public static final String KEY_SCHEDULED_AT = "scheduled_at";
    // A due-count older than this is too stale to quote, so the generic text is used.
    private static final long MAX_COUNT_AGE_MS = 26L * 60 * 60 * 1000;
    private static final int NOTIFICATION_ID = 1001;

    public ReminderWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        int dueCount = getInputData().getInt(KEY_DUE_COUNT, -1);
        long scheduledAt = getInputData().getLong(KEY_SCHEDULED_AT, 0L);
        if (scheduledAt <= 0 || System.currentTimeMillis() - scheduledAt > MAX_COUNT_AGE_MS) {
            dueCount = -1; // the app has not been opened for a day or more: the number would be wrong
        }

        String title = "Time to practice German";
        String text = dueCount > 0
                ? dueCount + (dueCount == 1 ? " word is" : " words are") + " waiting for review"
                : "Keep your streak going with a quick round";

        Intent launchIntent = new Intent(context, MainActivity.class);
        launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingIntentFlags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, launchIntent, pendingIntentFlags);

        Notification notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_reminder)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build();

        // POST_NOTIFICATIONS (API 33+) is requested when the user first turns
        // the reminder ON from the Profile screen (see
        // MainActivity.ReminderBridge#requestPermission()). If it was denied
        // or later revoked, this quietly no-ops instead of crashing the
        // background work.
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification);
        } catch (SecurityException ignored) {
            // Permission not granted — nothing to do; the app doesn't nag
            // about it outside the explicit opt-in toggle.
        }
        return Result.success();
    }

    /** Must be called once (idempotent) before the first notification is ever posted. */
    public static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Daily study reminder", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("A once-a-day nudge to practice your German vocabulary.");
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
}
