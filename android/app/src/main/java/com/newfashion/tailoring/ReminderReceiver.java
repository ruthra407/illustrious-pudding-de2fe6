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
            "reminder_voice_channel_v3";

    private static final int DEFAULT_NOTIFICATION_ID = 1001;

    @Override
    public void onReceive(Context context, Intent intent) {

        final Context appContext =
                context.getApplicationContext();

        String title =
                intent.getStringExtra("title");

        String message =
                intent.getStringExtra("message");

        if (title == null ||
                title.trim().isEmpty()) {

            title = "நினைவூட்டல்";
        }

        if (message == null ||
                message.trim().isEmpty()) {

            message =
                    "உங்களுக்கு ஒரு நினைவூட்டல் உள்ளது.";
        }

        final String finalTitle = title;
        final String finalMessage = message;

        int notificationId =
                intent.getIntExtra(
                        "requestCode",
                        intent.getIntExtra(
                                "notification_id",
                                DEFAULT_NOTIFICATION_ID
                        )
                );

        Log.d(TAG, "================================");
        Log.d(TAG, "REMINDER RECEIVED");
        Log.d(TAG, "Title: " + finalTitle);
        Log.d(TAG, "Message: " + finalMessage);
        Log.d(TAG, "Notification ID: " + notificationId);

        createNotificationChannel(appContext);

        /*
         * Notification tap -> MainActivity.
         *
         * Voice playback does NOT depend
         * on notification tap.
         */
        Intent openIntent =
                new Intent(
                        appContext,
                        MainActivity.class
                );

        openIntent.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK |
                        Intent.FLAG_ACTIVITY_CLEAR_TOP |
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
        );

        PendingIntent pendingIntent =
                PendingIntent.getActivity(
                        appContext,
                        notificationId,
                        openIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT |
                                PendingIntent.FLAG_IMMUTABLE
                );

        /*
         * Main reminder notification.
         */
        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(
                        appContext,
                        CHANNEL_ID
                )
                        .setSmallIcon(
                                android.R.drawable.ic_popup_reminder
                        )
                        .setContentTitle(
                                finalTitle
                        )
                        .setContentText(
                                finalMessage
                        )
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
                        .setContentIntent(
                                pendingIntent
                        )
                        .setSilent(true);

        NotificationManager manager =
                (NotificationManager)
                        appContext.getSystemService(
                                Context.NOTIFICATION_SERVICE
                        );

        if (manager != null) {

            if (Build.VERSION.SDK_INT <
                    Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(
                            appContext,
                            Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED) {

                manager.notify(
                        notificationId,
                        builder.build()
                );

                Log.d(
                        TAG,
                        "REMINDER NOTIFICATION SHOWN"
                );

            } else {

                Log.e(
                        TAG,
                        "POST_NOTIFICATIONS permission missing"
                );
            }
        }

        /*
         * IMPORTANT:
         *
         * Start ReminderVoiceService immediately.
         *
         * User does NOT need to tap
         * the notification.
         */
        Intent voiceIntent =
                new Intent(
                        appContext,
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
                notificationId
        );

        try {

            Log.d(
                    TAG,
                    "STARTING ReminderVoiceService NOW"
            );

            if (Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.O) {

                ContextCompat.startForegroundService(
                        appContext,
                        voiceIntent
                );

            } else {

                appContext.startService(
                        voiceIntent
                );
            }

            Log.d(
                    TAG,
                    "ReminderVoiceService START COMMAND SENT"
            );

        } catch (Exception error) {

            Log.e(
                    TAG,
                    "FAILED TO START ReminderVoiceService",
                    error
            );
        }

        Log.d(
                TAG,
                "REMINDER RECEIVER FINISHED"
        );

        Log.d(
                TAG,
                "================================"
        );
    }

    private void createNotificationChannel(
            Context context
    ) {

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
                "Tailoring reminder notifications with voice"
        );

        /*
         * Notification sound is disabled.
         *
         * Female voice is played separately
         * by ReminderVoiceService.
         */
        channel.setSound(
                null,
                null
        );

        manager.createNotificationChannel(
                channel
        );
    }
}
