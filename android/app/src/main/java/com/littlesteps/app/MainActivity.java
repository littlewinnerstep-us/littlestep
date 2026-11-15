package com.littlesteps.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

public class MainActivity extends Activity {

    private WebView web;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private volatile String lang = "en";

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        tts = new TextToSpeech(this, status -> {
            ttsReady = (status == TextToSpeech.SUCCESS);
            if (ttsReady) applyLanguage();
        });

        web = new WebView(this);
        setContentView(web);
        if (Build.VERSION.SDK_INT >= 23) {
            web.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);              // progress is saved with localStorage
        s.setAllowFileAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setTextZoom(100);

        web.setWebViewClient(new WebViewClient());
        // Lets the game's parent screen record voice clips (microphone).
        web.setWebChromeClient(new android.webkit.WebChromeClient() {
            @Override
            public void onPermissionRequest(final android.webkit.PermissionRequest request) {
                runOnUiThread(() -> {
                    if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, 7);
                    }
                    request.grant(request.getResources());
                });
            }
        });  // keep every page inside the app
        web.setOnLongClickListener(v -> true);      // no text-selection popups
        web.setLongClickable(false);
        web.setHapticFeedbackEnabled(false);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.addJavascriptInterface(new Bridge(), "AndroidTTS");

        File otaIndex = new File(new File(getFilesDir(), "web"), "index.html");
        if (otaIndex.exists() && otaIndex.length() > 1000) {
            web.loadUrl(Uri.fromFile(otaIndex).toString());
        } else {
            web.loadUrl("file:///android_asset/index.html");
        }
        checkAndUpdateWeb();
    }

    /** Called by the game: AndroidTTS.speak(text), AndroidTTS.stop(), AndroidTTS.setLang("en"|"id"). */
    private class Bridge {
        @JavascriptInterface
        public void speak(String text) {
            if (!ttsReady || text == null) return;
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ls");
        }

        /** Same as speak, with natural rate and gentle pitch (avoids robotic pitch-bouncing). */
        @JavascriptInterface
        public void speakWith(String text, double rate, double pitch) {
            if (!ttsReady || text == null) return;
            float r = Math.max(0.72f, Math.min(1.15f, (float) rate));
            // Keep pitch close to 1.0 so neural/natural voices keep their human warmth.
            float p = Math.max(0.94f, Math.min(1.08f, 1.0f + ((float) pitch - 1.0f) * 0.35f));
            tts.setSpeechRate(r);
            tts.setPitch(p);
            tts.speak(text.trim(), TextToSpeech.QUEUE_FLUSH, null, "ls");
        }

        /** "ok", "nolang" (no voice installed for that language) or "none" (no speech engine). */
        @JavascriptInterface
        public String status(String code) {
            if (!ttsReady) return "none";
            Locale loc = "id".equals(code) ? new Locale("id", "ID") : Locale.US;
            int r = tts.isLanguageAvailable(loc);
            return r >= TextToSpeech.LANG_AVAILABLE ? "ok" : "nolang";
        }

        @JavascriptInterface
        public void stop() {
            if (ttsReady) tts.stop();
        }

        @JavascriptInterface
        public void setLang(String code) {
            lang = "id".equals(code) ? "id" : "en";
            if (ttsReady) applyLanguage();
        }
    }

    /** Picks the highest-quality installed voice for the language, preferring natural/neural voices. */
    private void pickBestVoice(Locale loc) {
        try {
            java.util.Set<android.speech.tts.Voice> voices = tts.getVoices();
            if (voices == null) return;
            android.speech.tts.Voice best = null;
            int bestScore = Integer.MIN_VALUE;
            for (android.speech.tts.Voice v : voices) {
                if (v.getLocale() == null || !v.getLocale().getLanguage().equals(loc.getLanguage())) continue;
                int score = 0;
                if (loc.getCountry().equalsIgnoreCase(v.getLocale().getCountry())) score += 30;
                int q = v.getQuality();
                if (q >= android.speech.tts.Voice.QUALITY_VERY_HIGH) score += 50;
                else if (q >= android.speech.tts.Voice.QUALITY_HIGH) score += 35;
                else if (q >= android.speech.tts.Voice.QUALITY_NORMAL) score += 15;
                String name = v.getName() != null ? v.getName().toLowerCase(Locale.US) : "";
                if (name.contains("neural") || name.contains("natural") || name.contains("wavenet")
                        || name.contains("studio") || name.contains("network") || name.contains("google")) {
                    score += 20;
                }
                if (name.contains("espeak") || name.contains("compact") || name.contains("pico")) {
                    score -= 40;
                }
                if (!v.isNetworkConnectionRequired()) score += 5;
                if (best == null || score > bestScore) {
                    best = v;
                    bestScore = score;
                }
            }
            if (best != null) tts.setVoice(best);
        } catch (Exception ignored) { }
    }

    private void applyLanguage() {
        Locale loc = "id".equals(lang) ? new Locale("id", "ID") : Locale.US;
        int r = tts.setLanguage(loc);
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts.setLanguage(Locale.getDefault());
        }
        pickBestVoice(loc);
        tts.setSpeechRate(0.94f);
        tts.setPitch(1.0f);
    }

    @Override
    public void onBackPressed() {
        // Back goes to the game's home screen first; from home it closes the app.
        web.evaluateJavascript("(window.goHome&&window.goHome())===true", value -> {
            if (!"true".equals(value)) finish();
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (tts != null) tts.stop();
        web.evaluateJavascript("window.appPause&&window.appPause()", null);   // stop music when the app is hidden
        web.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
        web.evaluateJavascript("window.appResume&&window.appResume()", null);
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        web.destroy();
        super.onDestroy();
    }

    private static final String OTA_BASE = "https://littlewinnerstep-us.github.io/littlestep/";

    private void checkAndUpdateWeb() {
        new Thread(() -> {
            try {
                String jsonStr = httpGetString(OTA_BASE + "version.json");
                if (jsonStr == null) return;
                JSONObject manifest = new JSONObject(jsonStr);
                String remoteVer = manifest.optString("version", "");
                JSONArray files = manifest.optJSONArray("files");
                if (remoteVer.isEmpty() || files == null || files.length() == 0) return;

                SharedPreferences prefs = getSharedPreferences("ls_ota", MODE_PRIVATE);
                File webDir = new File(getFilesDir(), "web");
                File localIndex = new File(webDir, "index.html");
                if (remoteVer.equals(prefs.getString("web_version", "")) && localIndex.exists()) {
                    return;
                }

                File tmpDir = new File(getFilesDir(), "web_tmp");
                deleteRecursive(tmpDir);
                if (!tmpDir.mkdirs() && !tmpDir.isDirectory()) return;

                for (int i = 0; i < files.length(); i++) {
                    String rel = files.getString(i);
                    if (rel.contains("..") || rel.startsWith("/")) return;
                    File dst = new File(tmpDir, rel);
                    File parent = dst.getParentFile();
                    if (parent != null && !parent.exists() && !parent.mkdirs()) return;
                    if (!httpDownload(OTA_BASE + rel, dst)) {
                        deleteRecursive(tmpDir);
                        return;
                    }
                }

                File tmpIndex = new File(tmpDir, "index.html");
                if (!tmpIndex.exists() || tmpIndex.length() < 1000) {
                    deleteRecursive(tmpDir);
                    return;
                }

                File bakDir = new File(getFilesDir(), "web_bak");
                deleteRecursive(bakDir);
                if (webDir.exists()) webDir.renameTo(bakDir);
                if (!tmpDir.renameTo(webDir)) {
                    if (bakDir.exists()) bakDir.renameTo(webDir);
                    deleteRecursive(tmpDir);
                    return;
                }
                deleteRecursive(bakDir);
                prefs.edit().putString("web_version", remoteVer).apply();

                final String newUrl = Uri.fromFile(new File(webDir, "index.html")).toString();
                runOnUiThread(() -> {
                    if (web == null) return;
                    web.evaluateJavascript(
                        "if(typeof CUR!=='undefined'&&CUR==='home'){location.replace('" + newUrl + "');}"
                        + "else if(window.toast){toast('Update ready — will apply next open');}",
                        null
                    );
                });
            } catch (Exception ignored) { }
        }).start();
    }

    private static String httpGetString(String urlStr) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(8000);
            if (conn.getResponseCode() != 200) return null;
            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
            br.close();
            return sb.toString();
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static boolean httpDownload(String urlStr, File dst) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(15000);
            if (conn.getResponseCode() != 200) return false;
            try (InputStream in = conn.getInputStream(); FileOutputStream out = new FileOutputStream(dst)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            }
            return dst.length() > 0;
        } catch (Exception e) {
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static void deleteRecursive(File f) {
        if (f == null || !f.exists()) return;
        File[] children = f.listFiles();
        if (children != null) {
            for (File c : children) deleteRecursive(c);
        }
        f.delete();
    }
}
