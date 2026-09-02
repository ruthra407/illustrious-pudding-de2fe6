package com.newfashion.tailoring;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.provider.MediaStore;
import android.net.Uri;

import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.JSObject;
import com.getcapacitor.PluginMethod;

import java.io.OutputStream;
import android.util.Base64;

@CapacitorPlugin(name = "NativeCardDownload")
public class NativeCardDownloadPlugin extends Plugin {

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
            values.put(
                MediaStore.Downloads.IS_PENDING,
                1
            );

            Uri uri = resolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                values
            );

            if (uri == null) {
                call.reject("Could not create download file");
                return;
            }

            try (OutputStream output =
                     resolver.openOutputStream(uri)) {

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
}
