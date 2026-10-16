package com.jilanapps.rahenoor;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContentValues;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.provider.MediaStore;
import android.util.Base64;
import java.io.OutputStream;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.webkit.JavascriptInterface;
import android.media.AudioManager;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.GeolocationPermissions;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.initialization.InitializationStatus;
import com.google.android.gms.ads.initialization.OnInitializationCompleteListener;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {
    private static final String HOME_URL = "file:///android_asset/www/index.html";
    private static final String LIVE_URL = "https://jilanapps.github.io/rah-e-noor/";
    private static final String VERSION_URL = "https://jilanapps.github.io/rah-e-noor/version.json";
    private static final String APK_URL = "https://jilanapps.github.io/rah-e-noor/rah-e-noor.apk";
    private WebView wv;
    private AdView adView; // AdMob anchored adaptive banner (bottom, own container)
    private static final String ADMOB_APP_ID = "ca-app-pub-7599547864631357~8548669231"; // manifest meta-data
    private static final String ADMOB_BANNER_ID = "ca-app-pub-7599547864631357/4806444359"; // real banner ad unit
    private AudioManager audioManager;
    private boolean appSilenced = false;
    private ValueCallback<Uri[]> filePathCallback;
    private static final int FILE_CHOOSER_RC = 1001;
    private long downloadId = -1;
    private boolean offlineShown = false;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        // Runtime location permission (needed for WebView geolocation on Android 6+)
        try {
            if (Build.VERSION.SDK_INT >= 23 &&
                checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION}, 2001);
            }
        } catch (Exception e) {}
        wv = new WebView(this);
        WebSettings ws = wv.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setDatabaseEnabled(true);
        ws.setAllowFileAccess(true);
        ws.setAllowContentAccess(true);
        // Needed so the bundled offline app (file://) can fetch its local data files
        try {
            ws.setAllowFileAccessFromFileURLs(true);
            ws.setAllowUniversalAccessFromFileURLs(true);
        } catch (Exception e) {}
        ws.setMediaPlaybackRequiresUserGesture(false);
        ws.setLoadWithOverviewMode(true);
        ws.setUseWideViewPort(true);
        ws.setCacheMode(WebSettings.LOAD_DEFAULT);
        ws.setGeolocationEnabled(true);
        wv.addJavascriptInterface(new JsBridge(), "Android");
        wv.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                try {
                    String u = url.toLowerCase();
                    if (u.startsWith("whatsapp:") || u.contains("wa.me") || u.contains("api.whatsapp.com")) {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                        return true;
                    }
                    if (u.startsWith("tel:") || u.startsWith("mailto:") || u.startsWith("sms:")) {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                        return true;
                    }
                    if (u.startsWith("http://") || u.startsWith("https://")) return false;
                    try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); return true; }
                    catch (Exception e) { return true; }
                } catch (Exception e) { return false; }
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request != null && request.isForMainFrame() && !offlineShown) {
                    offlineShown = true;
                    view.loadUrl("file:///android_asset/offline.html");
                }
            }
            @Override public void onPageFinished(WebView view, String url) {
                if (url != null && url.startsWith(HOME_URL)) offlineShown = false;
            }
        });
        wv.setWebChromeClient(new WebChromeClient() {
            @Override public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(new Runnable() { public void run() {
                    try { request.grant(request.getResources()); }
                    catch (Exception e) { try { request.deny(); } catch (Exception ex) {} }
                }});
            }
            @Override public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                callback.invoke(origin, true, false);
            }
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                try {
                    if (filePathCallback != null) { filePathCallback.onReceiveValue(null); }
                    filePathCallback = callback;
                    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    String[] types = params.getAcceptTypes();
                    intent.setType((types != null && types.length > 0 && types[0] != null && !types[0].isEmpty()) ? types[0] : "*/*");
                    startActivityForResult(Intent.createChooser(intent, "File chunein"), FILE_CHOOSER_RC);
                    return true;
                } catch (Exception e) {
                    try { if (filePathCallback != null) { filePathCallback.onReceiveValue(null); filePathCallback = null; } } catch (Exception ex) {}
                    return false;
                }
            }
        });
        // Layout: WebView fills the screen, AdMob banner lives in its own
        // container BELOW the WebView so it never covers app content/controls.
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams wvParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f);
        root.addView(wv, wvParams);
        adView = new AdView(this);
        adView.setVisibility(View.GONE); // shown only after an ad actually loads
        LinearLayout.LayoutParams adParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        adParams.gravity = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
        root.addView(adView, adParams);
        setContentView(root);
        // Offline-first: the app is bundled inside the APK and works without internet.
        // Prayer times use the built-in calculator offline; live times load when online.
        wv.loadUrl(HOME_URL);
        if (isOnline()) checkForUpdate();
        initAdMobBanner();
        IntentFilter f = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(downloadReceiver, f, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(downloadReceiver, f);
        }
    }

    // ---- AdMob anchored adaptive banner (Google Mobile Ads SDK) ----
    // The banner stays hidden until an ad actually loads, so the app behaves
    // exactly as before when ads are unavailable (offline / load failure).
    private void initAdMobBanner() {
        try {
            MobileAds.initialize(this, new OnInitializationCompleteListener() {
                @Override public void onInitializationComplete(InitializationStatus status) {}
            });
            adView.setAdUnitId(ADMOB_BANNER_ID);
            adView.setAdSize(getAdaptiveAdSize());
            adView.setAdListener(new AdListener() {
                @Override public void onAdLoaded() {
                    runOnUiThread(new Runnable() { public void run() {
                        try { if (adView != null) adView.setVisibility(View.VISIBLE); }
                        catch (Exception e) {}
                    }});
                }
                @Override public void onAdFailedToLoad(LoadAdError error) {
                    // keep hidden — never break the app because of ads
                }
            });
            adView.loadAd(new AdRequest.Builder().build());
        } catch (Exception e) { /* ads must never break the app */ }
    }

    private AdSize getAdaptiveAdSize() {
        try {
            android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
            int adWidth = (int) (dm.widthPixels / dm.density);
            if (adWidth <= 0) adWidth = 320;
            return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(this, adWidth);
        } catch (Exception e) {
            return AdSize.BANNER;
        }
    }

    @Override protected void onPause() {
        try { if (adView != null) adView.pause(); } catch (Exception e) {}
        super.onPause();
    }

    @Override protected void onResume() {
        super.onResume();
        try { if (adView != null) adView.resume(); } catch (Exception e) {}
    }

    private class JsBridge {
        @JavascriptInterface
        public void retry() {
            runOnUiThread(new Runnable() { public void run() {
                if (isOnline()) { offlineShown = false; wv.loadUrl(HOME_URL); }
                else toast("Abhi bhi internet nahi hai!");
            }});
        }
        // v2.17: Silent mode at prayer times (called from JS every 30s)
        @JavascriptInterface
        public void setSilentMode(final boolean silent) {
            runOnUiThread(new Runnable() { public void run() {
                try {
                    if (audioManager == null) {
                        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                    }
                    if (audioManager == null) return;
                    if (silent) {
                        if (audioManager.getRingerMode() != AudioManager.RINGER_MODE_SILENT) {
                            audioManager.setRingerMode(AudioManager.RINGER_MODE_SILENT);
                            appSilenced = true;
                        }
                    } else {
                        if (appSilenced) {
                            audioManager.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                            appSilenced = false;
                        }
                    }
                } catch (Exception e) { /* ignore */ }
            }});
        }
        private void toast(String m) {
            try { Toast.makeText(MainActivity.this, m, Toast.LENGTH_SHORT).show(); } catch (Exception e) {}
        }
        // ---- v2.25: image save/share + generic text share (Daily Image download/share fix) ----
        private Bitmap decodeDataUrl(String dataUrl) {
            try {
                int comma = dataUrl.indexOf(',');
                String b64 = comma >= 0 ? dataUrl.substring(comma + 1) : dataUrl;
                byte[] bytes = Base64.decode(b64, Base64.DEFAULT);
                return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            } catch (Exception e) { return null; }
        }
        private Uri saveBitmapToGallery(Bitmap bmp, String filename) {
            try {
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues cv = new ContentValues();
                    cv.put(MediaStore.Images.Media.DISPLAY_NAME, filename);
                    cv.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                    cv.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Rah-e-Noor");
                    Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
                    if (uri == null) return null;
                    OutputStream os = getContentResolver().openOutputStream(uri);
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, os);
                    os.close();
                    return uri;
                } else {
                    String url = MediaStore.Images.Media.insertImage(getContentResolver(), bmp, filename, "Rah-e-Noor");
                    return url == null ? null : Uri.parse(url);
                }
            } catch (Exception e) { return null; }
        }
        @JavascriptInterface
        public void saveImage(final String dataUrl, final String filename) {
            new Thread(new Runnable() { public void run() {
                final Bitmap bmp = decodeDataUrl(dataUrl);
                final Uri uri = bmp == null ? null : saveBitmapToGallery(bmp, filename);
                runOnUiThread(new Runnable() { public void run() {
                    if (uri != null) toast("\u2705 Tasveer save ho gayi — Gallery / Pictures / Rah-e-Noor");
                    else toast("\u26A0\uFE0F Tasveer save nahi hui — dobara koshish karein");
                }});
            }}).start();
        }
        @JavascriptInterface
        public void shareImage(final String dataUrl, final String text) {
            new Thread(new Runnable() { public void run() {
                final Bitmap bmp = decodeDataUrl(dataUrl);
                final Uri uri = bmp == null ? null : saveBitmapToGallery(bmp, "rah-e-noor-daily.png");
                runOnUiThread(new Runnable() { public void run() {
                    try {
                        if (uri == null) { toast("\u26A0\uFE0F Tasveer share nahi hui — dobara koshish karein"); return; }
                        Intent i = new Intent(Intent.ACTION_SEND);
                        i.setType("image/png");
                        i.putExtra(Intent.EXTRA_STREAM, uri);
                        i.putExtra(Intent.EXTRA_TEXT, text);
                        i.putExtra(Intent.EXTRA_SUBJECT, "Rah-e-Noor — Rozana Tasveer");
                        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(Intent.createChooser(i, "Share via..."));
                    } catch (Exception e) { toast("\u26A0\uFE0F Share nahi khula — dobara koshish karein"); }
                }});
            }}).start();
        }
        @JavascriptInterface
        public void shareText(final String title, final String text) {
            runOnUiThread(new Runnable() { public void run() {
                try {
                    Intent i = new Intent(Intent.ACTION_SEND);
                    i.setType("text/plain");
                    i.putExtra(Intent.EXTRA_SUBJECT, title);
                    i.putExtra(Intent.EXTRA_TEXT, text);
                    startActivity(Intent.createChooser(i, "Share via..."));
                } catch (Exception e) { toast("\u26A0\uFE0F Share nahi khula — dobara koshish karein"); }
            }});
        }
    }

    private boolean isOnline() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo ni = cm.getActiveNetworkInfo();
            return ni != null && ni.isConnected();
        } catch (Exception e) { return true; }
    }

    private void checkForUpdate() {
        new Thread(new Runnable() { public void run() {
            try {
                URL url = new URL(VERSION_URL + "?t=" + System.currentTimeMillis());
                HttpURLConnection c = (HttpURLConnection) url.openConnection();
                c.setConnectTimeout(8000); c.setReadTimeout(8000);
                BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream()));
                StringBuilder sb = new StringBuilder(); String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();
                JSONObject j = new JSONObject(sb.toString());
                final int serverCode = j.optInt("apkVersionCode", 0);
                final String notes = j.optString("notes", "");
                final String apkUrl = j.optString("url", APK_URL);
                int myCode = 0;
                try {
                    PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
                    myCode = pi.versionCode;
                } catch (Exception e) {}
                if (serverCode > myCode) {
                    final int sc = serverCode;
                    runOnUiThread(new Runnable() { public void run() { showUpdateDialog(sc, notes, apkUrl); } });
                }
            } catch (Exception e) { /* silent */ }
        }}).start();
    }

    private void showUpdateDialog(int serverCode, String notes, final String apkUrl) {
        try {
            String msg = "Naya version available hai!\n\n" + notes
                + "\n\nA new version is available. Download karein?";
            new AlertDialog.Builder(this)
                .setTitle("🕌 Rah-e-Noor Update")
                .setMessage(msg)
                .setPositiveButton("⬇ Download", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) { startApkDownload(apkUrl); }
                })
                .setNegativeButton("Baad me / Later", null)
                .setCancelable(true)
                .show();
        } catch (Exception e) {}
    }

    private void startApkDownload(String apkUrl) {
        try {
            DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            DownloadManager.Request req = new DownloadManager.Request(Uri.parse(apkUrl));
            req.setTitle("Rah-e-Noor Update");
            req.setDescription("Naya version download ho raha hai...");
            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "rah-e-noor.apk");
            req.setMimeType("application/vnd.android.package-archive");
            downloadId = dm.enqueue(req);
            Toast.makeText(this, "Download shuru! Complete hone par Install dabayein.", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Download fail: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private final BroadcastReceiver downloadReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context ctx, Intent intent) {
            try {
                long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (id != downloadId) return;
                DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                Uri uri = dm.getUriForDownloadedFile(id);
                if (uri == null) return;
                Intent install = new Intent(Intent.ACTION_VIEW);
                install.setDataAndType(uri, "application/vnd.android.package-archive");
                install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(install);
            } catch (Exception e) {
                Toast.makeText(MainActivity.this, "Install ke liye Downloads folder kholein.", Toast.LENGTH_LONG).show();
            }
        }
    };

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_RC) {
            try {
                if (filePathCallback == null) return;
                Uri[] results = null;
                if (resultCode == Activity.RESULT_OK && data != null && data.getDataString() != null) {
                    results = new Uri[]{Uri.parse(data.getDataString())};
                }
                filePathCallback.onReceiveValue(results);
            } catch (Exception e) {
                try { filePathCallback.onReceiveValue(null); } catch (Exception ex) {}
            }
            filePathCallback = null;
        }
    }

    @Override public void onBackPressed() {
        try { if (wv != null && wv.canGoBack()) { wv.goBack(); return; } } catch (Exception e) {}
        super.onBackPressed();
    }

    @Override protected void onDestroy() {
        try { unregisterReceiver(downloadReceiver); } catch (Exception e) {}
        try { if (adView != null) adView.destroy(); } catch (Exception e) {}
        try { if (wv != null) wv.destroy(); } catch (Exception e) {}
        super.onDestroy();
    }
}
