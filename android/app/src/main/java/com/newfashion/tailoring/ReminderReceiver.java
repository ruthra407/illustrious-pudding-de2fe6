package com.newfashion.tailoring;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

public class ReminderReceiver extends BroadcastReceiver {

    private static final String CHANNEL_ID = "reminder_voice_channel_v1";
    private static final int DEFAULT_NOTIFICATION_ID = 1001;

    @Override
    public void onReceive(Context context, Intent intent) {

        String title = intent.getStringExtra("title");
        String message = intent.getStringExtra("message");

        if (title == null || title.trim().isEmpty()) {
            title = "நினைவூட்டல்";
        }

        if (message == null || message.trim().isEmpty()) {
            message = "உங்களுக்கு ஒரு நினைவூட்டல் உள்ளது.";
        }

        int notificationId = intent.getIntExtra(
                "requestCode",
                DEFAULT_NOTIFICATION_ID
        );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }

        createNotificationChannel(context);

        Intent openIntent = new Intent(
                context,
                MainActivity.class
        );

        openIntent.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
        );

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT
                        | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(
                        context,
                        CHANNEL_ID
                )
                        .setSmallIcon(
                                android.R.drawable.ic_popup_reminder
                        )
                        .setContentTitle(title)
                        .setContentText(message)
                        .setStyle(
                                new NotificationCompat.BigTextStyle()
                                        .bigText(message)
                        )
                        .setPriority(
                                NotificationCompat.PRIORITY_HIGH
                        )
                        .setAutoCancel(true)
                        .setContentIntent(pendingIntent);

        NotificationManager notificationManager =
                (NotificationManager)
                        context.getSystemService(
                                Context.NOTIFICATION_SERVICE
                        );

        if (notificationManager != null) {
            notificationManager.notify(
                    notificationId,
                    builder.build()
            );
        }
    }

    private void createNotificationChannel(Context context) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            NotificationManager notificationManager =
                    context.getSystemService(
                            NotificationManager.class
                    );

            if (notificationManager == null) {
                return;
            }

            Uri soundUri = Uri.parse(
                    "android.resource://"
                            + context.getPackageName()
                            + "/"
                            + R.raw.voice_for_elevenlabs
            );

            AudioAttributes audioAttributes =
                    new AudioAttributes.Builder()
                            .setUsage(
                                    AudioAttributes.USAGE_NOTIFICATION
                            )
                            .setContentType(
                                    AudioAttributes.CONTENT_TYPE_SPEECH
                            )
                            .build();

            NotificationChannel channel =
                    new NotificationChannel(
                            CHANNEL_ID,
                            "தமிழ் நினைவூட்டல்கள்",
                            NotificationManager.IMPORTANCE_HIGH
                    );

            channel.setDescription(
                    "Tailoring reminder notifications"
            );

            channel.setSound(
                    soundUri,
                    audioAttributes
            );

            notificationManager.createNotificationChannel(
                    channel
            );
        }
    }
}
