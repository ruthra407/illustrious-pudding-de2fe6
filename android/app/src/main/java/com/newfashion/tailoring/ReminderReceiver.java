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

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class ReminderReceiver extends BroadcastReceiver {

    private static final String CHANNEL_ID =
            "reminder_voice_channel_v3";

    private static final int DEFAULT_NOTIFICATION_ID = 1001;

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

        final String finalTitle = title;
        final String finalMessage = message;

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

                return;
            }
        }

        /*
         * Notification channel
         */
        createNotificationChannel(context);

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
         * Notification
         *
         * Normal notification sound is disabled.
         * ElevenLabs voice is played separately.
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
         * Generate and play ElevenLabs voice
         */
        final BroadcastReceiver.PendingResult pendingResult =
                goAsync();

        new Thread(() -> {

            File audioFile = null;
            MediaPlayer player = null;

            try {

                /*
                 * Send the exact notification message
                 * to the TTS backend.
                 */
                audioFile =
                        generateTts(
                                finalMessage,
                                context
                        );

                /*
                 * Play returned MP3
                 */
                player =
                        new MediaPlayer();

                player.setAudioAttributes(
                        new AudioAttributes.Builder()
                                .setUsage(
                                        AudioAttributes.USAGE_NOTIFICATION
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

                player.setOnCompletionListener(
                        mp -> {

                            mp.release();

                            if (finalAudioFile.exists()) {
                                finalAudioFile.delete();
                            }

                            pendingResult.finish();
                        }
                );

                player.setOnErrorListener(
                        (mp, what, extra) -> {

                            mp.release();

                            if (finalAudioFile.exists()) {
                                finalAudioFile.delete();
                            }

                            pendingResult.finish();

                            return true;
                        }
                );

                player.prepare();

                player.start();

            } catch (Exception error) {

                error.printStackTrace();

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

        connection.setConnectTimeout(
                15000
        );

        connection.setReadTimeout(
                30000
        );

        connection.setRequestProperty(
                "Content-Type",
                "application/json; charset=UTF-8"
        );

        /*
         * JSON body:
         *
         * {
         *   "text": "notification message"
         * }
         */
        String json =
                "{\"text\":\""
                        + escapeJson(text)
                        + "\"}";

        OutputStream output =
                connection.getOutputStream();

        output.write(
                json.getBytes("UTF-8")
        );

        output.flush();
        output.close();

        int responseCode =
                connection.getResponseCode();

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

            throw new Exception(
                    "TTS server error "
                            + responseCode
                            + ": "
                            + errorMessage
            );
        }

        /*
         * Save generated MP3
         * into app cache.
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
     * Notification channel
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

        /*
         * Disable normal notification sound.
         * ElevenLabs generated voice will play separately.
         */
        channel.setSound(
                null,
                null
        );

        manager.createNotificationChannel(
                channel
        );
    }
}
