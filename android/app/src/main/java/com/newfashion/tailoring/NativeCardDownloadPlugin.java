package com.newfashion.tailoring;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import androidx.core.app.NotificationCompat;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.PluginMethod;

import java.io.File;
import java.io.FileInputStream;
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

        Uri sourceUri = Uri.parse(uriString);
        Uri targetUri = null;

        try {
            ContentResolver resolver = getContext().getContentResolver();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, filename);
                values.put(MediaStore.Images.Media.MIME_TYPE, mimeType);
                values.put(MediaStore.Images.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + "/New Fashion Tailoring");
                values.put(MediaStore.Images.Media.IS_PENDING, 1);

                targetUri = resolver.insert(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        values
                );

                if (targetUri == null) {
                    call.reject("Could not create Gallery image");
                    return;
                }

                copyUriToUri(sourceUri, targetUri);

                ContentValues done = new ContentValues();
                done.put(MediaStore.Images.Media.IS_PENDING, 0);
                resolver.update(targetUri, done, null, null);

            } else {
                // Legacy Android: save into public Pictures and let MediaScanner index it.
                File pictures = Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_PICTURES
                );
                File folder = new File(pictures, "New Fashion Tailoring");
                if (!folder.exists() && !folder.mkdirs()) {
                    call.reject("Could not create Pictures folder");
                    return;
                }

                File targetFile = new File(folder, filename);
                copyUriToFile(sourceUri, targetFile);
                targetUri = Uri.fromFile(targetFile);

                getContext().sendBroadcast(new Intent(
                        Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                        targetUri
                ));
            }

            showDownloadNotification(filename, targetUri, mimeType);

            JSObject result = new JSObject();
            result.put("ok", true);
            result.put("uri", targetUri.toString());
            result.put("filename", filename);
            result.put("location", "Pictures/New Fashion Tailoring");
            call.resolve(result);

        } catch (Exception e) {
            if (targetUri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    getContext().getContentResolver().delete(targetUri, null, null);
                } catch (Exception ignored) { }
            }
            call.reject("Card download failed: " + e.getMessage(), e);
        }
    }

    private void copyUriToUri(Uri source, Uri target) throws Exception {
        ContentResolver resolver = getContext().getContentResolver();
        try (InputStream input = resolver.openInputStream(source);
             OutputStream output = resolver.openOutputStream(target)) {
            if (input == null || output == null) throw new Exception("Could not open card file");
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            output.flush();
        }
    }

    private void copyUriToFile(Uri source, File target) throws Exception {
        ContentResolver resolver = getContext().getContentResolver();
        try (InputStream input = resolver.openInputStream(source);
             OutputStream output = new java.io.FileOutputStream(target)) {
            if (input == null) throw new Exception("Could not open card file");
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            output.flush();
        }
    }

    private void showDownloadNotification(String filename, Uri imageUri, String mimeType) {
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

            Intent openIntent = new Intent(Intent.ACTION_VIEW);
            openIntent.setDataAndType(imageUri, mimeType);
            openIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags |= PendingIntent.FLAG_IMMUTABLE;
            }

            PendingIntent pendingIntent = PendingIntent.getActivity(
                    getContext(),
                    NOTIFICATION_ID,
                    openIntent,
                    flags
            );

            NotificationCompat.Builder builder =
                    new NotificationCompat.Builder(getContext(), CHANNEL_ID)
                            .setSmallIcon(android.R.drawable.stat_sys_download_done)
                            .setContentTitle("Card Download Complete")
                            .setContentText("Tap to open the card in Gallery")
                            .setContentIntent(pendingIntent)
                            .setAutoCancel(true)
                            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                            .setStyle(new NotificationCompat.BigTextStyle()
                                    .bigText(filename + "\nPictures/New Fashion Tailoring"));

            manager.notify(NOTIFICATION_ID, builder.build());
        } catch (Exception ignored) {
            // Notification failure must never turn a successful file save into a failed download.
        }
    }

    @PluginMethod
    public void saveCard(PluginCall call) {
        call.reject("Legacy saveCard API disabled; use saveCardFromUri");
    }
}
