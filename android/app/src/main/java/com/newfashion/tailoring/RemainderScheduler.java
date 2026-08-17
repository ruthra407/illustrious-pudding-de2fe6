package com.newfashion.tailoring;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import java.util.Calendar;
import org.json.JSONArray;

public final class ReminderScheduler {
    private ReminderScheduler() {}

    public static void scheduleAll(Context context, JSONArray times) {
        if (times == null) return;

        AlarmManager am = (AlarmManager)
                context.getSystemService(Context.ALARM_SERVICE);

        for (int i = 0; i < times.length(); i++) {
            String t = times.optString(i, "");
            int[] hm = parse(t);
            if (hm == null) continue;

            scheduleOne(context, am, i, hm[0], hm[1]);
        }
    }

    public static void scheduleNext(
            Context context,
            int slot,
            int hour,
            int minute) {

        AlarmManager am = (AlarmManager)
                context.getSystemService(Context.ALARM_SERVICE);

        scheduleOne(context, am, slot, hour, minute);
    }

    private static void scheduleOne(
            Context context,
            AlarmManager am,
            int slot,
            int hour,
            int minute) {

        Calendar now = Calendar.getInstance();
        Calendar next = Calendar.getInstance();

        next.set(Calendar.HOUR_OF_DAY, hour);
        next.set(Calendar.MINUTE, minute);
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);

        if (!next.after(now)) {
            next.add(Calendar.DAY_OF_YEAR, 1);
        }

        Intent i = new Intent(context, ReminderReceiver.class);
        i.putExtra("slot", slot);
        i.putExtra("hour", hour);
        i.putExtra("minute", minute);

        PendingIntent pi = PendingIntent.getBroadcast(
                context,
                7000 + slot,
                i,
                PendingIntent.FLAG_UPDATE_CURRENT |
                        PendingIntent.FLAG_IMMUTABLE
        );

        long trigger = next.getTimeInMillis();

        if (android.os.Build.VERSION.SDK_INT >= 31 &&
                !am.canScheduleExactAlarms()) {

            am.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    trigger,
                    pi
            );

        } else if (android.os.Build.VERSION.SDK_INT >= 23) {

            am.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    trigger,
                    pi
            );

        } else {

            am.setExact(
                    AlarmManager.RTC_WAKEUP,
                    trigger,
                    pi
            );
        }
    }

    public static void cancelAll(Context context) {
        AlarmManager am = (AlarmManager)
                context.getSystemService(Context.ALARM_SERVICE);

        for (int slot = 0; slot < 10; slot++) {
            Intent i = new Intent(context, ReminderReceiver.class);

            PendingIntent pi = PendingIntent.getBroadcast(
                    context,
                    7000 + slot,
                    i,
                    PendingIntent.FLAG_NO_CREATE |
                            PendingIntent.FLAG_IMMUTABLE
            );

            if (pi != null) {
                am.cancel(pi);
                pi.cancel();
            }
        }
    }

    private static int[] parse(String s) {
        try {
            String[] p = s.split(":");

            if (p.length != 2) return null;

            int hour = Integer.parseInt(p[0]);
            int minute = Integer.parseInt(p[1]);

            if (hour < 0 || hour > 23 ||
                    minute < 0 || minute > 59) {
                return null;
            }

            return new int[]{hour, minute};

        } catch (Exception e) {
            return null;
        }
    }
}
