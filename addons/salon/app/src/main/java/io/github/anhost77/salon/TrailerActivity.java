package io.github.anhost77.salon;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * A trailer, full screen, inside the app: TMDB finds the official trailer for the title (extra "title") or takes
 * the given YouTube key (extra "key"), and YouTube's embedded player plays it in a WebView, no YouTube app. Back
 * closes it. With no trailer found, it falls back to a YouTube search, as before.
 */
public final class TrailerActivity extends Activity {
    private final Handler main = new Handler(Looper.getMainLooper());
    private WebView web;
    private TextView status;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setDomStorageEnabled(true);
        web.setWebChromeClient(new WebChromeClient());
        web.setBackgroundColor(Color.BLACK);
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));
        status = new TextView(this);
        status.setTextColor(0xCCFFFFFF);
        status.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDisplayMetrics().heightPixels / 1080f * 34);
        status.setGravity(Gravity.CENTER);
        status.setText(R.string.trailer_loading);
        root.addView(status, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);

        String key = getIntent().getStringExtra("key");
        String title = getIntent().getStringExtra("title");
        if (key != null) {
            play(key);
            return;
        }
        new Thread(() -> {
            String found = null;
            try {
                int id = getIntent().getIntExtra("tmdb", 0);
                if (Tmdb.enabled() && id > 0) found = Tmdb.videoKey(getIntent().getBooleanExtra("tv", false) ? "tv" : "movie", id);
                if (found == null && Tmdb.enabled() && title != null) found = Tmdb.trailerKey(title);
            } catch (Exception offline) {
                // Falls back to the YouTube search below.
            }
            String k = found;
            main.post(() -> {
                if (isFinishing()) return;
                if (k != null) play(k);
                else youtubeSearch(title);
            });
        }, "trailer").start();
    }

    private void play(String key) {
        status.setVisibility(View.GONE);
        // YouTube's embedded player wants a real web origin: the page is served as if from themoviedb.org.
        String html = "<!doctype html><html><head><meta name='viewport' content='width=device-width'>"
                + "<style>html,body{margin:0;height:100%;background:#000;overflow:hidden}"
                + "iframe{border:0;width:100%;height:100%}</style></head><body>"
                + "<iframe src='https://www.youtube-nocookie.com/embed/" + key
                + "?autoplay=1&rel=0&modestbranding=1&playsinline=1&hl=fr&cc_load_policy=0' "
                + "allow='autoplay; encrypted-media; fullscreen'></iframe></body></html>";
        web.loadDataWithBaseURL("https://www.themoviedb.org", html, "text/html", "utf-8", null);
    }

    private void youtubeSearch(String title) {
        String q = (title == null ? "" : Tmdb.clean(title)) + " bande annonce";
        try {
            startActivity(new Intent(Intent.ACTION_SEARCH).setPackage("com.google.android.youtube.tv")
                    .putExtra("query", q).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException noApp) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(q))));
            } catch (RuntimeException nothing) {
                // Nothing can show it.
            }
        }
        finish();
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.loadUrl("about:blank");
            web.destroy();
        }
        super.onDestroy();
    }
}
