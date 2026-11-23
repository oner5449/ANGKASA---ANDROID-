
package com.angkasa.music;

import android.app.Activity;
import android.os.Bundle;
import android.os.Build;
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.webkit.*;
import android.content.Context;

import androidx.core.content.ContextCompat;

import java.lang.ref.WeakReference;

public class MainActivity extends Activity {

    private WebView webView;
    private ValueCallback<Uri[]> fileCallback;

    private static WeakReference<MainActivity> activityRef =
        new WeakReference<>(null);

    public static boolean serviceRequested = false;

    private static final int FILE_REQUEST = 2001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        activityRef = new WeakReference<>(this);

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {

            requestPermissions(
                new String[]{Manifest.permission.POST_NOTIFICATIONS},
                3001
            );
        }

        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);

        webView.addJavascriptInterface(
            new AngkasaBridge(this),
            "AngkasaNative"
        );

        webView.setWebViewClient(new WebViewClient());

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(
                WebView view,
                ValueCallback<Uri[]> callback,
                FileChooserParams params
            ) {
                if (fileCallback != null) {
                    fileCallback.onReceiveValue(null);
                }

                fileCallback = callback;

                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);

                startActivityForResult(
                    Intent.createChooser(intent, "Pilih file"),
                    FILE_REQUEST
                );

                return true;
            }
        });

        webView.loadUrl("file:///android_asset/index.html");
    }

    public static class AngkasaBridge {
        private final Context context;

        AngkasaBridge(Context context) {
            this.context = context.getApplicationContext();
        }

        @JavascriptInterface
        public void update(
            String title,
            String artist,
            boolean playing
        ) {
            if (!playing && !serviceRequested) return;

            Intent intent = new Intent(context, PlaybackService.class);
            intent.setAction(PlaybackService.ACTION_UPDATE);
            intent.putExtra("title", title);
            intent.putExtra("artist", artist);
            intent.putExtra("playing", playing);

            if (playing) {
                serviceRequested = true;
                ContextCompat.startForegroundService(context, intent);
            } else {
                context.startService(intent);
            }
        }
    }

    public static void runJavascript(String javascript) {
        MainActivity activity = activityRef.get();

        if (activity == null || activity.webView == null) return;

        activity.runOnUiThread(() ->
            activity.webView.evaluateJavascript(
                "try{" + javascript + "}catch(e){}",
                null
            )
        );
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
    }

    @Override
    protected void onPause() {
        // Jangan pause WebView saat aplikasi diminimalkan.
        super.onPause();
    }

    @Override
    protected void onActivityResult(
        int requestCode,
        int resultCode,
        Intent data
    ) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == FILE_REQUEST && fileCallback != null) {
            Uri[] results = null;

            if (resultCode == RESULT_OK && data != null) {
                if (data.getClipData() != null) {
                    int count = data.getClipData().getItemCount();
                    results = new Uri[count];

                    for (int i = 0; i < count; i++) {
                        results[i] = data.getClipData()
                            .getItemAt(i).getUri();
                    }
                } else if (data.getData() != null) {
                    results = new Uri[]{data.getData()};
                }
            }

            fileCallback.onReceiveValue(results);
            fileCallback = null;
        }
    }

    @Override
    protected void onDestroy() {
        if (activityRef.get() == this) {
            activityRef.clear();
        }

        if (fileCallback != null) {
            fileCallback.onReceiveValue(null);
            fileCallback = null;
        }

        super.onDestroy();
    }
}
