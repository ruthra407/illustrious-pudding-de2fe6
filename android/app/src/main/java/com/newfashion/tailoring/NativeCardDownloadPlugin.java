package com.newfashion.tailoring;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

@CapacitorPlugin(name = "NativeCardDownload")
public class NativeCardDownloadPlugin extends Plugin {

    @PluginMethod
    public void saveCard(PluginCall call) {
        String filename = call.getString("filename", "NEW_FASHION_TAILORING_CUSTOMER_CARD.jpg");
        String base64 = call.getString("base64", "");

        if (base64 == null || base64.trim().isEmpty()) {
            call.reject("Card image data is empty");
            return;
        }

        try {
            if (filename == null || filename.trim().isEmpty()) {
                filename = "NEW_FASHION_TAILORING_CUSTOMER_CARD.jpg";
            }
            filename = filename.replaceAll("[^a-zA-Z0-9._-]", "_");
            byte[] bytes = Base64.decode(base64, Base64.DEFAULT);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentResolver resolver = getContext().getContentResolver();
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, filename);
                values.put(MediaStore.Downloads.MIME_TYPE, "image/jpeg");
                values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/New Fashion Tailoring");
                values.put(MediaStore.Downloads.IS_PENDING, 1);

                Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri == null) {
                    call.reject("Android could not create the download file");
                    return;
                }

                try (OutputStream out = resolver.openOutputStream(uri)) {
                    if (out == null) throw new IllegalStateException("Download stream unavailable");
                    out.write(bytes);
                    out.flush();
                }

                ContentValues done = new ContentValues();
                done.put(MediaStore.Downloads.IS_PENDING, 0);
                resolver.update(uri, done, null, null);

                JSObject result = new JSObject();
                result.put("saved", true);
                result.put("location", "Downloads/New Fashion Tailoring/" + filename);
                call.resolve(result);
            } else {
                File dir = new File(getContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "New Fashion Tailoring");
                if (!dir.exists() && !dir.mkdirs()) {
                    call.reject("Could not create app download folder");
                    return;
                }
                File file = new File(dir, filename);
                try (FileOutputStream out = new FileOutputStream(file)) {
                    out.write(bytes);
                    out.flush();
                }

                JSObject result = new JSObject();
                result.put("saved", true);
                result.put("location", file.getAbsolutePath());
                call.resolve(result);
            }
        } catch (Exception e) {
            call.reject("Card save failed: " + e.getMessage(), e);
        }
    }
}
