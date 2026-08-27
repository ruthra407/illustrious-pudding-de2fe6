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
import java.util.Calendar;
import java.util.List;

public final class ReminderScheduler {
    private static final String PREFS_NAME="reminder_scheduler";
    private static final String REMINDERS_KEY="reminders";
    private ReminderScheduler() {}

    public static void scheduleReminder(Context context,int requestCode,long triggerAtMillis,String title,String message){
        Context app=context.getApplicationContext();
        saveReminder(app,requestCode,triggerAtMillis,title,message);
        scheduleAlarm(app,requestCode,triggerAtMillis,title,message);
    }

    public static void scheduleDailyReminder(Context context,int requestCode,String time,String title,String message){
        if(time==null || !time.matches("^([01]\\d|2[0-3]):[0-5]\\d$")) return;
        try{
            String[] parts=time.split(":");
            Calendar c=Calendar.getInstance();
            c.set(Calendar.HOUR_OF_DAY,Integer.parseInt(parts[0]));
            c.set(Calendar.MINUTE,Integer.parseInt(parts[1]));
            c.set(Calendar.SECOND,0);
            c.set(Calendar.MILLISECOND,0);
            if(c.getTimeInMillis()<=System.currentTimeMillis()) c.add(Calendar.DAY_OF_YEAR,1);
            scheduleReminder(context,requestCode,c.getTimeInMillis(),title,message);
        }catch(Exception ignored){}
    }

    private static void scheduleAlarm(Context context,int requestCode,long triggerAtMillis,String title,String message){
        AlarmManager am=(AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        if(am==null)return;
        Intent intent=new Intent(context,ReminderReceiver.class);
        intent.putExtra("notification_id",requestCode);
        intent.putExtra("requestCode",requestCode);
        intent.putExtra("title",title);
        intent.putExtra("message",message);
        PendingIntent pi=PendingIntent.getBroadcast(context,requestCode,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);

        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.S){
            if(!am.canScheduleExactAlarms()) throw new IllegalStateException("Exact alarm permission is not granted");
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,triggerAtMillis,pi);
        }else if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.M){
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,triggerAtMillis,pi);
        }else{
            am.setExact(AlarmManager.RTC_WAKEUP,triggerAtMillis,pi);
        }
    }

    public static boolean canScheduleExactAlarms(Context context){
        if(Build.VERSION.SDK_INT<Build.VERSION_CODES.S)return true;
        AlarmManager am=(AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        return am!=null && am.canScheduleExactAlarms();
    }

    public static void rescheduleNextDailyReminder(Context context,int requestCode){
        Context app=context.getApplicationContext();
        ReminderData r=findReminder(app,requestCode);
        if(r==null)return;
        Calendar next=Calendar.getInstance();
        next.setTimeInMillis(r.triggerAtMillis);
        while(next.getTimeInMillis()<=System.currentTimeMillis()) next.add(Calendar.DAY_OF_YEAR,1);
        saveReminder(app,r.requestCode,next.getTimeInMillis(),r.title,r.message);
        scheduleAlarm(app,r.requestCode,next.getTimeInMillis(),r.title,r.message);
    }

    public static void cancelReminder(Context context,int requestCode){
        Context app=context.getApplicationContext();
        AlarmManager am=(AlarmManager)app.getSystemService(Context.ALARM_SERVICE);
        if(am!=null){
            Intent i=new Intent(app,ReminderReceiver.class);
            PendingIntent pi=PendingIntent.getBroadcast(app,requestCode,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            am.cancel(pi); pi.cancel();
        }
        removeReminder(app,requestCode);
    }

    public static void rescheduleAll(Context context){
        Context app=context.getApplicationContext();
        for(ReminderData r:loadReminders(app)){
            if(r.triggerAtMillis>System.currentTimeMillis()) scheduleAlarm(app,r.requestCode,r.triggerAtMillis,r.title,r.message);
        }
    }

    private static ReminderData findReminder(Context c,int id){
        for(ReminderData r:loadReminders(c)) if(r.requestCode==id)return r;
        return null;
    }

    private static void saveReminder(Context context,int id,long trigger,String title,String message){
        SharedPreferences p=context.getSharedPreferences(PREFS_NAME,Context.MODE_PRIVATE);
        JSONArray old;
        try{old=new JSONArray(p.getString(REMINDERS_KEY,"[]"));}catch(Exception e){old=new JSONArray();}
        JSONArray out=new JSONArray();
        try{
            for(int i=0;i<old.length();i++){JSONObject o=old.getJSONObject(i);if(o.getInt("requestCode")!=id)out.put(o);}
            JSONObject o=new JSONObject();o.put("requestCode",id);o.put("triggerAtMillis",trigger);o.put("title",title==null?"":title);o.put("message",message==null?"":message);out.put(o);
            p.edit().putString(REMINDERS_KEY,out.toString()).apply();
        }catch(Exception ignored){}
    }

    private static void removeReminder(Context context,int id){
        SharedPreferences p=context.getSharedPreferences(PREFS_NAME,Context.MODE_PRIVATE);
        JSONArray old;try{old=new JSONArray(p.getString(REMINDERS_KEY,"[]"));}catch(Exception e){return;}
        JSONArray out=new JSONArray();
        try{for(int i=0;i<old.length();i++){JSONObject o=old.getJSONObject(i);if(o.getInt("requestCode")!=id)out.put(o);}}catch(Exception ignored){}
        p.edit().putString(REMINDERS_KEY,out.toString()).apply();
    }

    private static List<ReminderData> loadReminders(Context context){
        List<ReminderData> list=new ArrayList<>();
        SharedPreferences p=context.getSharedPreferences(PREFS_NAME,Context.MODE_PRIVATE);
        try{
            JSONArray a=new JSONArray(p.getString(REMINDERS_KEY,"[]"));
            for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);list.add(new ReminderData(o.getInt("requestCode"),o.getLong("triggerAtMillis"),o.optString("title",""),o.optString("message","")));}
        }catch(Exception ignored){}
        return list;
    }

    private static final class ReminderData{
        final int requestCode;final long triggerAtMillis;final String title;final String message;
        ReminderData(int r,long t,String ti,String m){requestCode=r;triggerAtMillis=t;title=ti;message=m;}
    }
}
