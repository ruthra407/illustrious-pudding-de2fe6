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
import android.media.MediaPlayer;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ReminderVoiceService extends Service {

    private static final String TAG = "ReminderVoiceService";

    private static final String TTS_URL =
            "https://new-fashion-voice-tts.onrender.com/tts";

    private static final String ELEVENLABS_VOICE_ID =
            "nJPQW86B3xSFcIV4aV5H";

    private static final String SERVICE_CHANNEL_ID =
            "reminder_voice_service_v2";

    private static final int SERVICE_NOTIFICATION_ID = 91001;

    private ExecutorService executor;
    private MediaPlayer mediaPlayer;
    private AudioManager audioManager;
    private AudioFocusRequest audioFocusRequest;
    private File currentAudioFile;

    @Override
    public void onCreate() {
        super.onCreate();

        Log.d(TAG, "========================================");
        Log.d(TAG, "ReminderVoiceService ON CREATE");
        Log.d(TAG, "========================================");

        executor = Executors.newSingleThreadExecutor();

        audioManager = (AudioManager)
                getSystemService(Context.AUDIO_SERVICE);

        createServiceNotificationChannel();

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

        stopCurrentPlayback();

        updateServiceNotification(
                "🔊 குரல் தயாராகிறது",
                "தமிழ் நினைவூட்டல் குரல் உருவாக்கப்படுகிறது..."
        );

        final String finalMessage = message;

        executor.execute(() -> {
            File audioFile = null;

            try {
                Log.d(TAG, "TTS PROCESS STARTED");

                audioFile = generateTts(finalMessage);
                currentAudioFile = audioFile;

                Log.d(TAG, "TTS MP3 CREATED");
                Log.d(TAG, "MP3 SIZE = " +
                        audioFile.length() + " bytes");

                updateServiceNotification(
                        "🔊 குரல் கிடைத்தது",
                        "நினைவூட்டல் பேசப்படுகிறது..."
                );

                playAudio(audioFile, startId);

            } catch (Exception e) {
                Log.e(TAG, "TTS / PLAYBACK FAILED", e);

                if (audioFile != null && audioFile.exists()) {
                    try {
                        audioFile.delete();
                    } catch (Exception ignored) {
                    }
                }

                updateServiceNotification(
                        "❌ குரல் வரவில்லை",
                        getSafeErrorMessage(e)
                );

                stopServiceAfterError(startId);
            }
        });

        return START_NOT_STICKY;
    }

    private File generateTts(String originalText) throws Exception {

        String text = prepareTtsText(originalText);

        Log.d(TAG, "Prepared TTS text = " + text);

        URL url = new URL(TTS_URL);

        HttpURLConnection connection =
                (HttpURLConnection) url.openConnection();

        try {
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(90000);

            connection.setRequestProperty(
                    "Content-Type",
                    "application/json; charset=UTF-8"
            );

            connection.setRequestProperty(
                    "Accept",
                    "audio/mpeg, audio/*"
            );

            String json =
                    "{"
                            + "\"text\":\""
                            + escapeJson(text)
                            + "\","
                            + "\"voice_id\":\""
                            + escapeJson(ELEVENLABS_VOICE_ID)
                            + "\""
                            + "}";

            Log.d(TAG, "Sending TTS POST request...");

            OutputStream output =
                    connection.getOutputStream();

            output.write(
                    json.getBytes(StandardCharsets.UTF_8)
            );

            output.flush();
            output.close();

            int responseCode =
                    connection.getResponseCode();

            Log.d(TAG, "TTS RESPONSE CODE = " +
                    responseCode);

            if (responseCode !=
                    HttpURLConnection.HTTP_OK) {

                throw new Exception(
                        "TTS server error " +
                                responseCode +
                                ": " +
                                readErrorResponse(connection)
                );
            }

            File audioFile =
                    new File(
                            getCacheDir(),
                            "reminder_voice_" +
                                    System.currentTimeMillis() +
                                    ".mp3"
                    );

            InputStream input =
                    connection.getInputStream();

            FileOutputStream outputFile =
                    new FileOutputStream(audioFile);

            byte[] buffer = new byte[8192];
            int length;

            while ((length = input.read(buffer)) != -1) {
                outputFile.write(
                        buffer,
                        0,
                        length
                );
            }

            outputFile.flush();
            outputFile.close();
            input.close();

            if (!audioFile.exists() ||
                    audioFile.length() == 0) {

                throw new Exception(
                        "TTS server returned empty audio"
                );
            }

            return audioFile;

        } finally {
            connection.disconnect();
        }
    }

    private void playAudio(
            File audioFile,
            int startId
    ) {
        try {
            Log.d(TAG, "STARTING AUDIO PLAYBACK");

            AudioAttributes audioAttributes =
                    new AudioAttributes.Builder()
                            .setUsage(
                                    AudioAttributes.USAGE_ALARM
                            )
                            .setContentType(
                                    AudioAttributes.CONTENT_TYPE_SPEECH
                            )
                            .build();

            if (!requestAudioFocus(audioAttributes)) {
                throw new Exception(
                        "Audio focus unavailable"
                );
            }

            MediaPlayer player = new MediaPlayer();
            mediaPlayer = player;

            player.setAudioAttributes(audioAttributes);

            player.setVolume(1.0f, 1.0f);

            player.setDataSource(
                    audioFile.getAbsolutePath()
            );

            player.setOnPreparedListener(mp -> {
                Log.d(TAG, "MEDIAPLAYER PREPARED");

                try {
                    mp.setVolume(1.0f, 1.0f);
                    mp.start();

                    Log.d(TAG, "========================================");
                    Log.d(TAG, "VOICE PLAYBACK STARTED");
                    Log.d(TAG, "========================================");

                } catch (Exception e) {
                    Log.e(
                            TAG,
                            "Could not start MediaPlayer",
                            e
                    );

                    cleanupPlayback(audioFile);
                    stopForegroundService(startId);
                }
            });

            player.setOnCompletionListener(mp -> {
                Log.d(TAG, "VOICE PLAYBACK COMPLETED");

                cleanupPlayback(audioFile);
                stopForegroundService(startId);
            });

            player.setOnErrorListener((mp, what, extra) -> {
                Log.e(
                        TAG,
                        "MEDIAPLAYER ERROR: " +
                                what +
                                " / " +
                                extra
                );

                cleanupPlayback(audioFile);
                stopForegroundService(startId);

                return true;
            });

            player.prepareAsync();

            Log.d(
                    TAG,
                    "MediaPlayer prepareAsync() called"
            );

        } catch (Exception e) {
            Log.e(TAG, "PLAYBACK FAILED", e);

            cleanupPlayback(audioFile);
            stopForegroundService(startId);
        }
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
                                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                        )
                                .setAudioAttributes(attributes)
                                .setAcceptsDelayedFocusGain(false)
                                .build();

                int result =
                        audioManager.requestAudioFocus(
                                audioFocusRequest
                        );

                Log.d(
                        TAG,
                        "Audio focus result = " + result
                );

                return result ==
                        AudioManager.AUDIOFOCUS_REQUEST_GRANTED;

            } else {

                int result =
                        audioManager.requestAudioFocus(
                                null,
                                AudioManager.STREAM_ALARM,
                                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                        );

                return result ==
                        AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
            }

        } catch (Exception e) {
            Log.e(TAG, "AUDIO FOCUS ERROR", e);
            return false;
        }
    }

    private void stopCurrentPlayback() {

        try {
            if (mediaPlayer != null) {

                try {
                    if (mediaPlayer.isPlaying()) {
                        mediaPlayer.stop();
                    }
                } catch (Exception ignored) {
                }

                try {
                    mediaPlayer.reset();
                } catch (Exception ignored) {
                }

                try {
                    mediaPlayer.release();
                } catch (Exception ignored) {
                }

                mediaPlayer = null;
            }

        } catch (Exception e) {
            Log.e(
                    TAG,
                    "Could not stop current playback",
                    e
            );
        }

        abandonAudioFocus();
    }

    private void cleanupPlayback(
            File audioFile
    ) {
        try {
            if (mediaPlayer != null) {

                try {
                    mediaPlayer.reset();
                } catch (Exception ignored) {
                }

                try {
                    mediaPlayer.release();
                } catch (Exception ignored) {
                }

                mediaPlayer = null;
            }

        } catch (Exception e) {
            Log.e(
                    TAG,
                    "MediaPlayer cleanup error",
                    e
            );
        }

        abandonAudioFocus();

        if (audioFile != null &&
                audioFile.exists()) {

            try {
                audioFile.delete();
            } catch (Exception ignored) {
            }
        }

        currentAudioFile = null;
    }

    private void abandonAudioFocus() {

        if (audioManager == null) {
            return;
        }

        try {
            if (Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.O) {

                if (audioFocusRequest != null) {

                    audioManager.abandonAudioFocusRequest(
                            audioFocusRequest
                    );

                    audioFocusRequest = null;
                }

            } else {

                audioManager.abandonAudioFocus(null);
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
        try {
            if (Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.N) {

                stopForeground(
                        Service.STOP_FOREGROUND_REMOVE
                );

            } else {
                stopForeground(true);
            }

        } catch (Exception ignored) {
        }

        stopSelf(startId);
    }

    private void stopServiceAfterError(
            int startId
    ) {
        abandonAudioFocus();

        try {
            if (Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.N) {

                stopForeground(
                        Service.STOP_FOREGROUND_REMOVE
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
        String s = value == null ? "" : value;

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

        for (int i = 20; i >= 1; i--) {
            text = text.replaceAll(
                    "(?<!\\d)" +
                            i +
                            "\\s*நாள்",
                    numbers[i] +
                            " நாள்"
            );
        }

        return text;
    }

    private String escapeJson(
            String value
    ) {
        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String readErrorResponse(
            HttpURLConnection connection
    ) {
        try {
            InputStream errorStream =
                    connection.getErrorStream();

            if (errorStream == null) {
                return "Unknown server error";
            }

            StringBuilder builder =
                    new StringBuilder();

            byte[] buffer = new byte[1024];
            int length;

            while ((length =
                    errorStream.read(buffer)) != -1) {

                builder.append(
                        new String(
                                buffer,
                                0,
                                length,
                                StandardCharsets.UTF_8
                        )
                );
            }

            errorStream.close();

            return builder.toString();

        } catch (Exception e) {
            return e.getMessage() != null
                    ? e.getMessage()
                    : "Unknown server error";
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

        stopCurrentPlayback();

        if (currentAudioFile != null &&
                currentAudioFile.exists()) {

            try {
                currentAudioFile.delete();
            } catch (Exception ignored) {
            }
        }

        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }

        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
