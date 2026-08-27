package com.newfashion.tailoring;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ReminderVoiceService extends Service {
    private static final String TAG="ReminderVoiceService";
    private static final String CHANNEL_ID="reminder_voice_service_v2";
    private static final int SERVICE_NOTIFICATION_ID=91001;

    private ExecutorService executor;
    private TextToSpeech tts;
    private AudioManager audioManager;
    private AudioFocusRequest audioFocusRequest;
    private volatile boolean ttsReady=false;
    private int currentStartId=0;

    @Override public void onCreate(){
        super.onCreate();
        executor=Executors.newSingleThreadExecutor();
        audioManager=(AudioManager)getSystemService(Context.AUDIO_SERVICE);
        createChannel();
        startAsForeground("🔊 நினைவூட்டல் குரல்","குரல் தயாராகிறது...");

        tts=new TextToSpeech(getApplicationContext(),status->{
            if(status==TextToSpeech.SUCCESS){
                int lang=tts.setLanguage(new Locale("ta","IN"));
                ttsReady=lang!=TextToSpeech.LANG_MISSING_DATA && lang!=TextToSpeech.LANG_NOT_SUPPORTED;
                if(!ttsReady){
                    int fallback=tts.setLanguage(new Locale("ta"));
                    ttsReady=fallback!=TextToSpeech.LANG_MISSING_DATA && fallback!=TextToSpeech.LANG_NOT_SUPPORTED;
                }
                if(ttsReady){
                    tts.setSpeechRate(0.92f);
                    tts.setPitch(1.0f);
                    selectTamilFemaleVoice();
                    Log.d(TAG,"Android Tamil TTS ready");
                }else Log.e(TAG,"Tamil TTS language unavailable");
            }else Log.e(TAG,"TextToSpeech initialization failed: "+status);
        });

        if(tts!=null){
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){
                @Override public void onStart(String utteranceId){Log.d(TAG,"TTS PLAYBACK STARTED at="+System.currentTimeMillis());}
                @Override public void onDone(String utteranceId){Log.d(TAG,"TTS PLAYBACK FINISHED at="+System.currentTimeMillis());stopForegroundService(currentStartId);}
                @Override public void onError(String utteranceId){Log.e(TAG,"TTS playback error");stopForegroundService(currentStartId);}
                @Override public void onError(String utteranceId,int errorCode){Log.e(TAG,"TTS playback error code="+errorCode);stopForegroundService(currentStartId);}
            });
        }
    }

    private void selectTamilFemaleVoice(){
        if(tts==null)return;
        try{
            android.speech.tts.Voice best=null;
            for(android.speech.tts.Voice v:tts.getVoices()){
                if(v==null||v.getLocale()==null)continue;
                String lang=v.getLocale().getLanguage();
                String name=(v.getName()==null?"":v.getName()).toLowerCase(Locale.ROOT);
                if("ta".equalsIgnoreCase(lang)){
                    if(name.contains("female")||name.contains("woman")||name.contains("girl")||name.contains("kalpana")||name.contains("veena")||name.contains("lekha")||name.contains("heera")){best=v;break;}
                    if(best==null)best=v;
                }
            }
            if(best!=null)tts.setVoice(best);
        }catch(Exception e){Log.w(TAG,"Could not select Tamil voice",e);}
    }

    @Override public int onStartCommand(Intent intent,int flags,int startId){
        currentStartId=startId;
        if(intent==null){stopForegroundService(startId);return START_NOT_STICKY;}
        final String message=prepareTtsText(intent.getStringExtra("message"));
        if(message.trim().isEmpty()){stopForegroundService(startId);return START_NOT_STICKY;}
        Log.d(TAG,"Voice request received at="+System.currentTimeMillis());
        executor.execute(()->speakWhenReady(message,startId));
        return START_NOT_STICKY;
    }

    private void speakWhenReady(String text,int startId){
        long deadline=System.currentTimeMillis()+3000;
        while(!ttsReady && System.currentTimeMillis()<deadline){
            try{Thread.sleep(20);}catch(InterruptedException ignored){Thread.currentThread().interrupt();break;}
        }
        if(!ttsReady||tts==null){Log.e(TAG,"Tamil TTS not ready");stopForegroundService(startId);return;}
        requestAudioFocus();
        Bundle params=new Bundle();
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME,1.0f);
        params.putString(TextToSpeech.Engine.KEY_PARAM_STREAM,Integer.toString(AudioManager.STREAM_ALARM));
        String utteranceId="reminder_"+System.currentTimeMillis();
        int result=tts.speak(text,TextToSpeech.QUEUE_FLUSH,params,utteranceId);
        Log.d(TAG,"TTS speak() result="+result+" at="+System.currentTimeMillis());
        if(result==TextToSpeech.ERROR)stopForegroundService(startId);
    }

    private void requestAudioFocus(){
        if(audioManager==null)return;
        try{
            AudioAttributes attrs=new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
            if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O){
                audioFocusRequest=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(attrs).setAcceptsDelayedFocusGain(false).build();
                audioManager.requestAudioFocus(audioFocusRequest);
            }else audioManager.requestAudioFocus(null,AudioManager.STREAM_ALARM,AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
        }catch(Exception e){Log.w(TAG,"Audio focus request failed",e);}
    }

    private void abandonAudioFocus(){
        if(audioManager==null)return;
        try{
            if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O && audioFocusRequest!=null){audioManager.abandonAudioFocusRequest(audioFocusRequest);audioFocusRequest=null;}
            else if(Build.VERSION.SDK_INT<Build.VERSION_CODES.O)audioManager.abandonAudioFocus(null);
        }catch(Exception ignored){}
    }

    private String prepareTtsText(String value){
        String s=value==null?"":value;
        s=s.replaceAll("(?i)\\bBlouse\\b","ப்ளவுஸ்");
        s=s.replaceAll("(?i)\\bChudi\\b","சுடிதார்");
        s=s.replaceAll("(?i)\\bSaree\\b","சாரி");
        s=s.replaceAll("(?i)\\bShirt\\b","சர்ட்");
        s=s.replaceAll("(?i)\\bcustomer\\b","கஸ்டமர்");
        s=s.replaceAll("(?i)\\bNagaraj\\b","நாகராஜ்");
        s=s.replaceAll("(?i)\\bBritannia\\b","பிரிட்டானியா");
        return convertNumbersToTamil(s);
    }

    private String convertNumbersToTamil(String text){
        String[] n={"","ஒரு","இரண்டு","மூன்று","நான்கு","ஐந்து","ஆறு","ஏழு","எட்டு","ஒன்பது","பத்து","பதினொன்று","பன்னிரண்டு","பதின்மூன்று","பதினான்கு","பதினைந்து","பதினாறு","பதினேழு","பதினெட்டு","பத்தொன்பது","இருபது"};
        for(int i=20;i>=1;i--)text=text.replaceAll("(?<!\\d)"+i+"\\s*நாள்",n[i]+" நாள்");
        return text;
    }

    private void createChannel(){
        if(Build.VERSION.SDK_INT<Build.VERSION_CODES.O)return;
        NotificationManager m=getSystemService(NotificationManager.class);if(m==null)return;
        NotificationChannel c=new NotificationChannel(CHANNEL_ID,"Reminder Voice",NotificationManager.IMPORTANCE_LOW);
        c.setSound(null,null);c.setDescription("Android Tamil voice reminder playback");m.createNotificationChannel(c);
    }

    private Notification createNotification(String title,String text){
        Intent open=new Intent(this,MainActivity.class);
        PendingIntent pi=PendingIntent.getActivity(this,91002,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this,CHANNEL_ID).setSmallIcon(android.R.drawable.ic_lock_silent_mode_off).setContentTitle(title).setContentText(text).setPriority(NotificationCompat.PRIORITY_LOW).setOngoing(true).setContentIntent(pi).build();
    }

    private void startAsForeground(String title,String text){
        try{
            Notification n=createNotification(title,text);
            if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.Q)ServiceCompat.startForeground(this,SERVICE_NOTIFICATION_ID,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            else startForeground(SERVICE_NOTIFICATION_ID,n);
        }catch(Exception e){Log.e(TAG,"Foreground service start failed",e);stopSelf();}
    }

    private void stopForegroundService(int startId){
        abandonAudioFocus();
        try{if(tts!=null)tts.stop();}catch(Exception ignored){}
        try{stopForeground(true);}catch(Exception ignored){}
        stopSelf(startId);
    }

    @Override public void onDestroy(){
        try{if(tts!=null){tts.stop();tts.shutdown();}}catch(Exception ignored){}
        abandonAudioFocus();
        if(executor!=null)executor.shutdownNow();
        super.onDestroy();
    }

    @Nullable @Override public IBinder onBind(Intent intent){return null;}
}
