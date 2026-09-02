package com.newfashion.tailoring;

import android.content.Intent;
import android.net.Uri;

import androidx.core.content.FileProvider;

import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.PluginMethod;

import android.util.Base64;

import java.io.File;
import java.io.FileOutputStream;

@CapacitorPlugin(name = "NativeCardShare")
public class NativeCardSharePlugin extends Plugin {

    @PluginMethod
    public void shareCard(PluginCall call) {

        String filename = call.getString(
            "filename",
            "NEW_FASHION_TAILORING_CUSTOMER_CARD.jpg"
        );

        String base64 = call.getString("base64");

        if (base64 == null || base64.isEmpty()) {
            call.reject("Missing card image");
            return;
        }

        try {

            // Base64 → JPG bytes
            byte[] data = Base64.decode(
                base64,
                Base64.DEFAULT
            );

            // App cache folder
            File shareDir = new File(
                getContext().getCacheDir(),
                "shared_cards"
            );

            if (!shareDir.exists()) {
                shareDir.mkdirs();
            }

            File imageFile = new File(
                shareDir,
                filename
            );

            // Write JPG
            try (FileOutputStream output =
                     new FileOutputStream(imageFile)) {

                output.write(data);
                output.flush();
            }

            // Generate secure content:// URI
            Uri contentUri = FileProvider.getUriForFile(
                getContext(),
                getContext().getPackageName() + ".fileprovider",
                imageFile
            );

            // IMPORTANT:
            // Only IMAGE is sent.
            // NO customer text.
            Intent shareIntent = new Intent(
                Intent.ACTION_SEND
            );

            shareIntent.setType("image/jpeg");

            shareIntent.putExtra(
                Intent.EXTRA_STREAM,
                contentUri
            );

            // Give WhatsApp permission to read the image
            shareIntent.addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            );

            // Native Android Share Sheet
            Intent chooser = Intent.createChooser(
                shareIntent,
                "Share Customer Card"
            );

            getContext().startActivity(chooser);

            call.resolve();

        } catch (Exception e) {

            call.reject(
                "Card share failed: " +
                e.getMessage(),
                e
            );
        }
    }
}
