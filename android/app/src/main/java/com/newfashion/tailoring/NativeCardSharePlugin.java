package com.newfashion.tailoring;

import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;

import androidx.core.content.FileProvider;

import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.PluginMethod;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

@CapacitorPlugin(name = "NativeCardShare")
public class NativeCardSharePlugin extends Plugin {

    @PluginMethod
    public void shareCardFromUri(PluginCall call) {
        String uriString = call.getString("uri");
        String filename = safeFilename(
                call.getString("filename", "NEW_FASHION_TAILORING_CUSTOMER_CARD.jpg")
        );
        String mimeType = call.getString("mimeType", "image/jpeg");

        if (uriString == null || uriString.trim().isEmpty()) {
            call.reject("Missing card file URI");
            return;
        }

        try {
            Uri sourceUri = Uri.parse(uriString);

            // Make a private cache copy owned by this app.
            File shareDir = new File(getContext().getCacheDir(), "shared_cards");
            if (!shareDir.exists() && !shareDir.mkdirs()) {
                call.reject("Could not create share folder");
                return;
            }

            File shareFile = new File(shareDir, filename);

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
            shareIntent.setClipData(
                    ClipData.newRawUri("Customer Card", contentUri)
            );

            PackageManager pm = getContext().getPackageManager();

            // Prefer WhatsApp when installed.
            String whatsappPackage = findWhatsAppPackage(pm);

            if (whatsappPackage != null) {
                shareIntent.setPackage(whatsappPackage);

                // Explicitly grant the URI permission to WhatsApp.
                grantUriPermission(
                        whatsappPackage,
                        contentUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                );

                getActivity().startActivity(shareIntent);
            } else {
                // WhatsApp is not installed: use Android share chooser.
                Intent chooser = Intent.createChooser(
                        shareIntent,
                        "Share Customer Card"
                );
                chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                // Grant URI access to every app that can receive this image.
                List<ResolveInfo> receivers =
                        pm.queryIntentActivities(shareIntent, PackageManager.MATCH_DEFAULT_ONLY);

                for (ResolveInfo info : receivers) {
                    if (info.activityInfo != null &&
                            info.activityInfo.packageName != null) {
                        grantUriPermission(
                                info.activityInfo.packageName,
                                contentUri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                        );
                    }
                }

                getActivity().startActivity(chooser);
            }

            call.resolve();

        } catch (IllegalArgumentException e) {
            call.reject("Invalid card file URI", e);
        } catch (Exception e) {
            call.reject(
                    "Card share failed: " +
                    (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()),
                    e
            );
        }
    }

    private String findWhatsAppPackage(PackageManager pm) {
        String[] packages = new String[] {
                "com.whatsapp",
                "com.whatsapp.w4b"
        };

        for (String pkg : packages) {
            try {
                pm.getPackageInfo(pkg, 0);
                return pkg;
            } catch (PackageManager.NameNotFoundException ignored) {
            }
        }

        return null;
    }

    private void grantUriPermission(
            String packageName,
            Uri uri,
            int flags
    ) {
        try {
            getContext().grantUriPermission(packageName, uri, flags);
        } catch (Exception ignored) {
        }
    }

    private String safeFilename(String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            return "NEW_FASHION_TAILORING_CUSTOMER_CARD.jpg";
        }

        String clean = new File(filename).getName();

        if (clean.isEmpty()) {
            return "NEW_FASHION_TAILORING_CUSTOMER_CARD.jpg";
        }

        return clean;
    }

    @PluginMethod
    public void shareCard(PluginCall call) {
        call.reject("Legacy shareCard API disabled; use shareCardFromUri");
    }

    private static final class ContentCopy {

        static void copy(
                android.content.Context context,
                Uri source,
                File target
        ) throws Exception {

            try (InputStream input =
                         context.getContentResolver().openInputStream(source);
                 OutputStream output =
                         new FileOutputStream(target)) {

                if (input == null) {
                    throw new Exception("Could not open card file");
                }

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
