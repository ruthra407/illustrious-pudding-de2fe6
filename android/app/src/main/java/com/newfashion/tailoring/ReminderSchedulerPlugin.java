package com.newfashion.tailoring;

import android.app.AlarmManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import org.json.JSONArray;

@CapacitorPlugin(name="ReminderScheduler")
public class ReminderSchedulerPlugin extends Plugin {
    private static final int FIRST_REMINDER_ID=8001;

    @PluginMethod public void requestPermissions(PluginCall call){
        JSObject r=new JSObject();r.put("display","granted");call.resolve(r);
    }

    @PluginMethod public void checkExactAlarmPermission(PluginCall call){
        boolean granted=ReminderScheduler.canScheduleExactAlarms(getContext());
        JSObject r=new JSObject();r.put("granted",granted);r.put("exact_alarm",granted?"granted":"denied");call.resolve(r);
    }

    @PluginMethod public void requestExactAlarmPermission(PluginCall call){
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.S){
            try{
                Intent i=new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:"+getContext().getPackageName()));
                getContext().startActivity(i);
            }catch(Exception ignored){}
        }
        call.resolve();
    }

    @PluginMethod public void scheduleDailyReminders(PluginCall call){
        try{
            JSONArray times=call.getArray("times");
            String body=call.getString("body","");
            String title=call.getString("title","🔔 New Fashion Tailoring");
            if(times==null||times.length()!=24){call.reject("Exactly 24 reminder times are required.");return;}
            if(!ReminderScheduler.canScheduleExactAlarms(getContext())){call.reject("Exact alarm permission is not granted.");return;}
            cancelAll();
            for(int i=0;i<times.length();i++){
                ReminderScheduler.scheduleDailyReminder(getContext(),FIRST_REMINDER_ID+i,times.getString(i),title,body);
            }
            JSObject r=new JSObject();r.put("success",true);r.put("count",24);call.resolve(r);
        }catch(Exception e){call.reject("Could not schedule reminders: "+e.getMessage(),e);}
    }

    @PluginMethod public void cancelDailyReminders(PluginCall call){cancelAll();call.resolve();}

    private void cancelAll(){for(int i=0;i<24;i++)ReminderScheduler.cancelReminder(getContext(),FIRST_REMINDER_ID+i);}
}
