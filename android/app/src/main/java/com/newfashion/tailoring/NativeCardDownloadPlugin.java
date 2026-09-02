package com.newfashion.tailoring;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;

import androidx.core.app.NotificationCompat;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.PluginMethod;

import java.io.InputStream;
import java.io.OutputStream;

@CapacitorPlugin(name = "NativeCardDownload")
public class NativeCardDownloadPlugin extends Plugin {

    private static final String CHANNEL_ID = "card_downloads";
    private static final int NOTIFICATION_ID = 2001;

    @PluginMethod
    public void saveCardFromUri(PluginCall call) {
        String uriString = call.getString("uri");
        String filename = call.getString("filename", "NEW_FASHION_TAILORING_CUSTOMER_CARD.jpg");
        String mimeType = call.getString("mimeType", "image/jpeg");

        if (uriString == null || uriString.trim().isEmpty()) {
            call.reject("Missing card file URI");
            return;
        }

        try {
            Uri sourceUri = Uri.parse(uriString);
            ContentResolver resolver = getContext().getContentResolver();

            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, filename);
            values.put(MediaStore.Downloads.MIME_TYPE, mimeType);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.put(MediaStore.Downloads.RELATIVE_PATH, "Download/New Fashion Tailoring");
                values.put(MediaStore.Downloads.IS_PENDING, 1);
            }

            Uri targetUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (targetUri == null) {
                call.reject("Could not create download file");
                return;
            }

            try (InputStream input = resolver.openInputStream(sourceUri);
                 OutputStream output = resolver.openOutputStream(targetUri)) {

                if (input == null || output == null) {
                    resolver.delete(targetUri, null, null);
                    call.reject("Could not open card file");
                    return;
                }

                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                }
                output.flush();
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues done = new ContentValues();
                done.put(MediaStore.Downloads.IS_PENDING, 0);
                resolver.update(targetUri, done, null, null);
            }

            showDownloadNotification(filename);

            JSObject result = new JSObject();
            result.put("ok", true);
            result.put("uri", targetUri.toString());
            result.put("filename", filename);
            call.resolve(result);

        } catch (Exception e) {
            call.reject("Card download failed: " + e.getMessage(), e);
        }
    }

    // Keep the old API available for compatibility with older HTML builds.
    @PluginMethod
    public void saveCard(PluginCall call) {
        call.reject("Legacy saveCard API disabled for V18; use saveCardFromUri");
    }

    private void showDownloadNotification(String filename) {
        try {
            NotificationManager manager =
                    (NotificationManager) getContext().getSystemService(NotificationManager.class);
            if (manager == null) return;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        "Card Downloads",
                        NotificationManager.IMPORTANCE_DEFAULT
                );
                channel.setDescription("Customer card download notifications");
                manager.createNotificationChannel(channel);
            }

            NotificationCompat.Builder builder = new NotificationCompat.Builder(getContext(), CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setContentTitle("Card Download Complete")
                    .setContentText(filename)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT);

            manager.notify(NOTIFICATION_ID, builder.build());
        } catch (Exception ignored) {
            // Notification failure must never turn a successful file save into a failed download.
        }
    }
}
