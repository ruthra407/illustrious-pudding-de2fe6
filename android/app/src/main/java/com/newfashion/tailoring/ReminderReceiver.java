package com.newfashion.tailoring;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

public class ReminderReceiver extends BroadcastReceiver {

    private static final String CHANNEL_ID = "tailoring_reminders";

    @Override
    public void onReceive(Context context, Intent intent) {

        String title = intent.getStringExtra("title");

        String body = intent.getStringExtra("body");

        if (title == null || title.trim().isEmpty()) {
            title = "Tailoring Reminder";
        }

        if (body == null || body.trim().isEmpty()) {
            body = "You have a reminder.";
        }

        createNotificationChannel(context);

        Intent openIntent =
                new Intent(context, MainActivity.class);

        openIntent.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
        );

        PendingIntent pendingIntent =
                PendingIntent.getActivity(
                        context,
                        1001,
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
                        android.R.drawable.ic_dialog_info
                )
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(
                        new NotificationCompat.BigTextStyle()
                                .bigText(body)
                )
                .setPriority(
                        NotificationCompat.PRIORITY_HIGH
                )
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        NotificationManager manager =
                (NotificationManager)
                        context.getSystemService(
                                Context.NOTIFICATION_SERVICE
                        );

        if (manager != null) {
            int notificationId =
                    12000
                            + (int)
                            (System.currentTimeMillis() % 1000);

            manager.notify(
                    notificationId,
                    builder.build()
            );
        }
    }

    private void createNotificationChannel(
            Context context
    ) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            NotificationChannel channel =
                    new NotificationChannel(
                            CHANNEL_ID,
                            "Tailoring Reminders",
                            NotificationManager.IMPORTANCE_HIGH
                    );

            channel.setDescription(
                    "Reminder notifications"
            );

            NotificationManager manager =
                    context.getSystemService(
                            NotificationManager.class
                    );

            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
}
