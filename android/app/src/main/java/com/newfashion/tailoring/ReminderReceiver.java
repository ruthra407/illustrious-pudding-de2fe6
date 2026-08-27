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
    private static final String TAG="ReminderReceiver";
    private static final String CHANNEL_ID="reminder_voice_channel_v3";
    private static final int DEFAULT_NOTIFICATION_ID=1001;

    @Override public void onReceive(Context context,Intent intent){
        Context app=context.getApplicationContext();
        String title=intent.getStringExtra("title");
        String message=intent.getStringExtra("message");
        if(title==null||title.trim().isEmpty())title="நினைவூட்டல்";
        if(message==null||message.trim().isEmpty())message="உங்களுக்கு ஒரு நினைவூட்டல் உள்ளது.";
        final String finalTitle=title, finalMessage=message;
        int id=intent.getIntExtra("requestCode",intent.getIntExtra("notification_id",DEFAULT_NOTIFICATION_ID));

        long receivedAt=System.currentTimeMillis();
        Log.d(TAG,"REMINDER RECEIVED id="+id+" at="+receivedAt);

        createNotificationChannel(app);
        Intent open=new Intent(app,MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi=PendingIntent.getActivity(app,id,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder b=new NotificationCompat.Builder(app,CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(finalTitle).setContentText(finalMessage)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(finalMessage))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true).setContentIntent(pi).setSilent(true);
        NotificationManager nm=(NotificationManager)app.getSystemService(Context.NOTIFICATION_SERVICE);
        if(nm!=null && (Build.VERSION.SDK_INT<Build.VERSION_CODES.TIRAMISU || ContextCompat.checkSelfPermission(app,Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED)){
            nm.notify(id,b.build());
        }

        /* Schedule tomorrow before starting voice so daily reminders never stop after day one. */
        try{ReminderScheduler.rescheduleNextDailyReminder(app,id);}catch(Exception e){Log.e(TAG,"Next daily reminder reschedule failed",e);}

        Intent voice=new Intent(app,ReminderVoiceService.class);
        voice.putExtra("title",finalTitle);voice.putExtra("message",finalMessage);voice.putExtra("requestCode",id);
        try{
            if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O)ContextCompat.startForegroundService(app,voice);else app.startService(voice);
            Log.d(TAG,"ReminderVoiceService START COMMAND SENT at="+System.currentTimeMillis());
        }catch(Exception e){Log.e(TAG,"ReminderVoiceService start failed",e);}
    }

    private void createNotificationChannel(Context c){
        if(Build.VERSION.SDK_INT<Build.VERSION_CODES.O)return;
        NotificationManager m=c.getSystemService(NotificationManager.class);if(m==null)return;
        NotificationChannel ch=new NotificationChannel(CHANNEL_ID,"தமிழ் நினைவூட்டல்கள்",NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("Tailoring reminder notifications with Android Tamil voice");ch.setSound(null,null);m.createNotificationChannel(ch);
    }
}
