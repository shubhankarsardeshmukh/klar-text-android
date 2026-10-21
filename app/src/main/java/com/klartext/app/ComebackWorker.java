package com.klartext.app;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/**
 * A gentle nudge for learners who have stayed away. MainActivity schedules
 * four one-time jobs (after 2, 4, 7 and 14 days) whenever the app leaves the
 * screen, and cancels all of them the moment the app is opened again, so a
 * nudge only ever arrives if the learner really has been away that long.
 */
public class ComebackWorker extends Worker {
    public static final String KEY_DAYS = "days";
    private static final int NOTIFICATION_ID = 1002;

    public ComebackWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        SharedPreferences prefs = context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(MainActivity.PREF_COMEBACK, false)) return Result.success();
        int days = getInputData().getInt(KEY_DAYS, 2);
        int streak = prefs.getInt(MainActivity.PREF_STREAK, 0);

        String title;
        String text;
        if (days <= 2) {
            if (streak >= 3) {
                title = "Start a new streak";
                text = "You had a " + streak + " day streak. Two minutes today begins the next one.";
            } else {
                title = "Your German is waiting";
                text = "Two minutes today keeps your words from fading.";
            }
        } else if (days <= 4) {
            title = "Words fade fast without review";
            text = "Come back for a quick round. Your daily lesson is ready.";
        } else if (days <= 7) {
            title = "It has been a week";
            text = "Today's lesson takes about five minutes. Pick up where you left off.";
        } else {
            title = "Ready to start again?";
            text = "Easy words first. Your progress is saved.";
        }

        Intent launchIntent = new Intent(context, MainActivity.class);
        launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getActivity(context, 1, launchIntent, flags);

        ReminderWorker.ensureChannel(context);
        Notification n = new NotificationCompat.Builder(context, ReminderWorker.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_reminder)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, n);
        } catch (SecurityException ignored) {
            // Notification permission not granted: stay quiet.
        }
        return Result.success();
    }
}
