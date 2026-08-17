package com.newfashion.tailoring;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class ReminderScheduler {

    private static final String PREFS_NAME = "reminder_scheduler";
    private static final String REMINDERS_KEY = "reminders";

    private ReminderScheduler() {
        // Utility class
    }

    public static void scheduleReminder(
            Context context,
            int requestCode,
            long triggerAtMillis,
            String title,
            String message
    ) {
        Context appContext = context.getApplicationContext();

        saveReminder(
                appContext,
                requestCode,
                triggerAtMillis,
                title,
                message
        );

        scheduleAlarm(
                appContext,
                requestCode,
                triggerAtMillis,
                title,
                message
        );
    }

    private static void scheduleAlarm(
            Context context,
            int requestCode,
            long triggerAtMillis,
            String title,
            String message
    ) {
        AlarmManager alarmManager =
                (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        if (alarmManager == null) {
            return;
        }

        Intent intent = new Intent(
                context,
                ReminderReceiver.class
        );

        /*
         * ReminderReceiver reads notification_id,
         * so use the same key here.
         */
        intent.putExtra("notification_id", requestCode);
        intent.putExtra("requestCode", requestCode);
        intent.putExtra("title", title);
        intent.putExtra("message", message);

        PendingIntent pendingIntent =
                PendingIntent.getBroadcast(
                        context,
                        requestCode,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT
                                | PendingIntent.FLAG_IMMUTABLE
                );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

            alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
            );

        } else {

            alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
            );
        }
    }

    public static void cancelReminder(
            Context context,
            int requestCode
    ) {
        Context appContext = context.getApplicationContext();

        AlarmManager alarmManager =
                (AlarmManager) appContext.getSystemService(
                        Context.ALARM_SERVICE
                );

        if (alarmManager != null) {

            Intent intent = new Intent(
                    appContext,
                    ReminderReceiver.class
            );

            PendingIntent pendingIntent =
                    PendingIntent.getBroadcast(
                            appContext,
                            requestCode,
                            intent,
                            PendingIntent.FLAG_UPDATE_CURRENT
                                    | PendingIntent.FLAG_IMMUTABLE
                    );

            alarmManager.cancel(pendingIntent);
            pendingIntent.cancel();
        }

        removeReminder(
                appContext,
                requestCode
        );
    }

    public static void rescheduleAll(Context context) {

        Context appContext = context.getApplicationContext();

        List<ReminderData> reminders =
                loadReminders(appContext);

        long currentTime = System.currentTimeMillis();

        for (ReminderData reminder : reminders) {

            /*
             * Only restore reminders that are still in the future.
             */
            if (reminder.triggerAtMillis > currentTime) {

                scheduleAlarm(
                        appContext,
                        reminder.requestCode,
                        reminder.triggerAtMillis,
                        reminder.title,
                        reminder.message
                );
            }
        }
    }

    private static void saveReminder(
            Context context,
            int requestCode,
            long triggerAtMillis,
            String title,
            String message
    ) {

        SharedPreferences preferences =
                context.getSharedPreferences(
                        PREFS_NAME,
                        Context.MODE_PRIVATE
                );

        JSONArray oldArray;

        try {
            oldArray = new JSONArray(
                    preferences.getString(
                            REMINDERS_KEY,
                            "[]"
                    )
            );
        } catch (Exception e) {
            oldArray = new JSONArray();
        }

        JSONArray newArray = new JSONArray();

        try {

            for (int i = 0; i < oldArray.length(); i++) {

                JSONObject object =
                        oldArray.getJSONObject(i);

                if (object.getInt("requestCode") != requestCode) {
                    newArray.put(object);
                }
            }

            JSONObject reminder = new JSONObject();

            reminder.put("requestCode", requestCode);
            reminder.put("triggerAtMillis", triggerAtMillis);
            reminder.put(
                    "title",
                    title == null ? "" : title
            );
            reminder.put(
                    "message",
                    message == null ? "" : message
            );

            newArray.put(reminder);

        } catch (Exception e) {
            return;
        }

        preferences.edit()
                .putString(
                        REMINDERS_KEY,
                        newArray.toString()
                )
                .apply();
    }

    private static void removeReminder(
            Context context,
            int requestCode
    ) {

        SharedPreferences preferences =
                context.getSharedPreferences(
                        PREFS_NAME,
                        Context.MODE_PRIVATE
                );

        JSONArray oldArray;

        try {
            oldArray = new JSONArray(
                    preferences.getString(
                            REMINDERS_KEY,
                            "[]"
                    )
            );
        } catch (Exception e) {
            return;
        }

        JSONArray newArray = new JSONArray();

        try {

            for (int i = 0; i < oldArray.length(); i++) {

                JSONObject object =
                        oldArray.getJSONObject(i);

                if (object.getInt("requestCode") != requestCode) {
                    newArray.put(object);
                }
            }

        } catch (Exception e) {
            return;
        }

        preferences.edit()
                .putString(
                        REMINDERS_KEY,
                        newArray.toString()
                )
                .apply();
    }

    private static List<ReminderData> loadReminders(
            Context context
    ) {

        List<ReminderData> reminders =
                new ArrayList<>();

        SharedPreferences preferences =
                context.getSharedPreferences(
                        PREFS_NAME,
                        Context.MODE_PRIVATE
                );

        String saved =
                preferences.getString(
                        REMINDERS_KEY,
                        "[]"
                );

        try {

            JSONArray array =
                    new JSONArray(saved);

            for (int i = 0; i < array.length(); i++) {

                JSONObject object =
                        array.getJSONObject(i);

                ReminderData reminder =
                        new ReminderData(
                                object.getInt("requestCode"),
                                object.getLong("triggerAtMillis"),
                                object.optString("title", ""),
                                object.optString("message", "")
                        );

                reminders.add(reminder);
            }

        } catch (Exception ignored) {
            // Ignore invalid saved reminder data.
        }

        return reminders;
    }

    private static final class ReminderData {

        final int requestCode;
        final long triggerAtMillis;
        final String title;
        final String message;

        ReminderData(
                int requestCode,
                long triggerAtMillis,
                String title,
                String message
        ) {
            this.requestCode = requestCode;
            this.triggerAtMillis = triggerAtMillis;
            this.title = title;
            this.message = message;
        }
    }
}
