package com.newfashion.tailoring;

import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;

import androidx.core.content.FileProvider;

import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.PluginMethod;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

@CapacitorPlugin(name = "NativeCardShare")
public class NativeCardSharePlugin extends Plugin {

    @PluginMethod
    public void shareCardFromUri(PluginCall call) {
        String uriString = call.getString("uri");
        String filename = call.getString("filename", "NEW_FASHION_TAILORING_CUSTOMER_CARD.jpg");
        String mimeType = call.getString("mimeType", "image/jpeg");

        if (uriString == null || uriString.trim().isEmpty()) {
            call.reject("Missing card file URI");
            return;
        }

        File shareFile = null;

        try {
            Uri sourceUri = Uri.parse(uriString);

            // Always create our own cache copy and FileProvider URI.
            // This avoids relying on another provider's temporary URI permissions.
            File shareDir = new File(getContext().getCacheDir(), "shared_cards");
            if (!shareDir.exists() && !shareDir.mkdirs()) {
                call.reject("Could not create share folder");
                return;
            }

            filename = new File(filename).getName();
            if (filename.isEmpty()) filename = "NEW_FASHION_TAILORING_CUSTOMER_CARD.jpg";
            shareFile = new File(shareDir, filename);

            ContentCopy.copy(getContext(), sourceUri, shareFile);

            if (!shareFile.exists() || shareFile.length() == 0) {
                call.reject("Card file is empty");
                return;
            }

            Uri contentUri = FileProvider.getUriForFile(
                    getContext(),
                    getContext().getPackageName() + ".fileprovider",
                    shareFile
            );

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType(mimeType);
            shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            shareIntent.setClipData(ClipData.newRawUri("Customer Card", contentUri));

            Intent chooser = Intent.createChooser(shareIntent, "Share Customer Card");
            chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);

            getContext().startActivity(chooser);
            call.resolve();

        } catch (IllegalArgumentException e) {
            call.reject("Invalid card file URI", e);
        } catch (Exception e) {
            call.reject("Card share failed: " + e.getMessage(), e);
        }
    }

    @PluginMethod
    public void shareCard(PluginCall call) {
        call.reject("Legacy shareCard API disabled; use shareCardFromUri");
    }

    private static final class ContentCopy {
        static void copy(android.content.Context context, Uri source, File target) throws Exception {
            try (InputStream input = context.getContentResolver().openInputStream(source);
                 OutputStream output = new FileOutputStream(target)) {
                if (input == null) throw new Exception("Could not open card file");
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                }
                output.flush();
            }
        }
    }
}
