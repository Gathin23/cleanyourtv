package com.gathin.tvhome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Base64;
import android.view.KeyEvent;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.ComponentName;
import android.content.SharedPreferences;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Home screen drawn from assets/home.html (a port of the user's own design). Java only lists apps,
 * launches them and opens system screens; layout, focus and look all live in the HTML.
 */
public class WebHomeActivity extends Activity {

    private static final String LIVE_TV = "com.tcl.tv";

    private WebView web;
    /** Set by PlayerActivity before it closes ("next:..." or "ended:..."); read once by the page on resume. */
    static volatile String pendingOpen;
    private android.widget.FrameLayout root;
    private android.widget.VideoView intro;
    /** Set on a cold start and whenever the screen turns on; the next resume plays the intro. */
    private boolean introPending = true;
    private long screenOnAt;

    private final BroadcastReceiver screenOn = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            // Only when Home is what the TV wakes to; a later Home press must not replay it.
            screenOnAt = android.os.SystemClock.elapsedRealtime();
            if (hasWindowFocus()) playIntro();
        }
    };
    private final Map<String, String> bannerCache = new HashMap<>();

    private final BroadcastReceiver packagesChanged = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            bannerCache.clear();
            web.evaluateJavascript("window.reloadApps && reloadApps()", null);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        web.setBackgroundColor(0xFF2A2F6B);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        // localStorage: remembers the version (binge group) used per series.
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setTextZoom(100);
        s.setSupportZoom(false);
        web.setWebViewClient(new WebViewClient());
        // Page errors and console output go to logcat under "WebHome", for debugging over adb.
        web.setWebChromeClient(new android.webkit.WebChromeClient() {
            @Override
            public boolean onConsoleMessage(android.webkit.ConsoleMessage m) {
                android.util.Log.i("WebHome", m.messageLevel() + " " + m.sourceId() + ":" + m.lineNumber() + " " + m.message());
                return true;
            }
        });
        web.addJavascriptInterface(new Bridge(), "TV");
        web.setFocusable(true);
        root = new android.widget.FrameLayout(this);
        root.addView(web);
        setContentView(root);
        web.loadUrl("file:///android_asset/home.html");
        web.requestFocus();

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        registerReceiver(packagesChanged, f);
        registerReceiver(screenOn, new IntentFilter(Intent.ACTION_SCREEN_ON));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (introPending || android.os.SystemClock.elapsedRealtime() - screenOnAt < 8_000) playIntro();
        if (intro == null) web.requestFocus();
        web.evaluateJavascript("window.onHomeResume && onHomeResume()", null);
    }

    // A TV tem 2 GB: com a tela inicial escondida (jogo, filme, outro app) a página para de animar e de
    // rodar timers, e devolve memória quando o sistema pede.
    @Override
    protected void onStart() {
        super.onStart();
        web.onResume();
    }

    @Override
    protected void onStop() {
        web.onPause();
        super.onStop();
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= 10 && web != null) {   // 10 = TRIM_MEMORY_RUNNING_LOW (constante marcada como antiga)
            web.clearCache(false);          // só o cache em memória; o de disco (capas) fica
            android.util.Log.i("TvHome", "trim memory " + level);
        }
    }

    /** Test hook over adb: am start -n com.gathin.tvhome/.WebHomeActivity --es open "movies|detail:series:tt123". */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String open = intent.getStringExtra("open");
        if (open != null) {
            introPending = false;
            web.evaluateJavascript("window.debugOpen && debugOpen(" + JSONObject.quote(open) + ")", null);
        } else {
            // The Home key always lands on Início, wherever the launcher was left.
            web.evaluateJavascript("window.goHome && goHome()", null);
        }
    }

    /** Coming back from another app the WebView can lose key focus; take it back so the remote works. */
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && intro == null) web.requestFocus();
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(packagesChanged);
        unregisterReceiver(screenOn);
        web.destroy();
        super.onDestroy();
    }

    /** Home is the root: Back never leaves it. */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (intro != null) {
            if (event.getAction() == KeyEvent.ACTION_UP) endIntro();
            return true;
        }
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == KeyEvent.ACTION_UP) {
                web.evaluateJavascript("window.onBack && onBack()", null);
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    /** Intro video pushed to files/intro.mp4 (or .webm), shown full screen over Home, then faded out. */
    private void playIntro() {
        introPending = false;
        screenOnAt = 0;
        if (intro != null) return;
        java.io.File dir = getExternalFilesDir(null);
        java.io.File file = null;
        if (dir != null) {
            for (String n : new String[]{"intro.mp4", "intro.webm"}) {
                java.io.File c = new java.io.File(dir, n);
                if (c.isFile() && c.length() > 0) { file = c; break; }
            }
        }
        if (file == null) return;
        android.widget.FrameLayout box = new android.widget.FrameLayout(this);
        box.setBackgroundColor(0xFF000000);
        intro = new android.widget.VideoView(this);
        box.addView(intro, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT, android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.view.Gravity.CENTER));
        root.addView(box);
        intro.setTag(box);
        intro.setOnPreparedListener(mp -> {
            mp.setVolume(1f, 1f);
            intro.start();
        });
        intro.setOnCompletionListener(mp -> endIntro());
        intro.setOnErrorListener((mp, w, e) -> { endIntro(); return true; });
        intro.setVideoURI(android.net.Uri.fromFile(file));
        // Safety net: never leave Home covered if the player stalls.
        root.postDelayed(this::endIntro, 15_000);
    }

    private void endIntro() {
        if (intro == null) return;
        android.widget.VideoView v = intro;
        intro = null;
        android.view.View box = (android.view.View) v.getTag();
        box.animate().alpha(0f).setDuration(600).withEndAction(() -> {
            v.stopPlayback();
            root.removeView(box);
        }).start();
        web.requestFocus();
    }

    private void start(Intent i) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
        } catch (ActivityNotFoundException | SecurityException ignored) {
        }
    }

    private String bannerOf(ResolveInfo ri, PackageManager pm) {
        String pkg = ri.activityInfo.packageName;
        String cached = bannerCache.get(pkg);
        if (cached != null) return cached;
        Drawable d = ri.activityInfo.loadBanner(pm);
        if (d == null) {
            try {
                d = pm.getApplicationBanner(pkg);
            } catch (Exception ignored) {
            }
        }
        String uri = "";
        if (d != null) uri = toDataUri(d, 320, 180);
        bannerCache.put(pkg, uri);
        return uri;
    }

    private String iconOf(ResolveInfo ri, PackageManager pm) {
        Drawable d = ri.activityInfo.loadIcon(pm);
        return d == null ? "" : toDataUri(d, 96, 96);
    }

    private static String toDataUri(Drawable d, int w, int h) {
        Bitmap bmp;
        if (d instanceof BitmapDrawable && ((BitmapDrawable) d).getBitmap() != null) {
            Bitmap src = ((BitmapDrawable) d).getBitmap();
            bmp = Bitmap.createScaledBitmap(src, w, h, true);
        } else {
            bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bmp);
            d.setBounds(0, 0, w, h);
            d.draw(c);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
        return "data:image/png;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
    }

    /** Methods called from home.html as TV.name(). Runs on a WebView binder thread. */
    class Bridge {

        @JavascriptInterface
        public String version() {
            try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception e) { return ""; }
        }

        @JavascriptInterface
        public String apps() {
            PackageManager pm = getPackageManager();
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
            List<ResolveInfo> list = pm.queryIntentActivities(q, 0);
            Collections.sort(list, (a, b) -> a.loadLabel(pm).toString().compareToIgnoreCase(b.loadLabel(pm).toString()));
            JSONArray arr = new JSONArray();
            for (ResolveInfo ri : list) {
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(getPackageName()) || pkg.equals(LIVE_TV)) continue;
                try {
                    JSONObject o = new JSONObject();
                    o.put("pkg", pkg);
                    o.put("label", ri.loadLabel(pm).toString());
                    String banner = bannerOf(ri, pm);
                    o.put("banner", banner);
                    o.put("icon", banner.isEmpty() ? iconOf(ri, pm) : "");
                    arr.put(o);
                } catch (Exception ignored) {
                }
            }
            return arr.toString();
        }

        @JavascriptInterface
        public void launch(String pkg) {
            PackageManager pm = getPackageManager();
            Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (i == null) i = pm.getLaunchIntentForPackage(pkg);
            if (i == null) return;
            final Intent launch = i;
            runOnUiThread(() -> start(launch));
        }

        @JavascriptInterface
        public void liveTv() {
            launch(LIVE_TV);
        }

        @JavascriptInterface
        public void settings() {
            runOnUiThread(() -> start(new Intent(Settings.ACTION_SETTINGS)));
        }

        @JavascriptInterface
        public void wifiSettings() {
            runOnUiThread(() -> start(new Intent(Settings.ACTION_WIFI_SETTINGS)));
        }

        @JavascriptInterface
        public void inputs() {
            // Google TV's input picker; falls back to Live TV, which owns HDMI on TCL.
            Intent i = new Intent("com.android.tv.action.VIEW_INPUTS");
            if (getPackageManager().resolveActivity(i, 0) != null) {
                runOnUiThread(() -> start(i));
            } else {
                liveTv();
            }
        }

        @JavascriptInterface
        public void search() {
            runOnUiThread(() -> start(new Intent(Intent.ACTION_ASSIST)));
        }

        /**
         * What to resume: the most recent media session that has a title (playing or paused), remembered
         * across restarts; otherwise the most recently used app. "" when nothing is known.
         */
        @JavascriptInterface
        public String continueWatching() {
            try {
                MediaSessionManager msm = (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
                for (MediaController mc : msm.getActiveSessions(new ComponentName(WebHomeActivity.this, MediaListener.class))) {
                    Recents.record(WebHomeActivity.this, mc);
                }
            } catch (SecurityException ignored) {
                // Listener not enabled: use the stored history or the last used app.
            }
            try {
                JSONArray hist = Recents.list(WebHomeActivity.this);
                if (hist.length() > 0) {
                    JSONObject o = hist.getJSONObject(0);
                    o.put("kind", "media");
                    return o.toString();
                }
                UsageStatsManager usm = (UsageStatsManager) getSystemService(USAGE_STATS_SERVICE);
                long now = System.currentTimeMillis();
                List<UsageStats> stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, now - 7L * 86_400_000L, now);
                UsageStats best = null;
                PackageManager pm = getPackageManager();
                for (UsageStats u : stats) {
                    String p = u.getPackageName();
                    if (p.equals(getPackageName()) || p.equals(LIVE_TV) || u.getTotalTimeInForeground() < 30_000) continue;
                    if (pm.getLeanbackLaunchIntentForPackage(p) == null) continue;
                    if (p.startsWith("com.android.") || p.startsWith("com.tcl.") || p.equals("com.android.vending")) continue;
                    if (best == null || u.getLastTimeUsed() > best.getLastTimeUsed()) best = u;
                }
                if (best == null) return "";
                JSONObject o = new JSONObject();
                o.put("kind", "app");
                o.put("pkg", best.getPackageName());
                return o.toString();
            } catch (Exception e) {
                return "";
            }
        }

        /**
         * GET a URL off the UI thread and hand the body to the page as onFetch(id, ok, text). Native so
         * add-ons that send no CORS headers still answer.
         */
        @JavascriptInterface
        public void fetch(String id, String url) {
            new Thread(() -> {
                boolean ok = false;
                String body = "";
                // The TV's resolver drops lookups under load; give each host a few tries before giving up.
                for (int attempt = 0; attempt < 3; attempt++) {
                    try {
                        java.net.InetAddress.getByName(new java.net.URL(url).getHost());
                        break;
                    } catch (java.net.UnknownHostException e) {
                        try { Thread.sleep(1500L * (attempt + 1)); } catch (InterruptedException ignored) { }
                    } catch (Exception e) {
                        break;
                    }
                }
                try {
                    java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
                    c.setConnectTimeout(10_000);
                    c.setReadTimeout(20_000);
                    c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/120 Safari/537.36");
                    c.setInstanceFollowRedirects(true);
                    int code = c.getResponseCode();
                    java.io.InputStream in = code < 400 ? c.getInputStream() : c.getErrorStream();
                    StringBuilder sb = new StringBuilder();
                    if (in != null) {
                        try (java.io.Reader r = new java.io.InputStreamReader(in, "UTF-8")) {
                            char[] buf = new char[8192];
                            int n;
                            while ((n = r.read(buf)) > 0 && sb.length() < 4_000_000) sb.append(buf, 0, n);
                        }
                    }
                    body = sb.toString();
                    ok = code < 400;
                    if (!ok) android.util.Log.w("WebHome", "fetch " + code + " " + url);
                } catch (Exception e) {
                    body = String.valueOf(e.getMessage());
                    android.util.Log.w("WebHome", "fetch failed " + e + " " + url);
                }
                String js = "window.onFetch && onFetch(" + JSONObject.quote(id) + "," + ok + "," + JSONObject.quote(body) + ")";
                runOnUiThread(() -> web.evaluateJavascript(js, null));
            }, "fetch").start();
        }

        /**
         * Is this direct link alive? Asks for the first bytes (following redirects, also to plain http)
         * and answers onProbe(id, ok, ms). ok = 200/206 with a video-ish or unknown content type.
         */
        @JavascriptInterface
        public void probe(String id, String url, String headersJson) {
            new Thread(() -> {
                long t0 = android.os.SystemClock.elapsedRealtime();
                boolean ok = false;
                try {
                    String u = url;
                    for (int hop = 0; hop < 6 && !ok; hop++) {
                        java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(u).openConnection();
                        c.setInstanceFollowRedirects(false);
                        c.setConnectTimeout(6_000);
                        c.setReadTimeout(6_000);
                        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/120 Safari/537.36");
                        c.setRequestProperty("Range", "bytes=0-2047");
                        if (headersJson != null && !headersJson.isEmpty()) {
                            JSONObject h = new JSONObject(headersJson);
                            for (java.util.Iterator<String> it = h.keys(); it.hasNext(); ) {
                                String k = it.next();
                                c.setRequestProperty(k, h.optString(k));
                            }
                        }
                        int code = c.getResponseCode();
                        if (code >= 300 && code < 400 && c.getHeaderField("Location") != null) {
                            u = new java.net.URL(new java.net.URL(u), c.getHeaderField("Location")).toString();
                            c.disconnect();
                            continue;
                        }
                        String type = String.valueOf(c.getContentType()).toLowerCase();
                        boolean typeOk = type.contains("video") || type.contains("octet") || type.contains("mpegurl")
                                || type.contains("matroska") || type.equals("null") || type.contains("binary");
                        int read = 0;
                        if ((code == 200 || code == 206) && typeOk) {
                            try (java.io.InputStream in = c.getInputStream()) {
                                byte[] b = new byte[2048];
                                int n;
                                while (read < 512 && (n = in.read(b)) > 0) read += n;
                            }
                        }
                        ok = read > 0;
                        c.disconnect();
                        break;
                    }
                } catch (Exception ignored) {
                }
                long ms = android.os.SystemClock.elapsedRealtime() - t0;
                String js = "window.onProbe && onProbe(" + JSONObject.quote(id) + "," + ok + "," + ms + ")";
                runOnUiThread(() -> web.evaluateJavascript(js, null));
            }, "probe").start();
        }

        /** Stream add-ons copied from the Stremio account: files/addons.json, "[]" when missing. */
        @JavascriptInterface
        public String addons() {
            try {
                java.io.File f = new java.io.File(getExternalFilesDir(null), "addons.json");
                if (!f.isFile()) return "[]";
                return new String(java.nio.file.Files.readAllBytes(f.toPath()), "UTF-8");
            } catch (Exception e) {
                return "[]";
            }
        }

        /** Minha lista: séries e filmes marcados para assistir, como JSON [{type,id,name,poster,genres,at}]. */
        @JavascriptInterface
        public String myList() {
            return getSharedPreferences("mylist", MODE_PRIVATE).getString("items", "[]");
        }

        @JavascriptInterface
        public void saveMyList(String json) {
            getSharedPreferences("mylist", MODE_PRIVATE).edit().putString("items", json).apply();
        }

        /** A file the PC agent pushes into files/ (games.json, game_status.json); "" when missing. */
        @JavascriptInterface
        public String pcFile(String name) {
            if (!name.matches("[a-z_]+\\.json")) return "";
            try {
                java.io.File f = new java.io.File(getExternalFilesDir(null), name);
                if (!f.isFile()) return "";
                return new String(java.nio.file.Files.readAllBytes(f.toPath()), "UTF-8");
            } catch (Exception e) {
                return "";
            }
        }

        /** Ask the PC agent for something (launch a game, give the screen back). It reads this tag from logcat over ADB. */
        @JavascriptInterface
        public void gameRequest(String json) {
            android.util.Log.i("GameReq", json);
        }

        /**
         * Play one stream in Stremio's own player: stremio:///player/{stream}/{streamAddon}/{metaAddon}/{type}/{id}/{videoId},
         * where {stream} is the stream JSON, zlib-compressed and base64-encoded (Stremio's deep-link format).
         */
        /**
         * Play a direct link in the built-in player. p: {url, title, sub, art, key, headers, stream, addon,
         * meta, type, id, vid}; the stream fields build the Stremio link used if the TV cannot play it.
         * A saved item from the history passes {url, ..., fallback} instead.
         */
        @JavascriptInterface
        public boolean playDirect(String json) {
            try {
                JSONObject p = new JSONObject(json);
                String fallback = p.optString("fallback");
                if (fallback.isEmpty() && p.has("stream")) {
                    fallback = stremioPlayerUri(p.optString("stream"), p.optString("addon"), p.optString("meta"),
                            p.optString("type"), p.optString("id"), p.optString("vid"));
                }
                Intent i = new Intent(WebHomeActivity.this, PlayerActivity.class)
                        .putExtra("url", p.optString("url"))
                        .putExtra("title", p.optString("title"))
                        .putExtra("sub", p.optString("sub"))
                        .putExtra("art", p.optString("art"))
                        .putExtra("key", p.optString("key"))
                        .putExtra("headers", p.optString("headers"))
                        .putExtra("fallback", fallback)
                        .putExtra("subs", p.optString("subs"))
                        .putExtra("dub", p.optBoolean("dub"))
                        .putExtra("mtype", p.optString("type"))
                        .putExtra("mid", p.optString("id"))
                        .putExtra("vid", p.optString("vid"))
                        .putExtra("skips", p.optString("skips"));
                runOnUiThread(() -> startActivity(i));
                return true;
            } catch (Exception e) {
                android.util.Log.w("WebHome", "playDirect " + e);
                return false;
            }
        }

        private String stremioPlayerUri(String streamJson, String streamAddon, String metaAddon, String type, String id, String videoId) throws Exception {
            java.util.zip.Deflater d = new java.util.zip.Deflater();
            d.setInput(streamJson.getBytes("UTF-8"));
            d.finish();
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            while (!d.finished()) out.write(buf, 0, d.deflate(buf));
            d.end();
            String enc = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
            return "stremio:///player/" + pct(enc) + "/" + pct(streamAddon) + "/" + pct(metaAddon)
                    + "/" + pct(type) + "/" + pct(id) + "/" + pct(videoId);
        }

        @JavascriptInterface
        public boolean playInStremio(String streamJson, String streamAddon, String metaAddon, String type, String id, String videoId) {
            try {
                java.util.zip.Deflater d = new java.util.zip.Deflater();
                d.setInput(streamJson.getBytes("UTF-8"));
                d.finish();
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                while (!d.finished()) out.write(buf, 0, d.deflate(buf));
                d.end();
                String enc = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
                String uri = "stremio:///player/" + pct(enc) + "/" + pct(streamAddon) + "/" + pct(metaAddon)
                        + "/" + pct(type) + "/" + pct(id) + "/" + pct(videoId);
                return openUri(uri, "com.stremio.one");
            } catch (Exception e) {
                android.util.Log.w("WebHome", "playInStremio " + e);
                return false;
            }
        }

        private String pct(String s) throws java.io.UnsupportedEncodingException {
            return java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20");
        }

        /** Subtitle add-ons copied from the Stremio account: files/subtitles.json, "[]" when missing. */
        @JavascriptInterface
        public String subtitleAddons() {
            try {
                java.io.File f = new java.io.File(getExternalFilesDir(null), "subtitles.json");
                if (!f.isFile()) return "[]";
                return new String(java.nio.file.Files.readAllBytes(f.toPath()), "UTF-8");
            } catch (Exception e) {
                return "[]";
            }
        }

        /** Open a link (magnet:, stremio:, http:) in one app; true if that app took it. */
        @JavascriptInterface
        public boolean openUri(String uri, String pkg) {
            Intent i = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(uri));
            if (pkg != null && !pkg.isEmpty()) i.setPackage(pkg);
            if (getPackageManager().resolveActivity(i, 0) == null) return false;
            runOnUiThread(() -> start(i));
            return true;
        }

        /** What the player asked for when it closed ("next:type:id:vid", "ended:type:id:vid"), once. */
        @JavascriptInterface
        public String pendingOpen() {
            String p = WebHomeActivity.pendingOpen;
            WebHomeActivity.pendingOpen = null;
            return p == null ? "" : p;
        }

        /** Watched or in-progress items for the side list, newest first (the hero shows the first). */
        @JavascriptInterface
        public String recents() {
            return Recents.list(WebHomeActivity.this).toString();
        }

        @JavascriptInterface
        public String watched() {
            return Recents.watched(WebHomeActivity.this).toString();
        }

        @JavascriptInterface
        public void markWatched(String vid) {
            Recents.markWatched(WebHomeActivity.this, vid);
        }

        @JavascriptInterface
        public String progress() {
            return Recents.progress(WebHomeActivity.this).toString();
        }

        @JavascriptInterface
        public void unmarkWatched(String vid) {
            Recents.unmarkWatched(WebHomeActivity.this, vid);
        }

        @JavascriptInterface
        public String wifiName() {
            try {
                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
                WifiInfo info = wm.getConnectionInfo();
                String ssid = info == null ? null : info.getSSID();
                if (ssid == null || ssid.contains("unknown")) return "";
                return ssid.replace("\"", "");
            } catch (Exception e) {
                return "";
            }
        }
    }
}
