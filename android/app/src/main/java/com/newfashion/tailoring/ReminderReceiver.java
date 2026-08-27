package com.newfashion.tailoring;

import android.Manifest;
import android.app.BroadcastReceiver;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
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

    private static final String CHANNEL_ID = "reminder_voice_channel_v3";
    private static final int DEFAULT_NOTIFICATION_ID = 1001;

    // இங்கே உங்கள் deployed TTS server URL போட வேண்டும்
    private static final String TTS_URL =
            "https://YOUR-BACKEND-URL/tts";

    @Override
    public void onReceive(Context context, Intent intent) {

        String title = intent.getStringExtra("title");
        String message = intent.getStringExtra("message");

        if (title == null || title.trim().isEmpty()) {
            title = "நினைவூட்டல்";
        }

        if (message == null || message.trim().isEmpty()) {
            message = "உங்களுக்கு ஒரு நினைவூட்டல் உள்ளது.";
        }

        final String finalTitle = title;
        final String finalMessage = message;

        int notificationId = intent.getIntExtra(
                "requestCode",
                DEFAULT_NOTIFICATION_ID
        );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }

        createNotificationChannel(context);

        Intent openIntent = new Intent(
                context,
                MainActivity.class
        );

        openIntent.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
        );

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT
                        | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(
                        context,
                        CHANNEL_ID
                )
                        .setSmallIcon(
                                android.R.drawable.ic_popup_reminder
                        )
                        .setContentTitle(finalTitle)
                        .setContentText(finalMessage)
                        .setStyle(
                                new NotificationCompat.BigTextStyle()
                                        .bigText(finalMessage)
                        )
                        .setPriority(
                                NotificationCompat.PRIORITY_HIGH
                        )
                        .setAutoCancel(true)
                        .setContentIntent(pendingIntent)
                        .setSilent(true);

        NotificationManager manager =
                (NotificationManager)
                        context.getSystemService(
                                Context.NOTIFICATION_SERVICE
                        );

        if (manager != null) {
            manager.notify(
                    notificationId,
                    builder.build()
            );
        }

        android.content.BroadcastReceiver.PendingResult pendingResult =
                goAsync();

        new Thread(() -> {

            File audioFile = null;
            MediaPlayer player = null;

            try {

                audioFile = generateTts(
                        finalMessage,
                        context
                );

                player = new MediaPlayer();

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

                MediaPlayer finalPlayer = player;

                player.setOnCompletionListener(mp -> {
                    mp.release();

                    if (audioFile != null) {
                        audioFile.delete();
                    }

                    pendingResult.finish();
                });

                player.prepare();
                player.start();

            } catch (Exception e) {

                e.printStackTrace();

                if (player != null) {
                    try {
                        player.release();
                    } catch (Exception ignored) {
                    }
                }

                if (audioFile != null) {
                    audioFile.delete();
                }

                pendingResult.finish();
            }

        }).start();
    }

    private File generateTts(
            String text,
            Context context
    ) throws Exception {

        URL url = new URL(TTS_URL);

        HttpURLConnection connection =
                (HttpURLConnection) url.openConnection();

        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);

        connection.setRequestProperty(
                "Content-Type",
                "application/json"
        );

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

        if (connection.getResponseCode() != 200) {
            throw new Exception(
                    "TTS server error: "
                            + connection.getResponseCode()
            );
        }

        File file = new File(
                context.getCacheDir(),
                "notification_voice.mp3"
        );

        InputStream input =
                connection.getInputStream();

        FileOutputStream outputFile =
                new FileOutputStream(file);

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

        connection.disconnect();

        return file;
    }

    private String escapeJson(String value) {

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private void createNotificationChannel(
            Context context
    ) {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
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
                "Tailoring reminder notifications"
        );

        // Notification sound OFF.
        // TTS voice தனியாக MediaPlayer மூலம் play ஆகும்.
        channel.setSound(null, null);

        manager.createNotificationChannel(channel);
    }
}
