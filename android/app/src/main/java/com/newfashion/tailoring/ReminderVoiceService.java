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

    private static final String SERVICE_CHANNEL_ID =
            "reminder_voice_service_v2";

    private static final int SERVICE_NOTIFICATION_ID =
            91001;

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

        executor = Executors.newSingleThreadExecutor();

        audioManager =
                (AudioManager) getSystemService(
                        Context.AUDIO_SERVICE
                );

        createServiceNotificationChannel();

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
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
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

        textToSpeech =
                new TextToSpeech(
                        getApplicationContext(),
                        this
                );
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
                                (installedEngines == null
                                        ? 0
                                        : installedEngines.size())
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

                    fallbackTamilVoice = voice;
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

                if (female && !networkOnly) {

                    femaleTamilVoice = voice;
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
                    "Tamil voice inspection failed",
                    e
            );

            return false;
        }
    }

    private boolean tryTamilLanguageFallback() {

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

            return result !=
                    TextToSpeech.LANG_MISSING_DATA &&
                    result !=
                    TextToSpeech.LANG_NOT_SUPPORTED;

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "Tamil language fallback failed",
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

        if (intent == null) {

            stopSelf(startId);

            return START_NOT_STICKY;
        }

        String message =
                intent.getStringExtra(
                        "message"
                );

        if (message == null ||
                message.trim().isEmpty()) {

            stopSelf(startId);

            return START_NOT_STICKY;
        }

        final String finalMessage =
                prepareTtsText(message);

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

        return START_NOT_STICKY;
    }

    private void speakWhenReady(
            String message,
            int startId
    ) {

        final long timeoutMs = 15000L;

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

        if (!ttsReady ||
                textToSpeech == null) {

            Log.e(
                    TAG,
                    "Android Tamil TTS is not ready"
            );

            updateServiceNotification(
                    "❌ குரல் வரவில்லை",
                    "Phone-ன் Tamil TTS voice தயாராகவில்லை."
            );

            stopForegroundService(startId);

            return;
        }

        AudioAttributes audioAttributes =
                new AudioAttributes.Builder()
                        .setUsage(
                                AudioAttributes.USAGE_MEDIA
                        )
                        .setContentType(
                                AudioAttributes.CONTENT_TYPE_SPEECH
                        )
                        .build();

        try {

            textToSpeech.setPitch(1.08f);

            textToSpeech.setSpeechRate(0.92f);

        } catch (Exception ignored) {
        }

        if (!requestAudioFocus(
                audioAttributes
        )) {

            updateServiceNotification(
                    "❌ குரல் வரவில்லை",
                    "Audio focus கிடைக்கவில்லை."
            );

            stopForegroundService(startId);

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
                            TextToSpeech.QUEUE_FLUSH,
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

            stopForegroundService(startId);

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

            stopForegroundService(startId);

            return;
        }

        Log.d(
                TAG,
                "ANDROID TAMIL VOICE PLAYBACK STARTED"
        );
    }    private void attachUtteranceListener() {

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

                    audioFocusRequest = null;
                }

            } else {

                audioManager.abandonAudioFocus(
                        null
                );
            }

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "Audio focus release error",
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
    }

    private String prepareTtsText(
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

        return convertNumbersToTamil(s);
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
                        "Reminder Voice",
                        NotificationManager
                                .IMPORTANCE_LOW
                );

        channel.setDescription(
                "Tamil voice reminder playback"
        );

        channel.setSound(
                null,
                null
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
                    "Could not update service notification",
                    e
            );
        }
    }

    private String getSafeErrorMessage(
            Exception error
    ) {

        String message =
                error.getMessage();

        if (message == null ||
                message.trim().isEmpty()) {

            return error
                    .getClass()
                    .getSimpleName();
        }

        return message.length() > 180
                ? message.substring(
                        0,
                        180
                )
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

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "Could not shutdown Android TTS",
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
