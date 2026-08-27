package com.newfashion.tailoring;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

public class ReminderReceiver extends BroadcastReceiver {

    private static final String TAG = "ReminderReceiver";

    private static final String CHANNEL_ID =
            "reminder_voice_channel_v4";

    private static final int DEFAULT_NOTIFICATION_ID = 1001;

    @Override
    public void onReceive(Context context, Intent intent) {

        Context app = context.getApplicationContext();

        if (intent == null) {
            Log.e(TAG, "Received null intent");
            return;
        }

        String title = intent.getStringExtra("title");
        String message = intent.getStringExtra("message");

        if (title == null || title.trim().isEmpty()) {
            title = "நினைவூட்டல்";
        }

        if (message == null || message.trim().isEmpty()) {
            message = "உங்களுக்கு ஒரு நினைவூட்டல் உள்ளது.";
        }

        final String finalTitle = title;
        final String finalMessage = message;

        int id = intent.getIntExtra(
                "requestCode",
                intent.getIntExtra(
                        "notification_id",
                        DEFAULT_NOTIFICATION_ID
                )
        );

        Log.d(
                TAG,
                "========================================"
        );

        Log.d(
                TAG,
                "REMINDER RECEIVED"
        );

        Log.d(
                TAG,
                "ID = " + id
        );

        Log.d(
                TAG,
                "TITLE = " + finalTitle
        );

        Log.d(
                TAG,
                "MESSAGE = " + finalMessage
        );

        Log.d(
                TAG,
                "TIME = " + System.currentTimeMillis()
        );

        Log.d(
                TAG,
                "========================================"
        );

        // ---------------------------------------------------------
        // 1. Notification
        // ---------------------------------------------------------

        createNotificationChannel(app);

        Intent openIntent = new Intent(
                app,
                MainActivity.class
        );

        openIntent.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_CLEAR_TOP |
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        );

        PendingIntent pendingIntent =
                PendingIntent.getActivity(
                        app,
                        id,
                        openIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT |
                        PendingIntent.FLAG_IMMUTABLE
                );

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(
                        app,
                        CHANNEL_ID
                )
                        .setSmallIcon(
                                android.R.drawable.ic_popup_reminder
                        )
                        .setContentTitle(finalTitle)
                        .setContentText(finalMessage)
                        .setStyle(
                                new NotificationCompat.BigTextStyle()
                                        .bigText(finalMessage)
                        )
                        .setPriority(
                                NotificationCompat.PRIORITY_HIGH
                        )
                        .setCategory(
                                NotificationCompat.CATEGORY_REMINDER
                        )
                        .setAutoCancel(true)
                        .setContentIntent(pendingIntent)
                        .setSilent(true);

        NotificationManager notificationManager =
                (NotificationManager)
                        app.getSystemService(
                                Context.NOTIFICATION_SERVICE
                        );

        if (notificationManager != null) {

            boolean canNotify =
                    Build.VERSION.SDK_INT <
                            Build.VERSION_CODES.TIRAMISU
                            ||
                    ContextCompat.checkSelfPermission(
                            app,
                            Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED;

            if (canNotify) {

                notificationManager.notify(
                        id,
                        builder.build()
                );

                Log.d(
                        TAG,
                        "Notification displayed successfully"
                );

            } else {

                Log.w(
                        TAG,
                        "POST_NOTIFICATIONS permission denied"
                );
            }
        }

        // ---------------------------------------------------------
        // 2. Schedule next daily reminder
        // ---------------------------------------------------------

        try {

            ReminderScheduler.rescheduleNextDailyReminder(
                    app,
                    id
            );

            Log.d(
                    TAG,
                    "Next daily reminder scheduled"
            );

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "Next daily reminder scheduling failed",
                    e
            );
        }

        // ---------------------------------------------------------
        // 3. Start Voice Service
        // ---------------------------------------------------------

        Intent voiceIntent =
                new Intent(
                        app,
                        ReminderVoiceService.class
                );

        voiceIntent.putExtra(
                "title",
                finalTitle
        );

        voiceIntent.putExtra(
                "message",
                finalMessage
        );

        voiceIntent.putExtra(
                "requestCode",
                id
        );

        Log.d(
                TAG,
                "Starting ReminderVoiceService..."
        );

        try {

            if (Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.O) {

                ContextCompat.startForegroundService(
                        app,
                        voiceIntent
                );

            } else {

                app.startService(
                        voiceIntent
                );
            }

            Log.d(
                    TAG,
                    "ReminderVoiceService START COMMAND SENT"
            );

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "ReminderVoiceService START FAILED",
                    e
            );
        }
    }

    // -------------------------------------------------------------
    // Notification Channel
    // -------------------------------------------------------------

    private void createNotificationChannel(Context context) {

        if (Build.VERSION.SDK_INT <
                Build.VERSION_CODES.O) {
            return;
        }

        NotificationManager manager =
                context.getSystemService(
                        NotificationManager.class
                );

        if (manager == null) {
            return;
        }

        NotificationChannel channel =
                new NotificationChannel(
                        CHANNEL_ID,
                        "தமிழ் நினைவூட்டல்கள்",
                        NotificationManager.IMPORTANCE_HIGH
                );

        channel.setDescription(
                "New Fashion Tailoring reminder notifications"
        );

        // Notification itself should not make sound.
        // Voice service handles the spoken reminder.
        channel.setSound(
                null,
                null
        );

        manager.createNotificationChannel(
                channel
        );
    }
}
