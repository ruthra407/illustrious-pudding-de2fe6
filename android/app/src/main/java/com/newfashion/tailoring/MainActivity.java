package com.newfashion.tailoring;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Calendar;

public class MainActivity extends Activity {
    private static final int REQ_CONTACT = 501;
    private static final int REQ_CONTACT_PERMISSION = 502;
    private static final int REQ_NOTIFICATION_PERMISSION = 503;
    private WebView webView;
    private String pendingContactAction = null;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webView = new WebView(this);
        setContentView(webView);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);

        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new NativeBridge(this), "Android");
        webView.loadUrl(getString(com.newfashion.tailoring.R.string.app_url));
    }

    private void openContactPicker() {
        if (Build.VERSION.SDK_INT >= 23 &&
                checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            pendingContactAction = "pick";
            requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, REQ_CONTACT_PERMISSION);
            return;
        }
        Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI);
        startActivityForResult(intent, REQ_CONTACT);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_CONTACT || resultCode != RESULT_OK || data == null) return;

        Uri uri = data.getData();
        if (uri == null) return;

        String name = "";
        String phone = "";
        Cursor c = null;
        Cursor p = null;
        try {
            c = getContentResolver().query(uri,
                    new String[]{ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME},
                    null, null, null);
            if (c != null && c.moveToFirst()) {
                String id = c.getString(c.getColumnIndexOrThrow(ContactsContract.Contacts._ID));
                name = c.getString(c.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME));

                p = getContentResolver().query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER},
                        ContactsContract.CommonDataKinds.Phone.CONTACT_ID + "=?",
                        new String[]{id}, null);
                if (p != null && p.moveToFirst()) {
                    phone = p.getString(p.getColumnIndexOrThrow(
                            ContactsContract.CommonDataKinds.Phone.NUMBER));
                }
            }
        } finally {
            if (p != null) p.close();
            if (c != null) c.close();
        }

        final String jsName = JSONObject.quote(name);
        final String jsPhone = JSONObject.quote(phone);
        webView.post(() -> webView.evaluateJavascript(
                "window.onNativeContactSelected && window.onNativeContactSelected(" +
                        jsName + "," + jsPhone + ")", null));
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CONTACT_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openContactPicker();
            } else {
                Toast.makeText(this, "Contacts permission தேவை.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    public class NativeBridge {
        private final Context context;
        NativeBridge(Context context) { this.context = context; }

        @JavascriptInterface public void pickContact() {
            runOnUiThread(MainActivity.this::openContactPicker);
        }

        @JavascriptInterface public void enableReminders(String payload) {
            saveAndSchedule(payload);
            if (Build.VERSION.SDK_INT >= 33 &&
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATION_PERMISSION);
            }
        }

        @JavascriptInterface public void syncReminders(String payload) {
            saveAndSchedule(payload);
        }

        @JavascriptInterface public void disableReminders() {
            getSharedPreferences("nf_reminders", MODE_PRIVATE).edit()
                    .putBoolean("enabled", false).apply();
            ReminderScheduler.cancelAll(MainActivity.this);
        }

        private void saveAndSchedule(String payload) {
            try {
                JSONObject root = new JSONObject(payload);
                getSharedPreferences("nf_reminders", MODE_PRIVATE).edit()
                        .putString("payload", root.toString())
                        .putBoolean("enabled", root.optBoolean("enabled", true))
                        .apply();
                if (root.optBoolean("enabled", true)) {
                    ReminderScheduler.scheduleAll(MainActivity.this, root.optJSONArray("times"));
                }
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this,
                        "Reminder sync failed", Toast.LENGTH_SHORT).show());
            }
        }
    }
              }
