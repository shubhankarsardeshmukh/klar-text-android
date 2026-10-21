package com.klartext.app;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.CancellationSignal;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialCancellationException;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.credentials.exceptions.NoCredentialException;
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import org.json.JSONArray;
import org.json.JSONObject;
import androidx.webkit.WebViewAssetLoader;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private TextToSpeech tts;
    private volatile boolean ttsReady = false;
    private boolean triedRemoteFallback = false;

    // The single, always-current source of truth — the same URL the web
    // version and (eventually) the iOS version load. Whenever a change is
    // deployed via `firebase deploy`, every platform sees it immediately,
    // with no app-store release needed for ordinary content/logic changes.
    private static final String REMOTE_URL = "https://klartext-german.web.app/";
    // Bundled copy in app/src/main/assets/, used ONLY as an offline
    // fallback when REMOTE_URL can't be reached — it deliberately still
    // has a placeholder FIREBASE_CONFIG (see index.html), so it runs in
    // plain local-only mode, which is exactly right for "no internet
    // anyway" and avoids ever showing a broken half-loaded sign-in screen.
    private static final String LOCAL_FALLBACK_URL = "https://appassets.androidplatform.net/assets/index.html";

    // Runtime-permission request code for POST_NOTIFICATIONS (Feature 2).
    private static final int REQUEST_CODE_NOTIFICATIONS = 1001;
    // Runtime-permission request code for RECORD_AUDIO (speaking exercise).
    private static final int REQUEST_CODE_MIC = 1002;
    // "Nudge me if I stay away": settings kept natively so the background jobs know them.
    static final String PREFS = "klartext_native_prefs";
    static final String PREF_COMEBACK = "comeback_on";
    static final String PREF_STREAK = "streak";
    private static final int[] COMEBACK_DAYS = {2, 4, 7, 14};
    private SpeechRecognizer speech;
    private String pendingSpeechLang = null;
    // Unique WorkManager name for the daily reminder — reused so
    // enqueueUniquePeriodicWork() replaces rather than duplicates it.
    private static final String REMINDER_WORK_NAME = "klartext-daily-reminder";

    /**
     * Bridges the app's pronunciation button to Android's built-in
     * text-to-speech engine. This exists because standard Android WebView
     * (unlike Chrome for Android) does not implement the Web Speech API —
     * window.speechSynthesis is simply undefined inside a WebView — so the
     * in-page JavaScript alone could never make the sound buttons work on
     * a real device, even though it worked when previewed in a desktop
     * browser during development.
     */
    private class TTSBridge {
        @JavascriptInterface
        public boolean isAvailable() {
            return ttsReady;
        }

        @JavascriptInterface
        public void speak(String text) {
            if (!ttsReady || tts == null || text == null || text.trim().isEmpty()) return;
            HashMap<String, String> params = new HashMap<>();
            params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "klartext-utterance");
            tts.setSpeechRate(0.92f);
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, params);
        }

        /** Same as speak(), at a chosen speed (1.0 = normal) — used by the "slow" button. */
        @JavascriptInterface
        public void speakWithRate(String text, float rate) {
            if (!ttsReady || tts == null || text == null || text.trim().isEmpty()) return;
            HashMap<String, String> params = new HashMap<>();
            params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "klartext-utterance");
            tts.setSpeechRate(Math.max(0.3f, Math.min(rate, 2.0f)));
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, params);
        }

        @JavascriptInterface
        public void stop() {
            if (tts != null) tts.stop();
        }
    }

    /**
     * Bridges the Profile screen's daily-reminder toggle/time-picker (see
     * assets/index.html's reminderEnabled/reminderTime + applyReminderToNative())
     * to Android's notification permission + WorkManager, following the same
     * addJavascriptInterface pattern as TTSBridge above.
     *
     * The web layer owns the on/off + time preference (persisted via
     * window.storage); this bridge only ever mirrors that preference into a
     * native periodic work request — it holds no state of its own, so it's
     * safe to call schedule()/cancel() as often as the preference changes.
     */
    private class ReminderBridge {

        // Runtime permission is only ever requested from here — i.e. only
        // when the user explicitly flips the reminder toggle ON — never on
        // first app launch unprompted.
        @JavascriptInterface
        public void requestPermission() {
            if (Build.VERSION.SDK_INT < 33) return; // POST_NOTIFICATIONS only exists on API 33+
            if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                runOnUiThread(() -> ActivityCompat.requestPermissions(
                        MainActivity.this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        REQUEST_CODE_NOTIFICATIONS));
            }
        }

        // dueCount is a best-effort snapshot of computeDueForecast().dueReviews
        // at the moment this was called (app open, or the toggle/time
        // changing) — see the staleness note in ReminderWorker's class doc.
        // Pass -1 (or anything <= 0) for a generic fallback message.
        @JavascriptInterface
        public void schedule(int hour, int minute, int dueCount) {
            runOnUiThread(() -> {
                ReminderWorker.ensureChannel(MainActivity.this);

                Data inputData = new Data.Builder()
                        .putInt(ReminderWorker.KEY_DUE_COUNT, dueCount)
                        .putLong(ReminderWorker.KEY_SCHEDULED_AT, System.currentTimeMillis())
                        .build();

                PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                        ReminderWorker.class, 1, TimeUnit.DAYS)
                        .setInitialDelay(minutesUntilNext(hour, minute), TimeUnit.MINUTES)
                        .setInputData(inputData)
                        .build();

                // CANCEL_AND_REENQUEUE keeps a single reminder job in flight and
                // guarantees a changed reminder TIME takes effect right away
                // (UPDATE can keep the old next-run time). schedule() runs on
                // every app launch, which just re-aims the next run at the
                // chosen time of day.
                WorkManager.getInstance(MainActivity.this)
                        .enqueueUniquePeriodicWork(REMINDER_WORK_NAME, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, request);
            });
        }

        // Turns the "stay away" nudges on or off. The page calls this on every
        // launch with the current streak; `ask` is true only when the learner
        // has just switched it on, which is the only time permission is requested.
        @JavascriptInterface
        public void setComeback(boolean on, int streak, boolean ask) {
            SharedPreferences.Editor ed = getSharedPreferences(PREFS, MODE_PRIVATE).edit();
            ed.putBoolean(PREF_COMEBACK, on).putInt(PREF_STREAK, Math.max(0, streak)).apply();
            if (on && ask) requestPermission();
            if (!on) cancelComeback();
        }

        @JavascriptInterface
        public void cancel() {
            runOnUiThread(() ->
                    WorkManager.getInstance(MainActivity.this).cancelUniqueWork(REMINDER_WORK_NAME));
        }

        private long minutesUntilNext(int hour, int minute) {
            Calendar now = Calendar.getInstance();
            Calendar target = (Calendar) now.clone();
            target.set(Calendar.HOUR_OF_DAY, hour);
            target.set(Calendar.MINUTE, minute);
            target.set(Calendar.SECOND, 0);
            target.set(Calendar.MILLISECOND, 0);
            if (!target.after(now)) {
                target.add(Calendar.DAY_OF_YEAR, 1); // today's time already passed — start tomorrow
            }
            return Math.max(1, (target.getTimeInMillis() - now.getTimeInMillis()) / 60000);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Blocks screenshots and screen recording of this app at the OS
        // level (the system shows a black/blank frame instead) — this is
        // real, OS-enforced protection, unlike the WebView-side tweaks
        // below which are best-effort deterrents at most. It also hides
        // this app's content from the recent-apps task switcher preview.
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);

        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webview);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        // DOM storage (localStorage) is what keeps your Leitner progress saved
        // permanently on the device, across app closes and restarts. It is only
        // ever cleared when the app's own "Reset progress" button is pressed,
        // or if you manually clear this app's storage from Android Settings.
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        // Locks page text to the CSS sizes the app defines (including the
        // tablet breakpoints in index.html), so the OS-level "font size"
        // setting some tablets/skins (e.g. MIUI/HyperOS) apply by default
        // can't stretch or misalign the layout.
        settings.setTextZoom(100);
        // Make sure the WebView actually lays out using our
        // width=device-width viewport meta tag rather than any legacy
        // desktop-page heuristics, which some OEM WebView builds default to.
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);

        // Remote (Chrome DevTools) inspection of this WebView is off unless
        // explicitly enabled — being explicit here so a future edit can't
        // accidentally leave it on in a release build. This closes off the
        // easiest way to poke at the running page's JS/DOM/localStorage
        // from a connected computer.
        WebView.setWebContentsDebuggingEnabled(false);

        // Best-effort deterrent only (see decodeObfuscatedPack()'s comment
        // in index.html for why none of this is real protection against
        // someone willing to unzip the APK itself): disables long-press
        // text selection and the system context menu inside the WebView,
        // which otherwise makes it trivial to select-and-copy whole screens
        // of vocabulary out of the running app.
        webView.setLongClickable(false);
        webView.setOnLongClickListener(v -> true);
        webView.setHapticFeedbackEnabled(false);

        // Serves files from app/src/main/assets/ over a safe local https:// origin,
        // which is required for localStorage to persist reliably in a WebView.
        final WebViewAssetLoader assetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                // Only the fallback URL is actually served from local assets —
                // requests while loading REMOTE_URL pass through untouched and
                // go over the real network, same as any normal website.
                if (request.getUrl().toString().startsWith(LOCAL_FALLBACK_URL)
                        || request.getUrl().toString().startsWith("https://appassets.androidplatform.net/")) {
                    return assetLoader.shouldInterceptRequest(request.getUrl());
                }
                return null;
            }

            // Only the app's own pages may load as the MAIN page of this WebView
            // (they are the only ones that should ever see the AndroidTTS /
            // AndroidReminder / AndroidAuth bridges). Every other link, e.g.
            // "Open on Wiktionary", opens in the phone's normal browser instead.
            //
            // IMPORTANT: this must never touch sub-frames. Firebase sign-in
            // quietly loads a hidden frame from <project>.firebaseapp.com on
            // Android; blocking it (or throwing it out to the browser) makes
            // every sign-in / sign-up fail with a generic error.
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (!request.isForMainFrame()) {
                    return false;
                }
                Uri uri = request.getUrl();
                String host = uri.getHost();
                if (host != null && (host.equals(Uri.parse(REMOTE_URL).getHost())
                        || host.equals("appassets.androidplatform.net"))) {
                    return false;
                }
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                } catch (ActivityNotFoundException ignored) {
                    // No app can open this link — do nothing rather than navigate away.
                }
                return true;
            }

            // If the website answers with an error page (e.g. a missing site),
            // use the bundled offline copy instead of showing that error page.
            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
                if (request.isForMainFrame() && errorResponse != null
                        && errorResponse.getStatusCode() >= 400 && !triedRemoteFallback) {
                    triedRemoteFallback = true;
                    runOnUiThread(() -> webView.loadUrl(LOCAL_FALLBACK_URL));
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                // Only fall back on a failure to load the actual page itself
                // (the main frame) — a single failed sub-resource (e.g. one
                // blocked tracking pixel) should never trigger this; and only
                // fall back once, so a broken local copy can't loop forever.
                if (request.isForMainFrame() && !triedRemoteFallback) {
                    triedRemoteFallback = true;
                    runOnUiThread(() -> webView.loadUrl(LOCAL_FALLBACK_URL));
                }
            }
        });

        webView.addJavascriptInterface(new TTSBridge(), "AndroidTTS");
        webView.addJavascriptInterface(new ReminderBridge(), "AndroidReminder");
        webView.addJavascriptInterface(new AuthBridge(), "AndroidAuth");
        webView.addJavascriptInterface(new SpeechBridge(), "AndroidSpeech");

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS && tts != null) {
                int result = tts.setLanguage(Locale.GERMANY);
                ttsReady = result != TextToSpeech.LANG_MISSING_DATA
                        && result != TextToSpeech.LANG_NOT_SUPPORTED;
                tts.setSpeechRate(0.92f);
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String utteranceId) {
                        notifyJs("speaking");
                    }
                    @Override public void onDone(String utteranceId) {
                        notifyJs("done");
                    }
                    @Override public void onError(String utteranceId) {
                        notifyJs("error");
                    }
                });
            } else {
                ttsReady = false;
            }
        });

        webView.loadUrl(REMOTE_URL);

        // Makes the phone's back button navigate within the flashcard app
        // instead of immediately closing it — and, critically, always asks
        // the page's own JavaScript first (see window.__androidBackPressed
        // in index.html) before doing anything else. This is deliberately
        // NOT just "call webView.goBack() and hope its popstate event
        // reaches the page correctly" — the mid-Learn-session unsaved-
        // progress warning needs to fire reliably no matter how back was
        // triggered (3-button, gesture, or anything else), so JS always
        // gets first and final say on whether it's safe to actually leave.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                webView.evaluateJavascript(
                        "(function(){ try { return (window.__androidBackPressed ? window.__androidBackPressed() : 'not_handled'); } catch(e) { return 'not_handled'; } })();",
                        value -> {
                            String result = value != null ? value.replaceAll("^\"|\"$", "") : "not_handled";
                            if (!"handled".equals(result)) {
                                if (webView.canGoBack()) {
                                    webView.goBack();
                                } else {
                                    setEnabled(false);
                                    getOnBackPressedDispatcher().onBackPressed();
                                }
                            }
                        });
            }
        });
    }

    /**
     * Native Google sign-in. Google refuses to show its own sign-in page inside
     * a WebView, so the page asks the app to do it: the app shows the system
     * account chooser, receives a Google ID token, and hands it back to the
     * page, which exchanges it for a Firebase session (signInWithCredential).
     */
    private class AuthBridge {
        @JavascriptInterface
        public void signInWithGoogle() {
            runOnUiThread(() -> startGoogleSignIn());
        }
    }

    private void startGoogleSignIn() {
        final String clientId = getString(R.string.google_web_client_id);
        if (clientId == null || clientId.startsWith("PASTE_")) {
            notifyGoogle(null, "not-configured");
            return;
        }
        try {
            CredentialManager manager = CredentialManager.create(this);
            GetSignInWithGoogleOption option = new GetSignInWithGoogleOption.Builder(clientId).build();
            GetCredentialRequest request = new GetCredentialRequest.Builder()
                    .addCredentialOption(option)
                    .build();
            manager.getCredentialAsync(
                    this,
                    request,
                    new CancellationSignal(),
                    ContextCompat.getMainExecutor(this),
                    new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                        @Override
                        public void onResult(GetCredentialResponse result) {
                            Credential credential = result.getCredential();
                            if (credential instanceof CustomCredential
                                    && GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(credential.getType())) {
                                try {
                                    GoogleIdTokenCredential google =
                                            GoogleIdTokenCredential.createFrom(((CustomCredential) credential).getData());
                                    notifyGoogle(google.getIdToken(), null);
                                } catch (Exception e) {
                                    notifyGoogle(null, "parse-error");
                                }
                            } else {
                                notifyGoogle(null, "unexpected-credential");
                            }
                        }

                        @Override
                        public void onError(GetCredentialException e) {
                            if (e instanceof GetCredentialCancellationException) {
                                notifyGoogle(null, "cancelled");
                            } else if (e instanceof NoCredentialException) {
                                notifyGoogle(null, "no-account");
                            } else {
                                notifyGoogle(null, "failed:" + e.getType());
                            }
                        }
                    });
        } catch (Exception e) {
            notifyGoogle(null, "failed:" + e.getClass().getSimpleName());
        }
    }

    /**
     * Speaking exercise: lets the web page listen to the learner through
     * Android's own speech recognizer (WebView has no webkitSpeechRecognition).
     * The microphone permission is asked only when the learner taps the mic.
     * Results come back through window.__onNativeSpeech(jsonArrayOfGuesses, error).
     */
    private class SpeechBridge {
        @JavascriptInterface
        public boolean isAvailable() {
            return SpeechRecognizer.isRecognitionAvailable(MainActivity.this);
        }

        @JavascriptInterface
        public void start(final String lang) {
            runOnUiThread(() -> beginListening(lang));
        }

        @JavascriptInterface
        public void stop() {
            runOnUiThread(() -> {
                if (speech != null) { try { speech.stopListening(); } catch (Exception ignored) { } }
            });
        }
    }

    private void beginListening(String lang) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            pendingSpeechLang = lang;
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_CODE_MIC);
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { notifySpeech(null, "unavailable"); return; }
        try {
            if (speech != null) { speech.destroy(); speech = null; }
            speech = SpeechRecognizer.createSpeechRecognizer(this);
            speech.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle params) { notifySpeech(null, "ready"); }
                @Override public void onBeginningOfSpeech() { }
                @Override public void onRmsChanged(float rmsdB) { }
                @Override public void onBufferReceived(byte[] buffer) { }
                @Override public void onEndOfSpeech() { }
                @Override public void onPartialResults(Bundle partialResults) { }
                @Override public void onEvent(int eventType, Bundle params) { }
                @Override public void onError(int error) {
                    String code;
                    switch (error) {
                        case SpeechRecognizer.ERROR_NO_MATCH: code = "no-match"; break;
                        case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: code = "no-speech"; break;
                        case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: code = "permission"; break;
                        case SpeechRecognizer.ERROR_NETWORK:
                        case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: code = "network"; break;
                        default: code = "error:" + error;
                    }
                    notifySpeech(null, code);
                }
                @Override public void onResults(Bundle results) {
                    ArrayList<String> r = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    JSONArray arr = new JSONArray();
                    if (r != null) { for (String x : r) arr.put(x); }
                    notifySpeech(arr.toString(), arr.length() == 0 ? "no-match" : null);
                }
            });
            Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang == null ? "de-DE" : lang);
            i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
            i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
            speech.startListening(i);
        } catch (Exception e) {
            notifySpeech(null, "error:" + e.getClass().getSimpleName());
        }
    }

    /** Hands recognized guesses (JSON array text) or a status/error code to the page. */
    private void notifySpeech(String jsonArray, String code) {
        if (webView == null) return;
        final String js = "window.__onNativeSpeech && window.__onNativeSpeech("
                + (jsonArray == null ? "null" : jsonArray) + ","
                + (code == null ? "null" : JSONObject.quote(code)) + ");";
        runOnUiThread(() -> webView.evaluateJavascript(js, null));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_MIC) {
            boolean ok = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            String lang = pendingSpeechLang;
            pendingSpeechLang = null;
            if (ok) beginListening(lang); else notifySpeech(null, "permission");
        }
    }

    /** Hands the Google ID token (or an error code) back to the web page. */
    private void notifyGoogle(String idToken, String error) {
        if (webView == null) return;
        final String js = "window.__onNativeGoogle && window.__onNativeGoogle("
                + (idToken == null ? "null" : JSONObject.quote(idToken)) + ","
                + (error == null ? "null" : JSONObject.quote(error)) + ");";
        runOnUiThread(() -> webView.evaluateJavascript(js, null));
    }

    /** Reports TTS playback state back to the page so the speaker button's
        icon/animation stays in sync with what's actually being spoken. */
    private void notifyJs(String state) {
        if (webView == null) return;
        runOnUiThread(() -> webView.evaluateJavascript(
                "window.__onAndroidTtsEvent && window.__onAndroidTtsEvent('" + state + "');", null));
    }

    private void cancelComeback() {
        for (int d : COMEBACK_DAYS) {
            WorkManager.getInstance(this).cancelUniqueWork("klartext-comeback-" + d + "d");
        }
    }

    private void scheduleComeback() {
        if (!getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(PREF_COMEBACK, false)) return;
        ReminderWorker.ensureChannel(this);
        for (int d : COMEBACK_DAYS) {
            OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(ComebackWorker.class)
                    .setInitialDelay(d, TimeUnit.DAYS)
                    .setInputData(new Data.Builder().putInt(ComebackWorker.KEY_DAYS, d).build())
                    .build();
            WorkManager.getInstance(this).enqueueUniqueWork(
                    "klartext-comeback-" + d + "d", androidx.work.ExistingWorkPolicy.REPLACE, req);
        }
    }

    // Back on screen: the learner is here, so no "come back" nudge is due.
    @Override
    protected void onStart() {
        super.onStart();
        cancelComeback();
    }

    // Off screen: start the countdown to the nudges. onStart() cancels it.
    @Override
    protected void onStop() {
        super.onStop();
        scheduleComeback();
    }

    @Override
    protected void onDestroy() {
        if (speech != null) { speech.destroy(); speech = null; }
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        // Explicitly flush WebView's local storage to disk so progress is never lost.
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }
}

