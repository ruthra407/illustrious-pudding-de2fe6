package com.newfashion.tailoring;

import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.util.Base64;

import androidx.core.content.FileProvider;

import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.PluginMethod;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

@CapacitorPlugin(name = "NativeCardShare")
public class NativeCardSharePlugin extends Plugin {

    @PluginMethod
    public void shareCardFromBase64(PluginCall call) {
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

            File shareDir = new File(getContext().getCacheDir(), "shared_cards");
            if (!shareDir.exists() && !shareDir.mkdirs()) {
                call.reject("Could not create share folder");
                return;
            }

            File shareFile = new File(shareDir, filename);
            try (FileOutputStream output = new FileOutputStream(shareFile, false)) {
                output.write(bytes);
                output.flush();
            }

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

            String whatsappPackage = findWhatsAppPackage();
            if (whatsappPackage != null) {
                shareIntent.setPackage(whatsappPackage);

                getContext().grantUriPermission(
                        whatsappPackage,
                        contentUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                );

                getActivity().startActivity(shareIntent);
            } else {
                Intent chooser = Intent.createChooser(
                        shareIntent,
                        "Share Customer Card"
                );
                chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                PackageManager pm = getContext().getPackageManager();
                List<android.content.pm.ResolveInfo> receivers =
                        pm.queryIntentActivities(shareIntent, PackageManager.MATCH_DEFAULT_ONLY);

                for (android.content.pm.ResolveInfo info : receivers) {
                    if (info.activityInfo != null && info.activityInfo.packageName != null) {
                        getContext().grantUriPermission(
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
            call.reject("Invalid card image data", e);
        } catch (Exception e) {
            call.reject("Card share failed: " + e.getMessage(), e);
        }
    }

    private String findWhatsAppPackage() {
        PackageManager pm = getContext().getPackageManager();

        String[] packages = {
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

    @PluginMethod
    public void shareCardFromUri(PluginCall call) {
        call.reject("Use shareCardFromBase64");
    }

    @PluginMethod
    public void shareCard(PluginCall call) {
        call.reject("Legacy shareCard API disabled; use shareCardFromBase64");
    }
}
