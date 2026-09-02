package com.newfashion.tailoring;

import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.provider.OpenableColumns;

import androidx.core.content.FileProvider;

import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.PluginMethod;

import java.io.File;

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

        try {
            Uri sourceUri = Uri.parse(uriString);
            Uri shareUri = sourceUri;

            // Filesystem may return a file:// URI. Convert it to a secure content:// URI.
            if ("file".equalsIgnoreCase(sourceUri.getScheme())) {
                File file = new File(sourceUri.getPath());
                if (!file.exists() || file.length() == 0) {
                    call.reject("Card file does not exist or is empty");
                    return;
                }
                shareUri = FileProvider.getUriForFile(
                        getContext(),
                        getContext().getPackageName() + ".fileprovider",
                        file
                );
            } else if ("content".equalsIgnoreCase(sourceUri.getScheme())) {
                if (getContext().getContentResolver().openInputStream(sourceUri) == null) {
                    call.reject("Card content URI cannot be opened");
                    return;
                }
            } else {
                call.reject("Unsupported card URI scheme");
                return;
            }

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType(mimeType);
            shareIntent.putExtra(Intent.EXTRA_STREAM, shareUri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            shareIntent.setClipData(ClipData.newRawUri("Customer Card", shareUri));

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

    // Keep the old API available for compatibility with older HTML builds.
    @PluginMethod
    public void shareCard(PluginCall call) {
        call.reject("Legacy shareCard API disabled for V18; use shareCardFromUri");
    }
}
