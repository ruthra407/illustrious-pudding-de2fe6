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
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;

import java.util.Locale;

public class ReminderVoiceService extends Service
        implements TextToSpeech.OnInitListener {

    private static final String TAG =
            "ReminderVoiceService";

    private static final String SERVICE_CHANNEL_ID =
            "reminder_voice_service_v2";

    private static final String ALERT_CHANNEL_ID =
            "reminder_alert_v5";

    private static final int SERVICE_NOTIFICATION_ID =
            91001;

    private static final int ALERT_NOTIFICATION_BASE_ID =
            92000;

    private TextToSpeech textToSpeech;

    private volatile boolean ttsReady = false;

    private AudioManager audioManager;

    private AudioFocusRequest audioFocusRequest;

    private final Handler mainHandler =
            new Handler(Looper.getMainLooper());

    @Override
    public void onCreate() {

        super.onCreate();

        audioManager =
                (AudioManager) getSystemService(
                        Context.AUDIO_SERVICE
                );

        createServiceNotificationChannel();

        createAlertNotificationChannel();

        Notification notification =
                createServiceNotification(
                        "நினைவூட்டல்",
                        "குரல் நினைவூட்டல் தயாராகிறது..."
                );

        try {

            if (Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.Q) {

                ServiceCompat.startForeground(
                        this,
                        SERVICE_NOTIFICATION_ID,
                        notification,
                        ServiceInfo
                                .FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                );

            } else {

                startForeground(
                        SERVICE_NOTIFICATION_ID,
                        notification
                );
            }

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "FOREGROUND SERVICE START FAILED",
                    e
            );

            stopSelf();

            return;
        }

        try {

            textToSpeech =
                    new TextToSpeech(
                            getApplicationContext(),
                            this
                    );

            if (Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.LOLLIPOP) {

                textToSpeech.setAudioAttributes(
                        new AudioAttributes.Builder()
                                .setUsage(
                                        AudioAttributes
                                                .USAGE_ASSISTANCE_NAVIGATION_GUIDANCE
                                )
                                .setContentType(
                                        AudioAttributes
                                                .CONTENT_TYPE_SPEECH
                                )
                                .build()
                );
            }

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "TTS OBJECT CREATION FAILED",
                    e
            );

            ttsReady = false;
        }
    }

    @Override
    public void onInit(int status) {

        if (textToSpeech == null) {

            ttsReady = false;

            return;
        }

        if (status != TextToSpeech.SUCCESS) {

            ttsReady = false;

            Log.e(
                    TAG,
                    "TTS INITIALIZATION FAILED = " + status
            );

            return;
        }

        try {

            int result =
                    textToSpeech.setLanguage(
                            new Locale("ta", "IN")
                    );

            ttsReady =
                    result != TextToSpeech.LANG_MISSING_DATA &&
                    result != TextToSpeech.LANG_NOT_SUPPORTED;

            if (ttsReady) {

                textToSpeech.setSpeechRate(0.92f);

                textToSpeech.setPitch(1.0f);

                Log.d(
                        TAG,
                        "TAMIL TTS READY"
                );

            } else {

                Log.e(
                        TAG,
                        "TAMIL TTS LANGUAGE NOT AVAILABLE"
                );
            }

        } catch (Exception e) {

            ttsReady = false;

            Log.e(
                    TAG,
                    "TAMIL TTS SETUP FAILED",
                    e
            );
        }
    }

    @Override
    public int onStartCommand(
            Intent intent,
            int flags,
            int startId
    ) {

        if (intent == null) {

            stopSelf(startId);

            return START_NOT_STICKY;
        }

        String title =
                intent.getStringExtra("title");

        String message =
                intent.getStringExtra("message");

        int requestCode =
                intent.getIntExtra(
                        "requestCode",
                        startId
                );

        if (title == null ||
                title.trim().isEmpty()) {

            title =
                    "New Fashion Tailoring";
        }

        if (message == null ||
                message.trim().isEmpty()) {

            Log.e(
                    TAG,
                    "REMINDER MESSAGE EMPTY"
            );

            stopSelf(startId);

            return START_NOT_STICKY;
        }

        /*
         * Notification:
         * Original notification text is preserved.
         */

        postReminderNotification(
                title,
                message,
                requestCode,
                startId
        );

        /*
         * Voice:
         * Notification UI text is cleaned before TTS.
         */

        speakWithAndroidTts(
                message,
                startId,
                0L
        );

        return START_NOT_STICKY;
    }

    private void speakWithAndroidTts(
            String message,
            int startId,
            long waitedMs
    ) {

        try {

            if (!ttsReady ||
                    textToSpeech == null) {

                if (waitedMs < 15000L) {

                    mainHandler.postDelayed(
                            () -> speakWithAndroidTts(
                                    message,
                                    startId,
                                    waitedMs + 500L
                            ),
                            500L
                    );

                } else {

                    Log.e(
                            TAG,
                            "TAMIL TTS NOT READY AFTER 15 SECONDS"
                    );
                }

                return;
            }

            String speechText =
                    prepareTtsText(message);

            if (speechText.trim().isEmpty()) {

                Log.e(
                        TAG,
                        "CLEANED TTS TEXT IS EMPTY"
                );

                return;
            }

            textToSpeech.stop();

            textToSpeech.setSpeechRate(0.92f);

            textToSpeech.setPitch(1.0f);

            final String utteranceId =
                    "reminder_" +
                    startId +
                    "_" +
                    System.currentTimeMillis();

            requestTtsAudioFocus();

            Bundle params = new Bundle();

            params.putInt(
                    TextToSpeech.Engine.KEY_PARAM_STREAM,
                    AudioManager.STREAM_MUSIC
            );

            textToSpeech.setOnUtteranceProgressListener(
                    new UtteranceProgressListener() {

                        @Override
                        public void onStart(String id) {

                            Log.d(
                                    TAG,
                                    "TTS STARTED"
                            );
                        }

                        @Override
                        public void onDone(String id) {

                            if (utteranceId.equals(id)) {

                                abandonTtsAudioFocus();
                            }
                        }

                        @Override
                        public void onError(String id) {

                            if (utteranceId.equals(id)) {

                                abandonTtsAudioFocus();
                            }
                        }
                    }
            );

            int result =
                    textToSpeech.speak(
                            speechText,
                            TextToSpeech.QUEUE_FLUSH,
                            params,
                            utteranceId
                    );

            if (result != TextToSpeech.SUCCESS) {

                abandonTtsAudioFocus();

                Log.e(
                        TAG,
                        "TTS SPEAK FAILED = " + result
                );
            }

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "ANDROID TTS FAILED",
                    e
            );
        }
    }

    private void requestTtsAudioFocus() {

        try {

            if (audioManager == null ||
                    Build.VERSION.SDK_INT <
                            Build.VERSION_CODES.O) {

                return;
            }

            AudioAttributes attributes =
                    new AudioAttributes.Builder()
                            .setUsage(
                                    AudioAttributes
                                            .USAGE_ASSISTANCE_NAVIGATION_GUIDANCE
                            )
                            .setContentType(
                                    AudioAttributes
                                            .CONTENT_TYPE_SPEECH
                            )
                            .build();

            audioFocusRequest =
                    new AudioFocusRequest.Builder(
                            AudioManager
                                    .AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                    )
                            .setAudioAttributes(
                                    attributes
                            )
                            .setAcceptsDelayedFocusGain(
                                    false
                            )
                            .setWillPauseWhenDucked(
                                    false
                            )
                            .build();

            audioManager.requestAudioFocus(
                    audioFocusRequest
            );

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "AUDIO FOCUS REQUEST FAILED",
                    e
            );
        }
    }

    private void abandonTtsAudioFocus() {

        try {

            if (audioManager == null ||
                    Build.VERSION.SDK_INT <
                            Build.VERSION_CODES.O) {

                return;
            }

            if (audioFocusRequest != null) {

                audioManager.abandonAudioFocusRequest(
                        audioFocusRequest
                );

                audioFocusRequest = null;
            }

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "AUDIO FOCUS RELEASE FAILED",
                    e
            );
        }
    }

    private void postReminderNotification(
            String title,
            String fullMessage,
            int requestCode,
            int startId
    ) {

        try {

            NotificationManager manager =
                    (NotificationManager)
                            getSystemService(
                                    Context.NOTIFICATION_SERVICE
                            );

            if (manager == null) {

                return;
            }

            Intent openIntent =
                    new Intent(
                            this,
                            MainActivity.class
                    );

            openIntent.setFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK |
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            );

            int safeRequestCode =
                    requestCode < 0
                            ? startId
                            : requestCode;

            PendingIntent pendingIntent =
                    PendingIntent.getActivity(
                            this,
                            safeRequestCode,
                            openIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT |
                            PendingIntent.FLAG_IMMUTABLE
                    );

            NotificationCompat.BigTextStyle
                    bigTextStyle =
                    new NotificationCompat
                            .BigTextStyle()
                            .bigText(fullMessage)
                            .setBigContentTitle(title);

            NotificationCompat.Builder builder =
                    new NotificationCompat.Builder(
                            this,
                            ALERT_CHANNEL_ID
                    )
                            .setSmallIcon(
                                    android.R.drawable
                                            .ic_dialog_info
                            )
                            .setContentTitle(title)
                            .setContentText(fullMessage)
                            .setStyle(bigTextStyle)
                            .setContentIntent(pendingIntent)
                            .setPriority(
                                    NotificationCompat
                                            .PRIORITY_HIGH
                            )
                            .setCategory(
                                    NotificationCompat
                                            .CATEGORY_REMINDER
                            )
                            .setAutoCancel(true)
                            .setOnlyAlertOnce(false);

            if (Build.VERSION.SDK_INT <
                    Build.VERSION_CODES.O) {

                Uri soundUri =
                        RingtoneManager.getDefaultUri(
                                RingtoneManager
                                        .TYPE_NOTIFICATION
                        );

                builder.setSound(soundUri);

                builder.setVibrate(
                        new long[] {
                                0,
                                400,
                                200,
                                400
                        }
                );
            }

            int notificationId =
                    ALERT_NOTIFICATION_BASE_ID +
                    (safeRequestCode % 1000);

            manager.notify(
                    notificationId,
                    builder.build()
            );

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "REMINDER NOTIFICATION FAILED",
                    e
            );
        }
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
                        "Reminder Voice Service",
                        NotificationManager
                                .IMPORTANCE_LOW
                );

        channel.setDescription(
                "Tamil voice reminder service"
        );

        channel.setSound(
                null,
                null
        );

        manager.createNotificationChannel(
                channel
        );
    }

    private void createAlertNotificationChannel() {

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

        Uri soundUri =
                RingtoneManager.getDefaultUri(
                        RingtoneManager
                                .TYPE_NOTIFICATION
                );

        AudioAttributes audioAttributes =
                new AudioAttributes.Builder()
                        .setUsage(
                                AudioAttributes
                                        .USAGE_NOTIFICATION
                        )
                        .setContentType(
                                AudioAttributes
                                        .CONTENT_TYPE_SONIFICATION
                        )
                        .build();

        NotificationChannel channel =
                new NotificationChannel(
                        ALERT_CHANNEL_ID,
                        "Reminder Alerts",
                        NotificationManager
                                .IMPORTANCE_HIGH
                );

        channel.setDescription(
                "Reminder alerts with sound and vibration"
        );

        channel.setSound(
                soundUri,
                audioAttributes
        );

        channel.enableVibration(true);

        channel.setVibrationPattern(
                new long[] {
                        0,
                        400,
                        200,
                        400
                }
        );

        manager.createNotificationChannel(
                channel
        );
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
                        NotificationCompat
                                .PRIORITY_LOW
                )
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .build();    private String prepareTtsText(
            String value
    ) {

        String s =
                value == null
                        ? ""
                        : value;

        s = cleanReminderVoiceText(s);

        s = s.replaceAll(
                "(?i)\\bBlouse\\s+pieces?\\b",
                "ப்ளவுஸ்கள்"
        );

        s = s.replaceAll(
                "(?i)\\bLining\\s+Blouse(?:\\s+pieces?)?\\b",
                "லைனிங் ப்ளவுஸ்கள்"
        );

        s = s.replaceAll(
                "(?i)\\bBlouse\\b",
                "ப்ளவுஸ்"
        );

        s = s.replaceAll(
                "(?i)\\bChudi\\b",
                "சுடிதார்"
        );

        s = s.replaceAll(
                "(?i)\\bSaree\\b",
                "சாரி"
        );

        s = s.replaceAll(
                "(?i)\\bShirt\\b",
                "சர்ட்"
        );

        s = s.replaceAll(
                "(?i)\\bcustomer\\b",
                "கஸ்டமர்"
        );

        /*
         * ஊர் பெயர்களின் சரியான உச்சரிப்பு.
         */

        s = s.replace(
                "Manthakudipatty",
                "மாந்தகுடிப்பட்டி"
        );

        s = s.replace(
                "Manthakudipatti",
                "மாந்தகுடிப்பட்டி"
        );

        s = s.replace(
                "மந்தாகுடிப்பட்டி",
                "மாந்தகுடிப்பட்டி"
        );

        s = s.replace(
                "Ponnamaravathi",
                "பொன்னமராவதி"
        );

        s = s.replace(
                "Ponnamaravathy",
                "பொன்னமராவதி"
        );

        /*
         * தேவையற்ற symbols.
         */

        s = s.replaceAll(
                "[•●▪◦◆◇★☆✓✔️🔔⏰⏳📢📌📍🔊🕐🕑🕒🕓🕔🕕🕖🕗🕘🕙🕚🕛]",
                " "
        );

        s = s.replaceAll(
                "\\s{2,}",
                " "
        ).trim();

        return convertNumbersToTamil(s).trim();
    }

    private String cleanReminderVoiceText(
            String value
    ) {

        if (value == null) {
            return "";
        }

        String[] lines =
                value.replace("\r", "")
                        .split("\\n");

        StringBuilder cleaned =
                new StringBuilder();

        for (String rawLine : lines) {

            if (rawLine == null) {
                continue;
            }

            String line =
                    rawLine.trim();

            if (line.isEmpty()) {
                continue;
            }

            /*
             * Notification headings மட்டும்
             * குரலில் வாசிக்கக்கூடாது.
             */

            if (line.contains(
                    "இன்றைய வேலை நிலவரம்"
            )) {
                continue;
            }

            if (line.contains(
                    "இன்று செய்ய:"
            )) {
                continue;
            }

            if (line.contains(
                    "அடுத்த நாட்கள்:"
            )) {
                continue;
            }

            if (line.matches(
                    ".*இன்று\\s+செய்ய\\s*[:：].*"
            )) {
                continue;
            }

            if (line.matches(
                    ".*அடுத்த\\s+நாட்கள்\\s*[:：].*"
            )) {
                continue;
            }

            /*
             * Emoji மற்றும் notification symbols
             * குரலில் வரக்கூடாது.
             */

            line = line.replaceAll(
                    "[\\p{So}\\p{Sk}]",
                    " "
            );

            line = line.replaceAll(
                    "^[\\s•●▪◦\\-–—:：]+",
                    ""
            );

            line = line.replaceAll(
                    "\\s{2,}",
                    " "
            ).trim();

            if (line.isEmpty()) {
                continue;
            }

            if (cleaned.length() > 0) {
                cleaned.append(". ");
            }

            cleaned.append(line);
        }

        return cleaned.toString().trim();
    }

    private String convertNumbersToTamil(
            String text
    ) {

        if (text == null ||
                text.trim().isEmpty()) {

            return "";
        }

        String[] numbers = {
                "",
                "ஒரு",
                "இரண்டு",
                "மூன்று",
                "நான்கு",
                "ஐந்து",
                "ஆறு",
                "ஏழு",
                "எட்டு",
                "ஒன்பது",
                "பத்து",
                "பதினொன்று",
                "பன்னிரண்டு",
                "பதின்மூன்று",
                "பதினான்கு",
                "பதினைந்து",
                "பதினாறு",
                "பதினேழு",
                "பதினெட்டு",
                "பத்தொன்பது",
                "இருபது"
        };

        for (int i = 20; i >= 1; i--) {

            text =
                    text.replaceAll(
                            "(?<!\\d)" +
                                    i +
                                    "\\s*நாள்",
                            numbers[i] +
                                    " நாள்"
                    );

            text =
                    text.replaceAll(
                            "(?<!\\d)" +
                                    i +
                                    "\\s*நாட்கள்",
                            numbers[i] +
                                    " நாட்கள்"
                    );
        }

        return text;
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
                    "SERVICE NOTIFICATION UPDATE FAILED",
                    e
            );
        }
    }

    @Override
    public void onDestroy() {

        Log.d(
                TAG,
                "REMINDER VOICE SERVICE DESTROY"
        );

        try {

            if (textToSpeech != null) {

                textToSpeech.stop();

                textToSpeech.shutdown();

                textToSpeech = null;
            }

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "TTS SHUTDOWN FAILED",
                    e
            );
        }

        ttsReady = false;

        abandonTtsAudioFocus();

        mainHandler.removeCallbacksAndMessages(
                null
        );

        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(
            Intent intent
    ) {

        return null;
    }
        }
