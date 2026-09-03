package com.newfashion.tailoring;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.PluginMethod;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

@CapacitorPlugin(name = "NativeCardDownload")
public class NativeCardDownloadPlugin extends Plugin {

    private static final String CHANNEL_ID = "card_downloads";
    private static final int NOTIFICATION_ID = 2001;

    @PluginMethod
    public void saveCardFromBase64(PluginCall call) {
        String base64 = call.getString("base64");
        String filename = call.getString(
                "filename",
                "NEW_FASHION_TAILORING_CUSTOMER_CARD.jpg"
        );
        String mimeType = call.getString("mimeType", "image/jpeg");

        if (base64 == null || base64.trim().isEmpty()) {
            call.reject("Missing card image data");
            return;
        }

        Uri targetUri = null;

        try {
            filename = new File(filename).getName();
            if (filename.isEmpty()) {
                filename = "NEW_FASHION_TAILORING_CUSTOMER_CARD.jpg";
            }

            byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
            if (bytes.length == 0) {
                call.reject("Card image is empty");
                return;
            }

            ContentResolver resolver = getContext().getContentResolver();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(
                        MediaStore.Images.Media.DISPLAY_NAME,
                        filename
                );
                values.put(
                        MediaStore.Images.Media.MIME_TYPE,
                        mimeType
                );
                values.put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES
                                + "/New Fashion Tailoring"
                );
                values.put(MediaStore.Images.Media.IS_PENDING, 1);

                targetUri = resolver.insert(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        values
                );

                if (targetUri == null) {
                    call.reject("Could not create Gallery image");
                    return;
                }

                try (OutputStream output =
                             resolver.openOutputStream(targetUri)) {

                    if (output == null) {
                        throw new Exception("Could not open Gallery image");
                    }

                    output.write(bytes);
                    output.flush();
                }

                ContentValues done = new ContentValues();
                done.put(MediaStore.Images.Media.IS_PENDING, 0);
                resolver.update(targetUri, done, null, null);

            } else {
                File pictures =
                        Environment.getExternalStoragePublicDirectory(
                                Environment.DIRECTORY_PICTURES
                        );

                File folder = new File(
                        pictures,
                        "New Fashion Tailoring"
                );

                if (!folder.exists() && !folder.mkdirs()) {
                    call.reject("Could not create Pictures folder");
                    return;
                }

                File targetFile = new File(folder, filename);

                try (FileOutputStream output =
                             new FileOutputStream(targetFile, false)) {
                    output.write(bytes);
                    output.flush();
                }

                targetUri = Uri.fromFile(targetFile);

                getContext().sendBroadcast(
                        new Intent(
                                Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                                targetUri
                        )
                );
            }

            showDownloadNotificationIfAllowed(
                    filename,
                    targetUri,
                    mimeType
            );

            JSObject result = new JSObject();
            result.put("ok", true);
            result.put("uri", targetUri.toString());
            result.put("filename", filename);
            result.put(
                    "location",
                    "Pictures/New Fashion Tailoring"
            );
            result.put(
                    "notificationShown",
                    canPostNotifications()
            );

            call.resolve(result);

        } catch (Exception e) {
            if (targetUri != null &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    resolverDelete(targetUri);
                } catch (Exception ignored) {
                }
            }

            call.reject(
                    "Card download failed: " + e.getMessage(),
                    e
            );
        }
    }

    private void resolverDelete(Uri uri) {
        getContext()
                .getContentResolver()
                .delete(uri, null, null);
    }

    private boolean canPostNotifications() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true;
        }

        return ContextCompat.checkSelfPermission(
                getContext(),
                Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED;
    }

    private void showDownloadNotificationIfAllowed(
            String filename,
            Uri imageUri,
            String mimeType
    ) {
        if (!canPostNotifications()) {
            return;
        }

        try {
            NotificationManager manager =
                    (NotificationManager) getContext()
                            .getSystemService(
                                    NotificationManager.class
                            );

            if (manager == null) {
                return;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel =
                        new NotificationChannel(
                                CHANNEL_ID,
                                "Card Downloads",
                                NotificationManager.IMPORTANCE_DEFAULT
                        );

                channel.setDescription(
                        "Customer card download notifications"
                );

                manager.createNotificationChannel(channel);
            }

            Intent openIntent = new Intent(Intent.ACTION_VIEW);
            openIntent.setDataAndType(imageUri, mimeType);
            openIntent.addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
            );

            int flags = PendingIntent.FLAG_UPDATE_CURRENT;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags |= PendingIntent.FLAG_IMMUTABLE;
            }

            PendingIntent pendingIntent =
                    PendingIntent.getActivity(
                            getContext(),
                            NOTIFICATION_ID,
                            openIntent,
                            flags
                    );

            NotificationCompat.Builder builder =
                    new NotificationCompat.Builder(
                            getContext(),
                            CHANNEL_ID
                    )
                    .setSmallIcon(
                            android.R.drawable.stat_sys_download_done
                    )
                    .setContentTitle(
                            "Card Download Complete"
                    )
                    .setContentText(
                            "Tap to open the card in Gallery"
                    )
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .setPriority(
                            NotificationCompat.PRIORITY_DEFAULT
                    )
                    .setStyle(
                            new NotificationCompat.BigTextStyle()
                                    .bigText(
                                            filename
                                            + "\nPictures/New Fashion Tailoring"
                                    )
                    );

            manager.notify(
                    NOTIFICATION_ID,
                    builder.build()
            );

        } catch (Exception ignored) {
            // Notification failure must never fail the download.
        }
    }

    @PluginMethod
    public void saveCardFromUri(PluginCall call) {
        call.reject("Use saveCardFromBase64");
    }

    @PluginMethod
    public void saveCard(PluginCall call) {
        call.reject(
                "Legacy saveCard API disabled; use saveCardFromBase64"
        );
    }
}
