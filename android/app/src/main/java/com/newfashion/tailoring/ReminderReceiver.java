package com.newfashion.tailoring;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class ReminderReceiver extends BroadcastReceiver {

    private static final String TAG =
            "ReminderReceiver";

    private static final String CHANNEL_ID =
            "reminder_voice_channel_v3";

    private static final String DEBUG_CHANNEL_ID =
            "tts_debug_channel_v1";

    private static final int DEFAULT_NOTIFICATION_ID =
            1001;

    private static final int DEBUG_NOTIFICATION_ID =
            9001;

    /*
     * ElevenLabs TTS backend
     */
    private static final String TTS_URL =
            "https://new-fashion-voice-tts.onrender.com/tts";

    @Override
    public void onReceive(
            Context context,
            Intent intent
    ) {

        String title =
                intent.getStringExtra("title");

        String message =
                intent.getStringExtra("message");

        if (title == null ||
                title.trim().isEmpty()) {

            title = "நினைவூட்டல்";
        }

        if (message == null ||
                message.trim().isEmpty()) {

            message =
                    "உங்களுக்கு ஒரு நினைவூட்டல் உள்ளது.";
        }

        final String finalTitle =
                title;

        final String finalMessage =
                message;

        int notificationId =
                intent.getIntExtra(
                        "requestCode",
                        DEFAULT_NOTIFICATION_ID
                );

        /*
         * Android 13+ notification permission
         */
        if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.TIRAMISU) {

            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED) {

                Log.e(
                        TAG,
                        "Notification permission not granted"
                );

                return;
            }
        }

        /*
         * Notification channels
         */
        createNotificationChannel(context);
        createDebugNotificationChannel(context);

        /*
         * Open MainActivity when notification is tapped
         */
        Intent openIntent =
                new Intent(
                        context,
                        MainActivity.class
                );

        openIntent.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK |
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
        );

        PendingIntent pendingIntent =
                PendingIntent.getActivity(
                        context,
                        notificationId,
                        openIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT |
                                PendingIntent.FLAG_IMMUTABLE
                );

        /*
         * Main notification
         */
        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(
                        context,
                        CHANNEL_ID
                )
                        .setSmallIcon(
                                android.R.drawable.ic_popup_reminder
                        )
                        .setContentTitle(
                                finalTitle
                        )
                        .setContentText(
                                finalMessage
                        )
                        .setStyle(
                                new NotificationCompat.BigTextStyle()
                                        .bigText(finalMessage)
                        )
                        .setPriority(
                                NotificationCompat.PRIORITY_HIGH
                        )
                        .setAutoCancel(true)
                        .setContentIntent(
                                pendingIntent
                        )
                        .setSilent(true);

        NotificationManager notificationManager =
                (NotificationManager)
                        context.getSystemService(
                                Context.NOTIFICATION_SERVICE
                        );

        if (notificationManager != null) {

            notificationManager.notify(
                    notificationId,
                    builder.build()
            );
        }

        /*
         * Keep BroadcastReceiver alive
         * while TTS work is running.
         */
        final BroadcastReceiver.PendingResult
                pendingResult = goAsync();

        /*
         * TTS work
         */
        new Thread(() -> {

            File audioFile = null;

            MediaPlayer player = null;

            try {

                Log.d(
                        TAG,
                        "TTS process started"
                );

                /*
                 * Diagnostic notification
                 */
                showDebugNotification(
                        context,
                        "🔊 குரல் தயாராகிறது",
                        "TTS server-க்கு request அனுப்பப்படுகிறது..."
                );

                /*
                 * Generate TTS MP3
                 */
                audioFile =
                        generateTts(
                                finalMessage,
                                context
                        );

                Log.d(
                        TAG,
                        "MP3 received successfully"
                );

                showDebugNotification(
                        context,
                        "🔊 குரல் கிடைத்தது",
                        "MP3 கிடைத்தது. இப்போது குரல் play ஆகும்."
                );

                /*
                 * Play MP3
                 */
                player =
                        new MediaPlayer();

                player.setAudioAttributes(
                        new AudioAttributes.Builder()
                                .setUsage(
                                        AudioAttributes.USAGE_MEDIA
                                )
                                .setContentType(
                                        AudioAttributes.CONTENT_TYPE_SPEECH
                                )
                                .build()
                );

                player.setDataSource(
                        audioFile.getAbsolutePath()
                );

                final File finalAudioFile =
                        audioFile;

                MediaPlayer finalPlayer =
                        player;

                player.setOnCompletionListener(
                        mp -> {

                            Log.d(
                                    TAG,
                                    "Voice playback completed"
                            );

                            mp.release();

                            if (finalAudioFile.exists()) {
                                finalAudioFile.delete();
                            }

                            pendingResult.finish();
                        }
                );

                player.setOnErrorListener(
                        (mp, what, extra) -> {

                            Log.e(
                                    TAG,
                                    "MediaPlayer error: "
                                            + what
                                            + " / "
                                            + extra
                            );

                            mp.release();

                            if (finalAudioFile.exists()) {
                                finalAudioFile.delete();
                            }

                            showDebugNotification(
                                    context,
                                    "❌ குரல் Play ஆகவில்லை",
                                    "MP3 கிடைத்தது; ஆனால் mobile-ல் play செய்ய முடியவில்லை."
                            );

                            pendingResult.finish();

                            return true;
                        }
                );

                player.prepare();

                player.start();

                Log.d(
                        TAG,
                        "Voice playback started"
                );

            } catch (Exception error) {

                Log.e(
                        TAG,
                        "TTS FAILED",
                        error
                );

                if (player != null) {

                    try {
                        player.release();
                    } catch (Exception ignored) {
                    }
                }

                if (audioFile != null &&
                        audioFile.exists()) {

                    audioFile.delete();
                }

                /*
                 * Show actual error on phone
                 * for debugging.
                 */
                String errorMessage =
                        error.getMessage();

                if (errorMessage == null ||
                        errorMessage.trim().isEmpty()) {

                    errorMessage =
                            error.getClass()
                                    .getSimpleName();
                }

                showDebugNotification(
                        context,
                        "❌ TTS குரல் வரவில்லை",
                        errorMessage
                );

                pendingResult.finish();
            }

        }).start();
    }

    /*
     * Send notification text to backend
     * and receive MP3 audio.
     */
    private File generateTts(
            String text,
            Context context
    ) throws Exception {

        Log.d(
                TAG,
                "Connecting to: " + TTS_URL
        );

        URL url =
                new URL(TTS_URL);

        HttpURLConnection connection =
                (HttpURLConnection)
                        url.openConnection();

        connection.setRequestMethod(
                "POST"
        );

        connection.setDoOutput(
                true
        );

        /*
         * Render Free instance may need
         * 50+ seconds to wake up.
         *
         * Therefore use 90 seconds.
         */
        connection.setConnectTimeout(
                30000
        );

        connection.setReadTimeout(
                90000
        );

        connection.setRequestProperty(
                "Content-Type",
                "application/json; charset=UTF-8"
        );

        /*
         * JSON body
         */
        String json =
                "{\"text\":\""
                        + escapeJson(text)
                        + "\"}";

        Log.d(
                TAG,
                "Sending POST /tts"
        );

        OutputStream output =
                connection.getOutputStream();

        output.write(
                json.getBytes("UTF-8")
        );

        output.flush();
        output.close();

        Log.d(
                TAG,
                "POST /tts sent"
        );

        int responseCode =
                connection.getResponseCode();

        Log.d(
                TAG,
                "TTS response code: "
                        + responseCode
        );

        /*
         * Server error
         */
        if (responseCode !=
                HttpURLConnection.HTTP_OK) {

            InputStream errorStream =
                    connection.getErrorStream();

            StringBuilder errorMessage =
                    new StringBuilder();

            if (errorStream != null) {

                byte[] errorBuffer =
                        new byte[1024];

                int errorLength;

                while ((errorLength =
                        errorStream.read(errorBuffer))
                        != -1) {

                    errorMessage.append(
                            new String(
                                    errorBuffer,
                                    0,
                                    errorLength,
                                    "UTF-8"
                            )
                    );
                }

                errorStream.close();
            }

            connection.disconnect();

            throw new Exception(
                    "TTS server error "
                            + responseCode
                            + ": "
                            + errorMessage
            );
        }

        /*
         * Save returned MP3
         */
        File audioFile =
                new File(
                        context.getCacheDir(),
                        "notification_voice_"
                                + System.currentTimeMillis()
                                + ".mp3"
                );

        InputStream input =
                connection.getInputStream();

        FileOutputStream outputFile =
                new FileOutputStream(
                        audioFile
                );

        byte[] buffer =
                new byte[8192];

        int length;

        while ((length =
                input.read(buffer))
                != -1) {

            outputFile.write(
                    buffer,
                    0,
                    length
            );
        }

        outputFile.flush();
        outputFile.close();

        input.close();

        connection.disconnect();

        /*
         * Verify MP3 file
         */
        if (!audioFile.exists() ||
                audioFile.length() == 0) {

            throw new Exception(
                    "TTS server returned empty audio"
            );
        }

        Log.d(
                TAG,
                "MP3 size: "
                        + audioFile.length()
                        + " bytes"
        );

        return audioFile;
    }

    /*
     * Escape special characters
     * before putting text into JSON.
     */
    private String escapeJson(
            String value
    ) {

        return value
                .replace(
                        "\\",
                        "\\\\"
                )
                .replace(
                        "\"",
                        "\\\""
                )
                .replace(
                        "\n",
                        "\\n"
                )
                .replace(
                        "\r",
                        "\\r"
                )
                .replace(
                        "\t",
                        "\\t"
                );
    }

    /*
     * Main notification channel
     */
    private void createNotificationChannel(
            Context context
    ) {

        if (Build.VERSION.SDK_INT <
                Build.VERSION_CODES.O) {

            return;
        }

        NotificationManager manager =
                context.getSystemService(
                        NotificationManager.class
                );

        if (manager == null) {
            return;
        }

        NotificationChannel channel =
                new NotificationChannel(
                        CHANNEL_ID,
                        "தமிழ் நினைவூட்டல்கள்",
                        NotificationManager.IMPORTANCE_HIGH
                );

        channel.setDescription(
                "Tailoring reminder notifications with voice"
        );

        channel.setSound(
                null,
                null
        );

        manager.createNotificationChannel(
                channel
        );
    }

    /*
     * Temporary TTS diagnostic channel
     */
    private void createDebugNotificationChannel(
            Context context
    ) {

        if (Build.VERSION.SDK_INT <
                Build.VERSION_CODES.O) {

            return;
        }

        NotificationManager manager =
                context.getSystemService(
                        NotificationManager.class
                );

        if (manager == null) {
            return;
        }

        NotificationChannel channel =
                new NotificationChannel(
                        DEBUG_CHANNEL_ID,
                        "TTS Debug",
                        NotificationManager.IMPORTANCE_HIGH
                );

        channel.setDescription(
                "Temporary voice debugging"
        );

        manager.createNotificationChannel(
                channel
        );
    }

    /*
     * Temporary diagnostic notification
     */
    private void showDebugNotification(
            Context context,
            String title,
            String message
    ) {

        try {

            NotificationManager manager =
                    (NotificationManager)
                            context.getSystemService(
                                    Context.NOTIFICATION_SERVICE
                            );

            if (manager == null) {
                return;
            }

            NotificationCompat.Builder builder =
                    new NotificationCompat.Builder(
                            context,
                            DEBUG_CHANNEL_ID
                    )
                            .setSmallIcon(
                                    android.R.drawable.ic_dialog_info
                            )
                            .setContentTitle(
                                    title
                            )
                            .setContentText(
                                    message
                            )
                            .setStyle(
                                    new NotificationCompat.BigTextStyle()
                                            .bigText(message)
                            )
                            .setPriority(
                                    NotificationCompat.PRIORITY_HIGH
                            )
                            .setAutoCancel(true);

            manager.notify(
                    DEBUG_NOTIFICATION_ID,
                    builder.build()
            );

        } catch (Exception error) {

            Log.e(
                    TAG,
                    "Could not show debug notification",
                    error
            );
        }
    }
    }
