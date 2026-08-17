package com.newfashion.tailoring;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import org.json.JSONArray;
import org.json.JSONObject;

public class ReminderReceiver extends BroadcastReceiver {
    private static final String CHANNEL_ID = "nf_delivery_reminders";

    @Override public void onReceive(Context context, Intent intent) {
        int slot = intent.getIntExtra("slot", 0);
        int hour = intent.getIntExtra("hour", 0);
        int minute = intent.getIntExtra("minute", 0);

        android.content.SharedPreferences prefs =
                context.getSharedPreferences(
                        "nf_reminders",
                        Context.MODE_PRIVATE
                );

        String payload = prefs.getString(
                "payload",
                "{\"enabled\":false}"
        );

        boolean enabled = prefs.getBoolean("enabled", false);

        if (enabled) {
            showNotification(context, payload);
        }

        ReminderScheduler.scheduleNext(
                context,
                slot,
                hour,
                minute
        );
    }

    private void showNotification(
            Context context,
            String payload) {

        createChannel(context);

        String body = buildBody(payload);

        Intent launch = new Intent(
                context,
                MainActivity.class
        );

        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pi = PendingIntent.getActivity(
                context,
                9001,
                launch,
                PendingIntent.FLAG_UPDATE_CURRENT |
                        PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder b =
                new NotificationCompat.Builder(
                        context,
                        CHANNEL_ID
                )
                .setSmallIcon(
                        android.R.drawable.ic_dialog_info
                )
                .setColor(0xFF6C4DF6)
                .setContentTitle(
                        "New Fashion Tailoring — Delivery Reminder"
                )
                .setContentText(body)
                .setStyle(
                        new NotificationCompat.BigTextStyle()
                                .bigText(body)
                )
                .setPriority(
                        NotificationCompat.PRIORITY_HIGH
                )
                .setAutoCancel(true)
                .setContentIntent(pi);

        NotificationManager nm =
                (NotificationManager)
                        context.getSystemService(
                                Context.NOTIFICATION_SERVICE
                        );

        if (nm != null) {
            nm.notify(
                    12000 +
                    (int)(System.currentTimeMillis() % 1000),
                    b.build()
            );
        }
    }

    private String buildBody(String payload) {
        try {
            JSONObject root = new JSONObject(payload);
            JSONArray orders = root.optJSONArray("orders");

            if (orders == null ||
                    orders.length() == 0) {
                return "Pending delivery orders இல்லை.";
            }

            StringBuilder sb = new StringBuilder();

            int n = Math.min(5, orders.length());

            for (int i = 0; i < n; i++) {
                JSONObject o = orders.getJSONObject(i);

                if (sb.length() > 0) {
                    sb.append("\n");
                }

                sb.append("📅 ")
                        .append(
                                o.optString(
                                        "deliveryDate",
                                        ""
                                )
                        )
                        .append(" · 👤 ")
                        .append(
                                o.optString(
                                        "customer",
                                        "Customer"
                                )
                        )
                        .append(" · ")
                        .append(
                                o.optString(
                                        "status",
                                        "Pending"
                                )
                        );
            }

            if (orders.length() > n) {
                sb.append("\n+")
                        .append(orders.length() - n)
                        .append(" more orders");
            }

            return sb.toString();

        } catch (Exception e) {
            return "Delivery reminder — app-ல் pending orders பார்க்கவும்.";
        }
    }

    private void createChannel(Context context) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch =
                    new NotificationChannel(
                            CHANNEL_ID,
                            "Delivery Reminders",
                            NotificationManager.IMPORTANCE_HIGH
                    );

            ch.setDescription(
                    "New Fashion Tailoring daily delivery reminders"
            );

            NotificationManager nm =
                    (NotificationManager)
                            context.getSystemService(
                                    Context.NOTIFICATION_SERVICE
                            );

            if (nm != null) {
                nm.createNotificationChannel(ch);
            }
        }
    }
}
