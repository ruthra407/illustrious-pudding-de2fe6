package com.newfashion.tailoring;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.speech.tts.TextToSpeech;
import java.util.Locale;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;


public class ReminderVoiceService extends Service {

    private static final String TAG = "ReminderVoiceService";

    private static final String SERVICE_CHANNEL_ID =
            "reminder_voice_service_v2";

    private static final int SERVICE_NOTIFICATION_ID = 91001;

    private TextToSpeech textToSpeech;
    private boolean ttsReady = false;

    @Override
    public void onCreate() {
        super.onCreate();

        Log.d(TAG, "========================================");
        Log.d(TAG, "ReminderVoiceService ON CREATE");
        Log.d(TAG, "========================================");

        createServiceNotificationChannel();

        textToSpeech = new TextToSpeech(
                getApplicationContext(),
                status -> {
                    if (status == TextToSpeech.SUCCESS) {
                        int result = textToSpeech.setLanguage(new Locale("ta", "IN"));
                        ttsReady = result != TextToSpeech.LANG_MISSING_DATA
                                && result != TextToSpeech.LANG_NOT_SUPPORTED;
                        textToSpeech.setSpeechRate(0.92f);
                        textToSpeech.setPitch(1.0f);
                        Log.d(TAG, "Android Tamil TTS ready = " + ttsReady);
                    } else {
                        ttsReady = false;
                        Log.e(TAG, "Android TTS initialization failed");
                    }
                }
        );

        Notification notification =
                createServiceNotification(
                        "🔊 நினைவூட்டல்",
                        "குரல் நினைவூட்டல் தயாராகிறது..."
                );

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                        this,
                        SERVICE_NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                );
            } else {
                startForeground(
                        SERVICE_NOTIFICATION_ID,
                        notification
                );
            }

            Log.d(TAG, "FOREGROUND SERVICE STARTED");

        } catch (Exception e) {
            Log.e(TAG, "FOREGROUND SERVICE START FAILED", e);
            stopSelf();
        }
    }

    @Override
    public int onStartCommand(
            Intent intent,
            int flags,
            int startId
    ) {
        Log.d(TAG, "========================================");
        Log.d(TAG, "ReminderVoiceService ON START COMMAND");
        Log.d(TAG, "startId = " + startId);
        Log.d(TAG, "========================================");

        if (intent == null) {
            Log.e(TAG, "Intent is NULL");
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        String title = intent.getStringExtra("title");
        String message = intent.getStringExtra("message");

        int requestCode = intent.getIntExtra(
                "requestCode",
                -1
        );

        Log.d(TAG, "requestCode = " + requestCode);
        Log.d(TAG, "title = " + title);
        Log.d(TAG, "message = " + message);

        if (message == null || message.trim().isEmpty()) {
            Log.e(TAG, "TTS message is EMPTY");
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        speakWithAndroidTts(message, startId);

        return START_NOT_STICKY;
    }

    private void speakWithAndroidTts(
            String message,
            int startId
    ) {
        try {
            if (!ttsReady || textToSpeech == null) {
                updateServiceNotification(
                        "❌ குரல் வரவில்லை",
                        "Android Tamil TTS தயாராக இல்லை."
                );
                stopSelf(startId);
                return;
            }

            String speechText = prepareTtsText(message);

            if (speechText.trim().isEmpty()) {
                stopSelf(startId);
                return;
            }

            updateServiceNotification(
                    "🔊 குரல் பேசுகிறது",
                    "Android தமிழ் குரல் நினைவூட்டலை வாசிக்கிறது..."
            );

            textToSpeech.stop();

            int result = textToSpeech.speak(
                    speechText,
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "reminder_" + startId
            );

            Log.d(TAG, "Android TTS speak result = " + result);

            if (result == TextToSpeech.ERROR) {
                updateServiceNotification(
                        "❌ குரல் வரவில்லை",
                        "Android Tamil TTS speak error."
                );
                stopSelf(startId);
            }

        } catch (Exception e) {
            Log.e(TAG, "ANDROID TTS FAILED", e);
            updateServiceNotification(
                    "❌ குரல் வரவில்லை",
                    getSafeErrorMessage(e)
            );
            stopSelf(startId);
        }
    }

    private String prepareTtsText(String value) {
        String s = value == null ? "" : value;

        s = s.replaceAll("(?i)\\bBlouse\\b", "ப்ளவுஸ்");
        s = s.replaceAll("(?i)\\bChudi\\b", "சுடிதார்");
        s = s.replaceAll("(?i)\\bSaree\\b", "சாரி");
        s = s.replaceAll("(?i)\\bShirt\\b", "சர்ட்");
        s = s.replaceAll("(?i)\\bcustomer\\b", "கஸ்டமர்");

        return convertNumbersToTamil(s);
    }

    private String convertNumbersToTamil(String text) {
        String[] numbers = {
                "", "ஒரு", "இரண்டு", "மூன்று", "நான்கு",
                "ஐந்து", "ஆறு", "ஏழு", "எட்டு", "ஒன்பது",
                "பத்து", "பதினொன்று", "பன்னிரண்டு", "பதின்மூன்று",
                "பதினான்கு", "பதினைந்து", "பதினாறு", "பதினேழு",
                "பதினெட்டு", "பத்தொன்பது", "இருபது"
        };

        for (int i = 20; i >= 1; i--) {
            text = text.replaceAll(
                    "(?<!\\d)" + i + "\\s*நாள்",
                    numbers[i] + " நாள்"
            );
        }

        return text;
    }

    private void createServiceNotificationChannel() {

        if (Build.VERSION.SDK_INT <
                Build.VERSION_CODES.O) {
            return;
        }

        NotificationManager manager =
                getSystemService(
                        NotificationManager.class
                );

        if (manager == null) {
            return;
        }

        NotificationChannel channel =
                new NotificationChannel(
                        SERVICE_CHANNEL_ID,
                        "Reminder Voice",
                        NotificationManager.IMPORTANCE_LOW
                );

        channel.setDescription(
                "Tamil voice reminder playback"
        );

        channel.setSound(null, null);

        manager.createNotificationChannel(channel);
    }

    private Notification createServiceNotification(
            String title,
            String text
    ) {
        Intent openIntent =
                new Intent(
                        this,
                        MainActivity.class
                );

        PendingIntent pendingIntent =
                PendingIntent.getActivity(
                        this,
                        91002,
                        openIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT |
                                PendingIntent.FLAG_IMMUTABLE
                );

        return new NotificationCompat.Builder(
                this,
                SERVICE_CHANNEL_ID
        )
                .setSmallIcon(
                        android.R.drawable
                                .ic_lock_silent_mode_off
                )
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(
                        NotificationCompat.PRIORITY_LOW
                )
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .build();
    }

    private void updateServiceNotification(
            String title,
            String text
    ) {
        try {
            NotificationManager manager =
                    (NotificationManager)
                            getSystemService(
                                    Context.NOTIFICATION_SERVICE
                            );

            if (manager != null) {
                manager.notify(
                        SERVICE_NOTIFICATION_ID,
                        createServiceNotification(
                                title,
                                text
                        )
                );
            }

        } catch (Exception e) {
            Log.e(
                    TAG,
                    "Could not update service notification",
                    e
            );
        }
    }

    private String getSafeErrorMessage(
            Exception error
    ) {
        String message = error.getMessage();

        if (message == null ||
                message.trim().isEmpty()) {

            return error
                    .getClass()
                    .getSimpleName();
        }

        return message.length() > 180
                ? message.substring(0, 180)
                : message;
    }

    @Override
    public void onDestroy() {

        Log.d(
                TAG,
                "ReminderVoiceService ON DESTROY"
        );

        try {
            if (textToSpeech != null) {
                textToSpeech.stop();
                textToSpeech.shutdown();
                textToSpeech = null;
            }
        } catch (Exception ignored) {
        }

        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
