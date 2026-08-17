package com.newfashion.tailoring;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.json.JSONObject;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(
            Context context,
            Intent intent) {

        if (!context.getSharedPreferences(
                "nf_reminders",
                Context.MODE_PRIVATE
        ).getBoolean("enabled", false)) {
            return;
        }

        String payload =
                context.getSharedPreferences(
                        "nf_reminders",
                        Context.MODE_PRIVATE
                ).getString("payload", "");

        try {
            JSONObject root = new JSONObject(payload);

            ReminderScheduler.scheduleAll(
                    context,
                    root.optJSONArray("times")
            );

        } catch (Exception ignored) {
        }
    }
}
