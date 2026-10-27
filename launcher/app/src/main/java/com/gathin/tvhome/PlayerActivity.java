package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.Tracks;
import androidx.media3.common.text.Cue;
import androidx.media3.common.text.CueGroup;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Built-in player for direct-link sources from Filmes e Séries. The video plays in ExoPlayer on the TV's
 * hardware decoder; the controls and the subtitles are drawn by assets/player.html in a transparent
 * WebView on top, in the same glass design as Home. Add-on subtitles are downloaded and parsed here
 * (SRT/VTT) so the page can shift them in time; subtitles inside the video come through onCues.
 *
 * Torrents never come here (they go to Stremio). If a link will not play here, the same source is
 * handed to Stremio through the "fallback" deep link instead of leaving a spinner.
 *
 * Extras: url, title, sub, art, key, headers (JSON object), fallback (stremio:/// URI), subs (JSON array).
 */
public class PlayerActivity extends Activity {

    private static final long SAVE_EVERY_MS = 10_000;
    private static final long STATE_EVERY_MS = 250;

    private ExoPlayer player;
    private WebView ui;
    private String url, title, sub, art, key, headersJson, fallback, subsJson;
    private volatile String tracksJson = "{\"audio\":[],\"text\":[]}";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean handedOff, triedHls, uiReady, dub;
    private String mtype, mid, vid, skipsJson;

    private final Runnable saver = new Runnable() {
        @Override
        public void run() {
            savePosition();
            handler.postDelayed(this, SAVE_EVERY_MS);
        }
    };

    /** Pushes position, duration and play state to the page four times a second. */
    private final Runnable stateTick = new Runnable() {
        @Override
        public void run() {
            if (player != null && uiReady) {
                long dur = player.getDuration();
                String js = "window.onState&&onState(" + player.getCurrentPosition() + "," + (dur < 0 ? 0 : dur) + ","
                        + player.getBufferedPosition() + "," + player.isPlaying() + ","
                        + (player.getPlaybackState() == Player.STATE_BUFFERING) + ")";
                ui.evaluateJavascript(js, null);
            }
            handler.postDelayed(this, STATE_EVERY_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        Intent in = getIntent();
        url = in.getStringExtra("url");
        title = str(in.getStringExtra("title"));
        sub = str(in.getStringExtra("sub"));
        art = str(in.getStringExtra("art"));
        key = in.getStringExtra("key");
        if (key == null || key.isEmpty()) key = url;
        headersJson = str(in.getStringExtra("headers"));
        fallback = str(in.getStringExtra("fallback"));
        subsJson = str(in.getStringExtra("subs"));
        dub = in.getBooleanExtra("dub", false);
        mtype = str(in.getStringExtra("mtype"));
        mid = str(in.getStringExtra("mid"));
        vid = str(in.getStringExtra("vid"));
        skipsJson = str(in.getStringExtra("skips"));
        if (url == null || url.isEmpty()) { finish(); return; }

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        PlayerView video = new PlayerView(this);
        video.setUseController(false);
        video.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        if (video.getSubtitleView() != null) video.getSubtitleView().setVisibility(View.GONE);
        root.addView(video, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        ui = new WebView(this);
        ui.setBackgroundColor(Color.TRANSPARENT);
        WebSettings s = ui.getSettings();
        s.setJavaScriptEnabled(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setTextZoom(100);
        ui.addJavascriptInterface(new Bridge(), "P");
        ui.setWebChromeClient(new android.webkit.WebChromeClient() {
            @Override
            public boolean onConsoleMessage(android.webkit.ConsoleMessage m) {
                android.util.Log.i("WebHome", "player " + m.messageLevel() + " " + m.lineNumber() + " " + m.message());
                return true;
            }
        });
        ui.setWebViewClient(new android.webkit.WebViewClient() {
            @Override
            public void onPageFinished(WebView v, String u) {
                uiReady = true;
            }
        });
        root.addView(ui, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);
        ui.loadUrl("file:///android_asset/player.html");
        ui.requestFocus();

        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setUserAgent("Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/120 Safari/537.36")
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15_000)
                .setReadTimeoutMs(30_000)
                .setDefaultRequestProperties(headers());
        DefaultRenderersFactory renderers = new DefaultRenderersFactory(this).setEnableDecoderFallback(true);
        player = new ExoPlayer.Builder(this, renderers)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(new DefaultDataSource.Factory(this, http)))
                .build();
        video.setPlayer(player);
        // Subtitles inside the video stay off until picked in the page; add-on subtitles are drawn by the page.
        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .setPreferredAudioLanguages("pt-BR", "pt")
                .build());

        MediaItem.Builder item = new MediaItem.Builder().setUri(Uri.parse(url))
                .setMediaMetadata(new MediaMetadata.Builder().setTitle(title).setArtist(sub).build());
        if (url.contains(".m3u8")) item.setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8);
        player.setMediaItem(item.build());

        long resume = prefs().getLong("pos:" + key, 0);
        if (resume > 30_000) player.seekTo(resume);
        player.addListener(new Player.Listener() {
            @Override
            public void onPlayerError(@NonNull PlaybackException error) {
                int code = error.errorCode;
                android.util.Log.w("WebHome", "player error " + error.getErrorCodeName() + " " + error.getMessage());
                // Network side (dead link, 403/404, server down): Stremio would fail the same way.
                boolean network = code >= 2000 && code < 3000 && code != PlaybackException.ERROR_CODE_IO_UNSPECIFIED;
                if (network) { deadLink(error.getErrorCodeName()); return; }
                // Many add-on links end, after redirects, in an HLS playlist with no .m3u8 in the URL.
                if (!triedHls && !url.contains(".m3u8") && code >= 3000 && code < 4000) {
                    triedHls = true;
                    android.util.Log.i("WebHome", "player retry as HLS after " + error.getErrorCodeName());
                    long at = player.getCurrentPosition();
                    player.setMediaItem(new MediaItem.Builder().setUri(Uri.parse(url))
                            .setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8)
                            .setMediaMetadata(new MediaMetadata.Builder().setTitle(title).setArtist(sub).build()).build(), at);
                    player.prepare();
                    player.play();
                    return;
                }
                handOff(error.getErrorCodeName());
            }

            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_ENDED) {
                    savePosition();
                    // Série: o próximo episódio; filme: a tela de recomendações.
                    if (!mid.isEmpty()) WebHomeActivity.pendingOpen = ("series".equals(mtype) ? "next:" : "ended:") + mtype + ":" + mid + ":" + vid;
                    finish();
                }
            }

            @Override
            public void onTracksChanged(@NonNull Tracks tracks) {
                // Video but no playable audio (DTS/TrueHD on this TV): better in Stremio than silent here.
                boolean hasAudio = false, audioOk = false;
                for (Tracks.Group g : tracks.getGroups()) {
                    if (g.getType() != C.TRACK_TYPE_AUDIO) continue;
                    hasAudio = true;
                    for (int i = 0; i < g.length; i++) if (g.isTrackSupported(i)) audioOk = true;
                }
                if (hasAudio && !audioOk) { handOff("AUDIO_UNSUPPORTED"); return; }
                boolean hasVideo = false;
                for (Tracks.Group g : tracks.getGroups()) if (g.getType() == C.TRACK_TYPE_VIDEO) hasVideo = true;
                if (hasVideo && !hasAudio) { noAudio(); return; }
                tracksJson = describeTracks(tracks);
                if (uiReady) ui.evaluateJavascript("window.onTracks&&onTracks()", null);
            }

            @Override
            public void onCues(@NonNull CueGroup cues) {
                // Subtitles inside the video (when picked): hand the text to the page, which draws all subtitles.
                StringBuilder sb = new StringBuilder();
                for (Cue c : cues.cues) {
                    if (c.text == null) continue;
                    if (sb.length() > 0) sb.append('\n');
                    sb.append(c.text);
                }
                if (uiReady) ui.evaluateJavascript("window.onCue&&onCue(" + JSONObject.quote(sb.toString()) + ")", null);
            }
        });
        player.prepare();
        player.play();
        handler.postDelayed(saver, SAVE_EVERY_MS);
        handler.post(stateTick);
    }

    /** Audio and in-video subtitle tracks for the page's menus. */
    private String describeTracks(Tracks tracks) {
        JSONObject o = new JSONObject();
        JSONArray audio = new JSONArray(), text = new JSONArray();
        try {
            int gi = 0;
            for (Tracks.Group g : tracks.getGroups()) {
                for (int i = 0; i < g.length; i++) {
                    if (!g.isTrackSupported(i)) continue;
                    Format f = g.getTrackFormat(i);
                    JSONObject t = new JSONObject();
                    t.put("g", gi);
                    t.put("i", i);
                    t.put("lang", f.language == null ? "" : f.language);
                    t.put("label", f.label == null ? "" : f.label);
                    t.put("sel", g.isTrackSelected(i));
                    if (g.getType() == C.TRACK_TYPE_AUDIO) {
                        t.put("ch", f.channelCount);
                        audio.put(t);
                    } else if (g.getType() == C.TRACK_TYPE_TEXT) {
                        text.put(t);
                    }
                }
                gi++;
            }
            o.put("audio", audio);
            o.put("text", text);
        } catch (Exception ignored) {
        }
        return o.toString();
    }

    private void selectTrack(int type, int group, int index) {
        Tracks t = player.getCurrentTracks();
        if (group < 0 || group >= t.getGroups().size()) return;
        Tracks.Group g = t.getGroups().get(group);
        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                .setTrackTypeDisabled(type, false)
                .setOverrideForType(new TrackSelectionOverride(g.getMediaTrackGroup(), index))
                .build());
    }

    /** Methods for assets/player.html as P.name(). */
    class Bridge {
        @JavascriptInterface
        public String info() {
            try {
                JSONObject o = new JSONObject();
                o.put("title", title);
                o.put("sub", sub);
                o.put("subs", subsJson.isEmpty() ? new JSONArray() : new JSONArray(subsJson));
                o.put("offset", prefs().getLong("off:" + key, 0));
                o.put("scale", (double) prefs().getFloat("sc:" + key, 1f));
                o.put("dub", dub);
                o.put("series", "series".equals(mtype));
                o.put("skips", skipsJson.isEmpty() ? new JSONArray() : new JSONArray(skipsJson));
                return o.toString();
            } catch (Exception e) {
                return "{}";
            }
        }

        @JavascriptInterface
        public String tracks() {
            return tracksJson;
        }

        @JavascriptInterface
        public void toggle() {
            runOnUiThread(() -> { if (player.isPlaying()) player.pause(); else player.play(); });
        }

        @JavascriptInterface
        public void seekBy(long ms) {
            runOnUiThread(() -> {
                long dur = player.getDuration();
                long to = Math.max(0, player.getCurrentPosition() + ms);
                if (dur > 0) to = Math.min(to, dur - 1000);
                player.seekTo(to);
            });
        }

        @JavascriptInterface
        public void selectAudio(int group, int index) {
            runOnUiThread(() -> selectTrack(C.TRACK_TYPE_AUDIO, group, index));
        }

        /** A subtitle track inside the video; -1 turns them off (add-on subtitles are drawn by the page). */
        @JavascriptInterface
        public void selectText(int group, int index) {
            runOnUiThread(() -> {
                if (group < 0) {
                    player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build());
                } else {
                    selectTrack(C.TRACK_TYPE_TEXT, group, index);
                }
            });
        }

        /** Download and parse an add-on subtitle; answers onSubLoaded(index, [[startMs, endMs, text]...]) or null. */
        @JavascriptInterface
        public void loadSub(int index, String subUrl) {
            new Thread(() -> {
                String cues = "null";
                try {
                    if (subUrl.startsWith("translate:")) {
                        cues = cachedTranslation(subUrl.substring(10));
                    } else {
                        cues = parseSubtitle(download(subUrl)).toString();
                    }
                } catch (Exception e) {
                    android.util.Log.w("WebHome", "subtitle " + e + " " + subUrl);
                }
                String js = "window.onSubLoaded&&onSubLoaded(" + index + "," + cues + ")";
                runOnUiThread(() -> ui.evaluateJavascript(js, null));
            }, "subtitle").start();
        }

        @JavascriptInterface
        public void saveOffset(long ms) {
            prefs().edit().putLong("off:" + key, ms).apply();
        }

        /** Speed correction for subtitles made for a different frame rate (1 = none). */
        @JavascriptInterface
        public void saveScale(double scale) {
            prefs().edit().putFloat("sc:" + key, (float) scale).apply();
        }

        /**
         * Turn on a subtitle track inside the video only to time the add-on subtitle against it; the page
         * keeps drawing the add-on subtitle. Returns false when the video has no subtitle track.
         */
        @JavascriptInterface
        public boolean refText(int group, int index) {
            runOnUiThread(() -> selectTrack(C.TRACK_TYPE_TEXT, group, index));
            return true;
        }

        /** "Próximo episódio": close and let Home play the next one with the same source. */
        @JavascriptInterface
        public void next() {
            if (!mid.isEmpty()) WebHomeActivity.pendingOpen = "next:" + mtype + ":" + mid + ":" + vid;
            runOnUiThread(() -> { savePosition(); finish(); });
        }

        @JavascriptInterface
        public void seekTo(long ms) {
            runOnUiThread(() -> player.seekTo(ms));
        }

        @JavascriptInterface
        public void close() {
            runOnUiThread(PlayerActivity.this::finish);
        }
    }

    // ---------- subtitles ----------

    /**
     * Our own PT-BR subtitle when no add-on has one: translate an English subtitle with Google's free
     * endpoint, one q per cue so the answer comes back as a list in the same order. A chunk that fails
     * stays in English.
     */
    /**
     * Translated subtitles are kept on the TV so the second time is instant and Google is asked less;
     * the folder is capped at 30 MB, dropping the least recently used first.
     */
    private String cachedTranslation(String srcUrl) throws Exception {
        java.io.File dir = new java.io.File(getFilesDir(), "subcache");
        dir.mkdirs();
        java.io.File f = new java.io.File(dir, Integer.toHexString(srcUrl.hashCode()) + ".json");
        if (f.length() > 0) {
            f.setLastModified(System.currentTimeMillis());
            android.util.Log.i("WebHome", "subtitle cache hit " + f.getName());
            return new String(java.nio.file.Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        }
        JSONArray cues = translateCues(parseSubtitle(download(srcUrl)));
        String json = cues.toString();
        if (cues.length() > 0 && translatedOk) {
            java.nio.file.Files.write(f.toPath(), json.getBytes(StandardCharsets.UTF_8));
            trimCache(dir, 30L * 1024 * 1024);
        }
        return json;
    }

    private static void trimCache(java.io.File dir, long max) {
        java.io.File[] all = dir.listFiles();
        if (all == null) return;
        java.util.Arrays.sort(all, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        long total = 0;
        for (java.io.File x : all) {
            total += x.length();
            if (total > max) x.delete();
        }
    }

    /** False when a chunk stayed in English, so a half-translated subtitle is not kept. */
    private static volatile boolean translatedOk;

    private static JSONArray translateCues(JSONArray cues) throws Exception {
        int i = 0, failed = 0;
        translatedOk = true;
        while (i < cues.length()) {
            StringBuilder q = new StringBuilder();
            int start = i;
            while (i < cues.length() && q.length() < 4000) {
                q.append("&q=").append(java.net.URLEncoder.encode(cues.getJSONArray(i).getString(2), "UTF-8"));
                i++;
            }
            try {
                JSONArray out = new JSONArray(translate(q.substring(1)));
                for (int k = 0; k < out.length() && start + k < i; k++) {
                    Object t = out.get(k);
                    if (t instanceof JSONArray) t = ((JSONArray) t).opt(0);   // [text, lang] with sl=auto
                    if (t instanceof String) cues.getJSONArray(start + k).put(2, t);
                }
            } catch (Exception e) {
                android.util.Log.w("WebHome", "translate " + e);
                translatedOk = false;
                if (++failed > 2) break;
            }
        }
        return cues;
    }

    private static String translate(String form) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(
                "https://translate.googleapis.com/translate_a/t?client=gtx&sl=en&tl=pt").openConnection();
        c.setConnectTimeout(10_000);
        c.setReadTimeout(20_000);
        c.setDoOutput(true);
        c.setInstanceFollowRedirects(false);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124 Safari/537.36");
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8");
        c.getOutputStream().write(form.getBytes(StandardCharsets.UTF_8));
        if (c.getResponseCode() != 200) throw new Exception("HTTP " + c.getResponseCode());
        try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        }
    }

    private static String download(String u) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setConnectTimeout(10_000);
        c.setReadTimeout(20_000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/120 Safari/537.36");
        int code = c.getResponseCode();
        // Follow a redirect across hosts/protocols, which HttpURLConnection will not do by itself.
        if (code >= 300 && code < 400 && c.getHeaderField("Location") != null) {
            return download(new URL(new URL(u), c.getHeaderField("Location")).toString());
        }
        if (code >= 400) throw new Exception("HTTP " + code);
        byte[] bytes;
        try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0 && out.size() < 5_000_000) out.write(buf, 0, n);
            bytes = out.toByteArray();
        }
        // gzip files (some OpenSubtitles links) start with 1f 8b.
        if (bytes.length > 2 && (bytes[0] & 0xff) == 0x1f && (bytes[1] & 0xff) == 0x8b) {
            try (java.util.zip.GZIPInputStream gz = new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(bytes));
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[16384];
                int n;
                while ((n = gz.read(buf)) > 0) out.write(buf, 0, n);
                bytes = out.toByteArray();
            }
        }
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            // Older Brazilian subtitles are often Windows-1252.
            return new String(bytes, "windows-1252");
        }
    }

    private static final Pattern TIME = Pattern.compile(
            "((?:\\d+:)?\\d{1,2}:\\d{2}[.,]\\d{1,3})\\s*-->\\s*((?:\\d+:)?\\d{1,2}:\\d{2}[.,]\\d{1,3})");

    /** SRT and WebVTT into [[startMs, endMs, html]]; keeps i/b/u tags, drops the rest. */
    static JSONArray parseSubtitle(String text) {
        JSONArray out = new JSONArray();
        String[] lines = text.replace("﻿", "").replace("\r\n", "\n").replace('\r', '\n').split("\n");
        for (int i = 0; i < lines.length; i++) {
            Matcher m = TIME.matcher(lines[i]);
            if (!m.find()) continue;
            long start = ms(m.group(1)), end = ms(m.group(2));
            StringBuilder sb = new StringBuilder();
            int j = i + 1;
            for (; j < lines.length && !lines[j].trim().isEmpty(); j++) {
                if (TIME.matcher(lines[j]).find()) break;
                if (sb.length() > 0) sb.append("<br>");
                sb.append(clean(lines[j]));
            }
            i = j - 1;
            String body = sb.toString().trim();
            if (body.isEmpty() || end <= start) continue;
            JSONArray cue = new JSONArray();
            cue.put(start);
            cue.put(end);
            cue.put(body);
            out.put(cue);
        }
        return out;
    }

    private static String clean(String line) {
        String s = line.replaceAll("\\{\\\\[^}]*\\}", "")               // {\an8} and other ASS overrides
                .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        // Put back the simple formatting tags.
        return s.replaceAll("&lt;(/?)([ibu])&gt;", "<$1$2>").replaceAll("&lt;/?(font|c|v)[^&]*&gt;", "");
    }

    private static long ms(String t) {
        String[] p = t.replace(',', '.').split(":");
        double secs = 0;
        for (String part : p) secs = secs * 60 + Double.parseDouble(part);
        return Math.round(secs * 1000);
    }

    // ---------- lifecycle ----------

    /** Link is down: say so and go back to the source list (another app would not do better). */
    private void deadLink(String why) {
        if (handedOff) return;
        handedOff = true;
        android.util.Log.w("WebHome", "player dead link " + why + " " + url);
        Toast.makeText(this, "Esse link está fora do ar. Escolha outra fonte.", Toast.LENGTH_LONG).show();
        finish();
    }

    /** Video track with no audio at all (some add-ons send picture-only files). */
    private void noAudio() {
        if (handedOff) return;
        handedOff = true;
        android.util.Log.w("WebHome", "player no audio " + url);
        Toast.makeText(this, "Essa fonte veio sem áudio. Escolha outra.", Toast.LENGTH_LONG).show();
        finish();
    }

    /** This link will not play here: give the same source to Stremio, or say so and close. */
    private void handOff(String why) {
        if (handedOff) return;
        handedOff = true;
        android.util.Log.w("WebHome", "player handoff " + why + " " + url);
        if (!fallback.isEmpty()) {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(fallback)).setPackage("com.stremio.one");
            if (getPackageManager().resolveActivity(i, 0) != null) {
                Toast.makeText(this, "Este formato não toca no player da TV. Abrindo no Stremio…", Toast.LENGTH_LONG).show();
                startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                finish();
                return;
            }
        }
        Toast.makeText(this, "Não foi possível tocar esta fonte. Tente outra.", Toast.LENGTH_LONG).show();
        finish();
    }

    private void savePosition() {
        if (player == null || handedOff) return;
        long pos = player.getCurrentPosition(), dur = player.getDuration();
        // Clips under two minutes are add-on notices or trailers, not something to resume.
        if (dur < 120_000 || pos < 5_000) return;
        boolean done = pos > dur - 120_000;
        prefs().edit().putLong("pos:" + key, done ? 0 : pos).apply();
        if (done) Recents.markWatched(this, vid.isEmpty() ? mid : vid);
        Recents.saveProgress(this, vid.isEmpty() ? mid : vid, pos, dur, done);
        try {
            JSONObject play = new JSONObject();
            play.put("url", url);
            play.put("title", title);
            play.put("sub", sub);
            play.put("art", art);
            play.put("key", key);
            play.put("headers", headersJson);
            play.put("fallback", fallback);
            play.put("subs", subsJson);
            play.put("dub", dub);
            play.put("type", mtype);
            play.put("id", mid);
            play.put("vid", vid);
            play.put("skips", skipsJson);
            Recents.recordDirect(this, key, title, sub, art, done ? dur : pos, dur, play.toString());
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        savePosition();
        if (player != null) player.pause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(saver);
        handler.removeCallbacks(stateTick);
        if (player != null) {
            player.release();
            player = null;
        }
        if (ui != null) ui.destroy();
        super.onDestroy();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int k = event.getKeyCode();
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            switch (k) {
                case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                    if (player.isPlaying()) player.pause(); else player.play();
                    return true;
                case KeyEvent.KEYCODE_MEDIA_PLAY:
                    player.play();
                    return true;
                case KeyEvent.KEYCODE_MEDIA_PAUSE:
                    player.pause();
                    return true;
                case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                    player.seekTo(player.getCurrentPosition() + 30_000);
                    return true;
                case KeyEvent.KEYCODE_MEDIA_REWIND:
                    player.seekTo(Math.max(0, player.getCurrentPosition() - 10_000));
                    return true;
                default:
            }
        }
        if (k == KeyEvent.KEYCODE_BACK) {
            // The page closes its menus first and calls P.close() when there is nothing left to close.
            if (event.getAction() == KeyEvent.ACTION_UP) {
                if (uiReady) ui.evaluateJavascript("window.onBack?onBack():P.close()", null); else finish();
            }
            return true;
        }
        if (!ui.hasFocus()) ui.requestFocus();
        return super.dispatchKeyEvent(event);
    }

    private Map<String, String> headers() {
        Map<String, String> m = new HashMap<>();
        try {
            if (headersJson.isEmpty()) return m;
            JSONObject o = new JSONObject(headersJson);
            for (Iterator<String> it = o.keys(); it.hasNext(); ) {
                String k = it.next();
                m.put(k, o.optString(k));
            }
        } catch (Exception ignored) {
        }
        return m;
    }

    private SharedPreferences prefs() {
        return getSharedPreferences("player", MODE_PRIVATE);
    }

    private static String str(String s) {
        return s == null ? "" : s;
    }
}
