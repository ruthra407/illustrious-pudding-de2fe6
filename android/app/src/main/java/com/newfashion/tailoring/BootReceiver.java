package com.newfashion.tailoring;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {

        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {

            /*
             * Device has restarted.
             *
             * Reminder rescheduling can be triggered here.
             * The actual saved-reminder restoration must be connected
             * to the reminder storage used by the application.
             */
        }
    }
}
