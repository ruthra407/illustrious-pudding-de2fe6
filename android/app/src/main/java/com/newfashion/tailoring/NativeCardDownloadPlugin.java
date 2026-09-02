package com.newfashion.tailoring;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Base64;

import androidx.core.app.NotificationCompat;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.PluginMethod;

import java.io.OutputStream;

@CapacitorPlugin(name = "NativeCardDownload")
public class NativeCardDownloadPlugin extends Plugin {

    private static final String CHANNEL_ID = "card_downloads";
    private static final int NOTIFICATION_ID = 2001;

    @PluginMethod
    public void saveCard(PluginCall call) {

        String filename = call.getString("filename");
        String base64 = call.getString("base64");
        String mimeType = call.getString("mimeType", "image/jpeg");

        if (filename == null || filename.isEmpty()) {
            call.reject("Missing filename");
            return;
        }

        if (base64 == null || base64.isEmpty()) {
            call.reject("Missing base64 image");
            return;
        }

        try {
            byte[] data = Base64.decode(base64, Base64.DEFAULT);

            ContentResolver resolver = getContext().getContentResolver();

            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, filename);
            values.put(MediaStore.Downloads.MIME_TYPE, mimeType);
            values.put(
                MediaStore.Downloads.RELATIVE_PATH,
                "Download/New Fashion Tailoring"
            );
            values.put(MediaStore.Downloads.IS_PENDING, 1);

            Uri uri = resolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                values
            );

            if (uri == null) {
                call.reject("Could not create download file");
                return;
            }

            try (OutputStream output = resolver.openOutputStream(uri)) {

                if (output == null) {
                    resolver.delete(uri, null, null);
                    call.reject("Could not open download stream");
                    return;
                }

                output.write(data);
                output.flush();
            }

            ContentValues done = new ContentValues();
            done.put(MediaStore.Downloads.IS_PENDING, 0);

            resolver.update(uri, done, null, null);

            // Download notification
            showDownloadNotification(filename, uri);

            JSObject result = new JSObject();
            result.put("ok", true);
            result.put("uri", uri.toString());
            result.put("filename", filename);

            call.resolve(result);

        } catch (Exception e) {
            call.reject(
                "Card download failed: " + e.getMessage(),
                e
            );
        }
    }

    private void showDownloadNotification(String filename, Uri uri) {

        NotificationManager manager =
            (NotificationManager) getContext()
                .getSystemService(NotificationManager.class);

        if (manager == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Card Downloads",
                NotificationManager.IMPORTANCE_DEFAULT
            );

            channel.setDescription(
                "Customer card download notifications"
            );

            manager.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder =
            new NotificationCompat.Builder(
                getContext(),
                CHANNEL_ID
            )
            .setSmallIcon(
                android.R.drawable.stat_sys_download_done
            )
            .setContentTitle("Card Download Complete")
            .setContentText(filename)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        manager.notify(NOTIFICATION_ID, builder.build());
    }
}
