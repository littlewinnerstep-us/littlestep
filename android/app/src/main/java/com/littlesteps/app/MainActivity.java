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
}
