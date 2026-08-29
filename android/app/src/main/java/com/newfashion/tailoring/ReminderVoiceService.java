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
import android.os.IBinder;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ReminderVoiceService extends Service
        implements TextToSpeech.OnInitListener {

    private static final String TAG =
            "ReminderVoiceService";

    /*
     * Foreground-service channel.
     * This is intentionally quiet.
     */
    private static final String SERVICE_CHANNEL_ID =
            "reminder_voice_service_v3";

    /*
     * IMPORTANT:
     *
     * This is the actual reminder alert channel.
     *
     * New channel ID is used because Android remembers
     * the sound/vibration setting of an old channel.
     */
    private static final String ALERT_CHANNEL_ID =
            "reminder_alert_v3";

    private static final int SERVICE_NOTIFICATION_ID =
            91001;

    private static final int ALERT_NOTIFICATION_BASE_ID =
            92000;

    private ExecutorService executor;

    private AudioManager audioManager;

    private AudioFocusRequest audioFocusRequest;

    private TextToSpeech textToSpeech;

    private String selectedEnginePackage;

    private List<TextToSpeech.EngineInfo> installedEngines;

    private int engineTryIndex = -1;

    private volatile boolean ttsReady = false;

    private volatile int currentStartId = -1;

    @Override
    public void onCreate() {

        super.onCreate();

        Log.d(
                TAG,
                "========================================"
        );

        Log.d(
                TAG,
                "REMINDER VOICE SERVICE CREATED"
        );

        Log.d(
                TAG,
                "NOTIFICATION SOUND + VIBRATION + TAMIL TTS"
        );

        Log.d(
                TAG,
                "========================================"
        );

        executor =
                Executors.newSingleThreadExecutor();

        audioManager =
                (AudioManager)
                        getSystemService(
                                Context.AUDIO_SERVICE
                        );

        createServiceNotificationChannel();

        createAlertNotificationChannel();

        Notification notification =
                createServiceNotification(
                        "🔊 நினைவூட்டல்",
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

            Log.d(
                    TAG,
                    "FOREGROUND SERVICE STARTED"
            );

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

        Log.d(
                TAG,
                "========== TTS ENGINE DIAGNOSTIC =========="
        );

        Log.d(
                TAG,
                "TTS INIT STATUS = " +
                        status
        );

        if (textToSpeech == null) {

            Log.e(
                    TAG,
                    "TTS OBJECT = NULL"
            );

            Log.d(
                    TAG,
                    "========== END TTS DIAGNOSTIC =========="
            );

            return;
        }

        try {

            selectedEnginePackage =
                    textToSpeech.getDefaultEngine();

            Log.d(
                    TAG,
                    "ACTIVE TTS ENGINE = " +
                            selectedEnginePackage
            );

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "DEFAULT ENGINE CHECK FAILED",
                    e
            );
        }

        if (status !=
                TextToSpeech.SUCCESS) {

            Log.e(
                    TAG,
                    "DEFAULT TTS INITIALIZATION FAILED = " +
                            status
            );

            if (tryNextInstalledEngine()) {

                return;
            }

            Log.e(
                    TAG,
                    "NO WORKING INSTALLED TTS ENGINE"
            );

            ttsReady = false;

            Log.d(
                    TAG,
                    "========== END TTS DIAGNOSTIC =========="
            );

            return;
        }

        inspectTamilVoices();

        boolean tamilVoiceSelected =
                selectInstalledTamilVoice();

        if (tamilVoiceSelected) {

            ttsReady = true;

            Log.d(
                    TAG,
                    "ANDROID TAMIL VOICE READY"
            );

            attachUtteranceListener();

            Log.d(
                    TAG,
                    "========== END TTS DIAGNOSTIC =========="
            );

            return;
        }

        boolean languageReady =
                tryTamilLanguageFallback();

        if (languageReady) {

            ttsReady = true;

            Log.d(
                    TAG,
                    "ANDROID TAMIL LANGUAGE READY"
            );

        } else {

            ttsReady = false;

            Log.e(
                    TAG,
                    "THIS ENGINE HAS NO USABLE TAMIL VOICE"
            );
        }

        attachUtteranceListener();

        Log.d(
                TAG,
                "========== END TTS DIAGNOSTIC =========="
        );
    }

    private boolean tryNextInstalledEngine() {

        try {

            if (textToSpeech == null) {

                return false;
            }

            if (installedEngines == null) {

                installedEngines =
                        textToSpeech.getEngines();

                engineTryIndex = -1;

                Log.d(
                        TAG,
                        "INSTALLED TTS ENGINE COUNT = " +
                                (
                                        installedEngines == null
                                                ? 0
                                                : installedEngines.size()
                                )
                );
            }

            if (installedEngines == null ||
                    installedEngines.isEmpty()) {

                return false;
            }

            while (engineTryIndex + 1 <
                    installedEngines.size()) {

                engineTryIndex++;

                TextToSpeech.EngineInfo info =
                        installedEngines.get(
                                engineTryIndex
                        );

                if (info == null ||
                        info.name == null) {

                    continue;
                }

                Log.d(
                        TAG,
                        "TRYING TTS ENGINE = " +
                                info.name +
                                " / " +
                                info.label
                );

                try {

                    textToSpeech.stop();

                    textToSpeech.shutdown();

                } catch (Exception ignored) {
                }

                ttsReady = false;

                textToSpeech =
                        new TextToSpeech(
                                getApplicationContext(),
                                this,
                                info.name
                        );

                selectedEnginePackage =
                        info.name;

                return true;
            }

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "INSTALLED ENGINE FALLBACK FAILED",
                    e
            );
        }

        return false;
    }

    private void inspectTamilVoices() {

        if (textToSpeech == null ||
                Build.VERSION.SDK_INT <
                        Build.VERSION_CODES.LOLLIPOP) {

            return;
        }

        try {

            int languageStatus =
                    textToSpeech.isLanguageAvailable(
                            new Locale(
                                    "ta",
                                    "IN"
                            )
                    );

            Log.d(
                    TAG,
                    "TA-IN AVAILABILITY = " +
                            languageStatus
            );

            Set<Voice> voices =
                    textToSpeech.getVoices();

            int tamilCount = 0;

            if (voices != null) {

                for (Voice voice : voices) {

                    if (voice == null ||
                            voice.getLocale() == null) {

                        continue;
                    }

                    Locale locale =
                            voice.getLocale();

                    if (!"ta".equalsIgnoreCase(
                            locale.getLanguage()
                    )) {

                        continue;
                    }

                    tamilCount++;

                    Log.d(
                            TAG,
                            "TAMIL VOICE NAME = " +
                                    voice.getName()
                    );

                    Log.d(
                            TAG,
                            "TAMIL VOICE LOCALE = " +
                                    locale
                    );

                    Log.d(
                            TAG,
                            "TAMIL VOICE FEATURES = " +
                                    voice.getFeatures()
                    );
                }
            }

            Log.d(
                    TAG,
                    "TOTAL TAMIL VOICES = " +
                            tamilCount
            );

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "TAMIL VOICE INSPECTION FAILED",
                    e
            );
        }
    }

    private boolean selectInstalledTamilVoice() {

        if (textToSpeech == null ||
                Build.VERSION.SDK_INT <
                        Build.VERSION_CODES.LOLLIPOP) {

            return false;
        }

        try {

            Set<Voice> voices =
                    textToSpeech.getVoices();

            if (voices == null ||
                    voices.isEmpty()) {

                return false;
            }

            Voice fallbackTamilVoice = null;

            Voice femaleTamilVoice = null;

            for (Voice voice : voices) {

                if (voice == null ||
                        voice.getLocale() == null) {

                    continue;
                }

                Locale locale =
                        voice.getLocale();

                if (!"ta".equalsIgnoreCase(
                        locale.getLanguage()
                )) {

                    continue;
                }

                if (fallbackTamilVoice == null ||
                        "IN".equalsIgnoreCase(
                                locale.getCountry()
                        )) {

                    fallbackTamilVoice =
                            voice;
                }

                String name =
                        voice.getName() == null
                                ? ""
                                : voice.getName()
                                .toLowerCase(
                                        Locale.ROOT
                                );

                String features =
                        voice.getFeatures() == null
                                ? ""
                                : voice.getFeatures()
                                .toString()
                                .toLowerCase(
                                        Locale.ROOT
                                );

                boolean female =
                        name.contains("female") ||
                        name.contains("woman") ||
                        name.contains("feminine") ||
                        features.contains("female") ||
                        features.contains("woman") ||
                        features.contains("feminine");

                boolean networkOnly =
                        features.contains(
                                "network"
                        );

                if (female &&
                        !networkOnly) {

                    femaleTamilVoice =
                            voice;
                }
            }

            Voice selected =
                    femaleTamilVoice != null
                            ? femaleTamilVoice
                            : fallbackTamilVoice;

            if (selected == null) {

                return false;
            }

            int result =
                    textToSpeech.setVoice(
                            selected
                    );

            Log.d(
                    TAG,
                    "SELECTED TAMIL VOICE = " +
                            selected.getName()
            );

            Log.d(
                    TAG,
                    "SELECTED TAMIL LOCALE = " +
                            selected.getLocale()
            );

            Log.d(
                    TAG,
                    "SETVOICE RESULT = " +
                            result
            );

            return result ==
                    TextToSpeech.SUCCESS;

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "TAMIL VOICE SELECTION FAILED",
                    e
            );

            return false;
        }
                        }    private boolean tryTamilLanguageFallback() {

        if (textToSpeech == null) {

            return false;
        }

        try {

            int result =
                    textToSpeech.setLanguage(
                            new Locale(
                                    "ta",
                                    "IN"
                            )
                    );

            Log.d(
                    TAG,
                    "TA-IN SET LANGUAGE RESULT = " +
                            result
            );

            if (result !=
                    TextToSpeech.LANG_MISSING_DATA &&
                    result !=
                    TextToSpeech.LANG_NOT_SUPPORTED) {

                return true;
            }

            result =
                    textToSpeech.setLanguage(
                            new Locale("ta")
                    );

            Log.d(
                    TAG,
                    "TA SET LANGUAGE RESULT = " +
                            result
            );

            return result !=
                    TextToSpeech.LANG_MISSING_DATA &&
                    result !=
                    TextToSpeech.LANG_NOT_SUPPORTED;

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "TAMIL LANGUAGE FALLBACK FAILED",
                    e
            );

            return false;
        }
    }

    @Override
    public int onStartCommand(
            Intent intent,
            int flags,
            int startId
    ) {

        currentStartId = startId;

        Log.d(
                TAG,
                "========================================"
        );

        Log.d(
                TAG,
                "REMINDER RECEIVED"
        );

        Log.d(
                TAG,
                "START ID = " +
                        startId
        );

        if (intent == null) {

            Log.e(
                    TAG,
                    "REMINDER INTENT = NULL"
            );

            stopSelf(startId);

            return START_NOT_STICKY;
        }

        String title =
                intent.getStringExtra(
                        "title"
                );

        String message =
                intent.getStringExtra(
                        "message"
                );

        int requestCode =
                intent.getIntExtra(
                        "requestCode",
                        startId
                );

        if (title == null ||
                title.trim().isEmpty()) {

            title =
                    "🔔 New Fashion Tailoring";
        }

        if (message == null ||
                message.trim().isEmpty()) {

            Log.e(
                    TAG,
                    "REMINDER MESSAGE = EMPTY"
            );

            stopSelf(startId);

            return START_NOT_STICKY;
        }

        final String finalMessage =
                prepareTtsText(
                        message
                );

        /*
         * VERY IMPORTANT:
         *
         * First send the actual reminder notification.
         *
         * Therefore:
         *
         * Notification sound
         * +
         * vibration
         * +
         * full prompt
         *
         * do NOT depend on TTS.
         */
        postReminderNotification(
                title,
                message,
                requestCode,
                startId
        );

        /*
         * Only the foreground-service notification
         * is updated here.
         */
        updateServiceNotification(
                "🔊 குரல் தயாராகிறது",
                "தமிழ் நினைவூட்டல் பேச தயாராகிறது..."
        );

        if (executor == null) {

            executor =
                    Executors.newSingleThreadExecutor();
        }

        executor.execute(
                () -> speakWhenReady(
                        finalMessage,
                        startId
                )
        );

        Log.d(
                TAG,
                "REMINDER NOTIFICATION POSTED"
        );

        return START_NOT_STICKY;
    }

    private void speakWhenReady(
            String message,
            int startId
    ) {

        final long timeoutMs =
                15000L;

        final long startTime =
                System.currentTimeMillis();

        while (!ttsReady &&
                System.currentTimeMillis()
                        - startTime <
                        timeoutMs) {

            try {

                Thread.sleep(100);

            } catch (InterruptedException e) {

                Thread.currentThread()
                        .interrupt();

                return;
            }
        }

        /*
         * TTS failure MUST NOT create another
         * warning notification.
         *
         * The actual reminder notification
         * has already been delivered.
         */
        if (!ttsReady ||
                textToSpeech == null) {

            Log.e(
                    TAG,
                    "ANDROID TAMIL TTS IS NOT READY"
            );

            stopForegroundService(
                    startId
            );

            return;
        }

        AudioAttributes audioAttributes =
                new AudioAttributes.Builder()
                        .setUsage(
                                AudioAttributes
                                        .USAGE_MEDIA
                        )
                        .setContentType(
                                AudioAttributes
                                        .CONTENT_TYPE_SPEECH
                        )
                        .build();

        try {

            textToSpeech.setPitch(
                    1.08f
            );

            textToSpeech.setSpeechRate(
                    0.92f
            );

        } catch (Exception e) {

            Log.w(
                    TAG,
                    "COULD NOT SET TTS PROPERTIES",
                    e
            );
        }

        if (!requestAudioFocus(
                audioAttributes
        )) {

            Log.e(
                    TAG,
                    "AUDIO FOCUS UNAVAILABLE"
            );

            /*
             * No warning notification.
             */
            stopForegroundService(
                    startId
            );

            return;
        }

        updateServiceNotification(
                "🔊 தமிழ் குரல்",
                "நினைவூட்டல் பேசப்படுகிறது..."
        );

        int result;

        try {

            result =
                    textToSpeech.speak(
                            message,
                            TextToSpeech
                                    .QUEUE_FLUSH,
                            null,
                            "reminder_" +
                                    startId
                    );

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "ANDROID TTS SPEAK FAILED",
                    e
            );

            abandonAudioFocus();

            /*
             * Notification has already sounded.
             * Do not replace it with an error notification.
             */
            stopForegroundService(
                    startId
            );

            return;
        }

        if (result !=
                TextToSpeech.SUCCESS) {

            Log.e(
                    TAG,
                    "ANDROID TTS SPEAK RETURNED ERROR = " +
                            result
            );

            abandonAudioFocus();

            stopForegroundService(
                    startId
            );

            return;
        }

        Log.d(
                TAG,
                "ANDROID TAMIL VOICE PLAYBACK STARTED"
        );
    }

    private void attachUtteranceListener() {

        if (textToSpeech == null) {

            return;
        }

        textToSpeech
                .setOnUtteranceProgressListener(
                        new UtteranceProgressListener() {

                            @Override
                            public void onStart(
                                    String utteranceId
                            ) {

                                Log.d(
                                        TAG,
                                        "TTS STARTED: " +
                                                utteranceId
                                );
                            }

                            @Override
                            public void onDone(
                                    String utteranceId
                            ) {

                                Log.d(
                                        TAG,
                                        "TTS COMPLETED: " +
                                                utteranceId
                                );

                                abandonAudioFocus();

                                if (currentStartId !=
                                        -1) {

                                    stopForegroundService(
                                            currentStartId
                                    );
                                }
                            }

                            @Override
                            public void onError(
                                    String utteranceId
                            ) {

                                Log.e(
                                        TAG,
                                        "TTS ERROR: " +
                                                utteranceId
                                );

                                abandonAudioFocus();

                                if (currentStartId !=
                                        -1) {

                                    stopForegroundService(
                                            currentStartId
                                    );
                                }
                            }

                            @Override
                            public void onError(
                                    String utteranceId,
                                    int errorCode
                            ) {

                                Log.e(
                                        TAG,
                                        "TTS ERROR: " +
                                                utteranceId +
                                                " / " +
                                                errorCode
                                );

                                abandonAudioFocus();

                                if (currentStartId !=
                                        -1) {

                                    stopForegroundService(
                                            currentStartId
                                    );
                                }
                            }
                        }
                );
    }

    /*
     * ONLY ONE requestAudioFocus METHOD.
     *
     * No duplicate override.
     */
    private boolean requestAudioFocus(
            AudioAttributes attributes
    ) {

        if (audioManager == null) {

            return true;
        }

        try {

            if (Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.O) {

                audioFocusRequest =
                        new AudioFocusRequest.Builder(
                                AudioManager
                                        .AUDIOFOCUS_GAIN_TRANSIENT
                        )
                                .setAudioAttributes(
                                        attributes
                                )
                                .setAcceptsDelayedFocusGain(
                                        false
                                )
                                .build();

                int result =
                        audioManager
                                .requestAudioFocus(
                                        audioFocusRequest
                                );

                Log.d(
                        TAG,
                        "AUDIO FOCUS RESULT = " +
                                result
                );

                return result ==
                        AudioManager
                                .AUDIOFOCUS_REQUEST_GRANTED;

            } else {

                int result =
                        audioManager.requestAudioFocus(
                                null,
                                AudioManager.STREAM_MUSIC,
                                AudioManager
                                        .AUDIOFOCUS_GAIN_TRANSIENT
                        );

                return result ==
                        AudioManager
                                .AUDIOFOCUS_REQUEST_GRANTED;
            }

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "AUDIO FOCUS ERROR",
                    e
            );

            return false;
        }
    }

    private void abandonAudioFocus() {

        if (audioManager == null) {

            return;
        }

        try {

            if (Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.O) {

                if (audioFocusRequest != null) {

                    audioManager
                            .abandonAudioFocusRequest(
                                    audioFocusRequest
                            );

                    audioFocusRequest =
                            null;
                }

            } else {

                audioManager
                        .abandonAudioFocus(
                                null
                        );
            }

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "AUDIO FOCUS RELEASE ERROR",
                    e
            );
        }
    }

    private void stopForegroundService(
            int startId
    ) {

        abandonAudioFocus();

        try {

            if (Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.N) {

                stopForeground(
                        Service
                                .STOP_FOREGROUND_REMOVE
                );

            } else {

                stopForeground(true);
            }

        } catch (Exception ignored) {
        }

        stopSelf(startId);
    }    private String prepareTtsText(
            String value
    ) {

        String s =
                value == null
                        ? ""
                        : value;

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

        s = s.replaceAll(
                "(?i)\\bNagaraj\\b",
                "நாகராஜ்"
        );

        s = s.replaceAll(
                "(?i)\\bBritannia\\b",
                "பிரிட்டானியா"
        );

        return convertNumbersToTamil(
                s
        );
    }

    private String convertNumbersToTamil(
            String text
    ) {

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

        for (int i = 20;
             i >= 1;
             i--) {

            text =
                    text.replaceAll(
                            "(?<!\\d)" +
                                    i +
                                    "\\s*நாள்",
                            numbers[i] +
                                    " நாள்"
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
                        "Reminder Voice Service",
                        NotificationManager
                                .IMPORTANCE_LOW
                );

        channel.setDescription(
                "Tamil voice reminder service"
        );

        /*
         * Foreground service status remains quiet.
         *
         * This is NOT the actual reminder alert.
         */
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

        /*
         * NOT SILENT.
         */
        channel.setSound(
                soundUri,
                audioAttributes
        );

        channel.enableVibration(
                true
        );

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
                                    Context
                                            .NOTIFICATION_SERVICE
                            );

            if (manager == null) {

                Log.e(
                        TAG,
                        "NOTIFICATION MANAGER = NULL"
                );

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

            PendingIntent pendingIntent =
                    PendingIntent.getActivity(
                            this,
                            requestCode,
                            openIntent,
                            PendingIntent
                                    .FLAG_UPDATE_CURRENT |
                                    PendingIntent
                                            .FLAG_IMMUTABLE
                    );

            int safeRequestCode =
                    requestCode;

            if (safeRequestCode < 0) {

                safeRequestCode =
                        startId;
            }

            int notificationId =
                    ALERT_NOTIFICATION_BASE_ID +
                            (
                                    safeRequestCode %
                                            1000
                            );

            NotificationCompat.BigTextStyle
                    bigTextStyle =
                    new NotificationCompat
                            .BigTextStyle()
                            .bigText(
                                    fullMessage
                            )
                            .setBigContentTitle(
                                    title
                            );

            Uri soundUri =
                    RingtoneManager
                            .getDefaultUri(
                                    RingtoneManager
                                            .TYPE_NOTIFICATION
                            );

            NotificationCompat.Builder builder =
                    new NotificationCompat.Builder(
                            this,
                            ALERT_CHANNEL_ID
                    )
                            .setSmallIcon(
                                    android.R.drawable
                                            .ic_dialog_info
                            )
                            .setContentTitle(
                                    title
                            )
                            .setContentText(
                                    fullMessage
                            )
                            .setStyle(
                                    bigTextStyle
                            )
                            .setContentIntent(
                                    pendingIntent
                            )
                            .setPriority(
                                    NotificationCompat
                                            .PRIORITY_HIGH
                            )
                            .setCategory(
                                    NotificationCompat
                                            .CATEGORY_REMINDER
                            )
                            .setAutoCancel(
                                    true
                            )
                            .setOngoing(
                                    false
                            )
                            .setOnlyAlertOnce(
                                    false
                            );

            /*
             * Android O+:
             * channel controls sound + vibration.
             *
             * Below Android O:
             * notification itself controls them.
             */
            if (Build.VERSION.SDK_INT <
                    Build.VERSION_CODES.O) {

                builder.setSound(
                        soundUri
                );

                builder.setVibrate(
                        new long[] {
                                0,
                                400,
                                200,
                                400
                        }
                );
            }

            Notification notification =
                    builder.build();

            manager.notify(
                    notificationId,
                    notification
            );

            Log.d(
                    TAG,
                    "========================================"
            );

            Log.d(
                    TAG,
                    "REMINDER ALERT SENT"
            );

            Log.d(
                    TAG,
                    "SOUND = ENABLED"
            );

            Log.d(
                    TAG,
                    "VIBRATION = ENABLED"
            );

            Log.d(
                    TAG,
                    "FULL PROMPT = BIG TEXT"
            );

            Log.d(
                    TAG,
                    "NOTIFICATION ID = " +
                            notificationId
            );

            Log.d(
                    TAG,
                    "========================================"
            );

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "REMINDER NOTIFICATION FAILED",
                    e
            );
        }
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
                        PendingIntent
                                .FLAG_UPDATE_CURRENT |
                                PendingIntent
                                        .FLAG_IMMUTABLE
                );

        return new NotificationCompat.Builder(
                this,
                SERVICE_CHANNEL_ID
        )
                .setSmallIcon(
                        android.R.drawable
                                .ic_lock_silent_mode_off
                )
                .setContentTitle(
                        title
                )
                .setContentText(
                        text
                )
                .setPriority(
                        NotificationCompat
                                .PRIORITY_LOW
                )
                .setOngoing(
                        true
                )
                .setContentIntent(
                        pendingIntent
                )
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
                                    Context
                                            .NOTIFICATION_SERVICE
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

        abandonAudioFocus();

        if (executor != null) {

            executor.shutdownNow();

            executor = null;
        }

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
