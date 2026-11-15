package com.littlesteps.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

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

        web.loadUrl("file:///android_asset/index.html");
    }

    /** Called by the game: AndroidTTS.speak(text), AndroidTTS.stop(), AndroidTTS.setLang("en"|"id"). */
    private class Bridge {
        @JavascriptInterface
        public void speak(String text) {
            if (!ttsReady || text == null) return;
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ls");
        }

        /** Same as speak, with the game's voice style (lively, slow and gentle after mistakes, calm). */
        @JavascriptInterface
        public void speakWith(String text, double rate, double pitch) {
            if (!ttsReady || text == null) return;
            // Speak sentence by sentence with a little pitch movement so it sounds less flat.
            String[] parts = text.split("(?<=[.!?])\\s+");
            tts.setSpeechRate((float) rate);
            for (int i = 0; i < parts.length; i++) {
                if (parts[i].trim().isEmpty()) continue;
                float p = (float) pitch + (i % 2 == 0 ? 0.07f : -0.05f) + (parts[i].endsWith("!") ? 0.06f : 0f);
                tts.setPitch(Math.max(0.6f, Math.min(1.6f, p)));
                tts.speak(parts[i].trim(), i == 0 ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD, null, "ls" + i);
            }
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

    /** Picks the highest-quality installed voice for the language (Google "network" voices sound the most natural). */
    private void pickBestVoice(Locale loc) {
        try {
            android.speech.tts.Voice best = null;
            for (android.speech.tts.Voice v : tts.getVoices()) {
                if (!v.getLocale().getLanguage().equals(loc.getLanguage())) continue;
                if (best == null || v.getQuality() > best.getQuality()) best = v;
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
        tts.setSpeechRate(0.9f);
        tts.setPitch(1.05f);
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
}
