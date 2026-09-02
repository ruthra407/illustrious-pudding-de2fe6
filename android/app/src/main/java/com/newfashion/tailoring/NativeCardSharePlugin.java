package com.newfashion.tailoring;

import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.util.Base64;

import androidx.core.content.FileProvider;

import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

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

        if (base64 == null || base64.trim().isEmpty()) {
            call.reject("Missing card image");
            return;
        }

        try {

            // 1. Sanitize filename
            filename = new File(filename).getName();

            if (filename.isEmpty()) {
                filename =
                        "NEW_FASHION_TAILORING_CUSTOMER_CARD.jpg";
            }

            // 2. Base64 → JPG bytes
            byte[] data = Base64.decode(
                    base64,
                    Base64.DEFAULT
            );

            if (data.length == 0) {
                call.reject("Card image is empty");
                return;
            }

            // 3. App cache/shared_cards
            File shareDir = new File(
                    getContext().getCacheDir(),
                    "shared_cards"
            );

            if (!shareDir.exists() &&
                    !shareDir.mkdirs()) {

                call.reject("Could not create share folder");
                return;
            }

            // 4. Write JPG
            File imageFile = new File(
                    shareDir,
                    filename
            );

            try (FileOutputStream output =
                         new FileOutputStream(imageFile)) {

                output.write(data);
                output.flush();
            }

            // 5. Secure content:// URI
            Uri contentUri = FileProvider.getUriForFile(
                    getContext(),
                    getContext().getPackageName()
                            + ".fileprovider",
                    imageFile
            );

            // 6. Native Android image share
            Intent shareIntent =
                    new Intent(Intent.ACTION_SEND);

            shareIntent.setType("image/jpeg");

            shareIntent.putExtra(
                    Intent.EXTRA_STREAM,
                    contentUri
            );

            shareIntent.addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
            );

            shareIntent.setClipData(
                    ClipData.newRawUri(
                            "Customer Card",
                            contentUri
                    )
            );

            // 7. Native Android Share Sheet
            Intent chooser = Intent.createChooser(
                    shareIntent,
                    "Share Customer Card"
            );

            chooser.addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_ACTIVITY_NEW_TASK
            );

            getContext().startActivity(chooser);

            call.resolve();

        } catch (IllegalArgumentException e) {

            call.reject(
                    "Invalid card image data",
                    e
            );

        } catch (Exception e) {

            call.reject(
                    "Card share failed: "
                            + e.getMessage(),
                    e
            );
        }
    }
}
