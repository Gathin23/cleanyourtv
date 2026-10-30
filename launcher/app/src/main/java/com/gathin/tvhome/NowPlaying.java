package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * now_playing: a bar under the clock with what an app plays right now, live from its MediaSession, laid out like
 * Android TV's own now-playing card: the album art (or the app's icon), moving equalizer bars while it plays and a
 * pause sign while paused, the title over the artist and app, and previous, next and stop buttons beside it. OK on
 * the bar pauses and resumes. Below the dock it folds into a pill in the top bar. Every active session is followed,
 * so an app that starts playing after Home opened shows up too; nothing is polled, and it all stops while Home is in
 * the background. Reading other apps' sessions needs the GuardRevival notification listener allowed; without it the
 * bar stays hidden. One class for the listeners, rather than a lambda each, to keep the APK small.
 */
final class NowPlaying extends MediaController.Callback implements MediaSessionManager.OnActiveSessionsChangedListener,
        View.OnClickListener, View.OnKeyListener, ViewTreeObserver.OnGlobalLayoutListener {

    private final MainActivity home;
    private final View bar, controls;
    private final TextView mini, title, sub;
    private final View[] row;
    private final MediaSessionManager sessions;
    private final ComponentName listener;
    private final List<MediaController> watched = new ArrayList<>();
    private final Lead lead, miniLead;
    /** Browsing the apps below the dock: the pill in the top bar instead of the bar. */
    private boolean compact;
    private MediaController current;
    /** The session Stop paused (it had no real stop): hidden until it plays again. */
    private Object dismissed;
    /** The dismissed session has been seen paused: only a play after that brings it back. */
    private boolean dismissedPaused;
    /** When the bar row last took focus: the key that brought it there can arrive twice through a key helper. */
    private long focusedAt;
    /** A layout is awaited after the bar came or went; true when the bar had focus. */
    private boolean relayoutFocused;

    NowPlaying(MainActivity home) {
        this.home = home;
        bar = home.findViewById(R.id.btnNowPlaying);
        controls = home.findViewById(R.id.nowControls);
        mini = home.findViewById(R.id.btnNowMini);
        // The bar holds the lead image and a column with title and subtitle; the controls hold previous, next, stop.
        android.view.ViewGroup b = (android.view.ViewGroup) bar, text = (android.view.ViewGroup) b.getChildAt(1),
                c = (android.view.ViewGroup) controls;
        title = (TextView) text.getChildAt(0);
        sub = (TextView) text.getChildAt(1);
        row = new View[]{bar, c.getChildAt(0), c.getChildAt(1), c.getChildAt(2)};
        float density = home.getResources().getDisplayMetrics().density;
        lead = new Lead(title.getTextColors(), density, 44, 16, 4, 17);
        // The pill in the top bar: no art, finer bars.
        miniLead = new Lead(mini.getTextColors(), density, 0, 0, 3, 14);
        ((ImageView) b.getChildAt(0)).setImageDrawable(lead);
        // The pill style tints compound drawables in the text colour.
        mini.setCompoundDrawableTintList(null);
        sessions = home.getSystemService(MediaSessionManager.class);
        listener = new ComponentName(home, GuardRevival.class);
        mini.setOnClickListener(this);
        for (View v : row) {
            v.setOnClickListener(this);
            v.setOnKeyListener(this);
        }
        // Focusable only while Up from the dock has put focus on it: Home that opens with nothing focused would hand
        // focus to the first focusable view, and that was the bar.
        release();
    }

    @Override public void onClick(View v) {
        if (current == null) return;
        MediaController.TransportControls t = current.getTransportControls();
        if (v == row[1]) t.skipToPrevious();
        else if (v == row[2]) t.skipToNext();
        else if (v == row[3]) {
            // Spotify offers no stop (its state lacks ACTION_STOP) and ignores one: pause such an app and hide it.
            PlaybackState p = current.getPlaybackState();
            if (p != null && (p.getActions() & PlaybackState.ACTION_STOP) != 0) t.stop();
            else {
                t.pause();
                dismissed = current.getSessionToken();
                dismissedPaused = false;
                pick();
            }
        } else if (state(current) == PlaybackState.STATE_PLAYING) t.pause();
        else t.play();
    }

    @Override public boolean onKey(View v, int code, KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
        if (event.getEventTime() - focusedAt < 250) return true;
        int i = 0;
        while (row[i] != v) i++;
        if (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN) {
            release();
            home.nowPlayingFocus(code == KeyEvent.KEYCODE_DPAD_UP ? 1 : 0);
        } else if (code == KeyEvent.KEYCODE_DPAD_RIGHT && i < 3) row[i + 1].requestFocus();
        else if (code == KeyEvent.KEYCODE_DPAD_LEFT && i > 0) row[i - 1].requestFocus();
        else return code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT;
        return true;
    }

    /** Up from the dock: the bar takes focus, when it is showing. */
    boolean takeFocus() {
        if (bar.getVisibility() != View.VISIBLE) return false;
        for (View v : row) v.setFocusable(true);
        focusedAt = SystemClock.uptimeMillis();
        return bar.requestFocus();
    }

    boolean hasFocus() {
        return bar.hasFocus() || controls.hasFocus();
    }

    private void release() {
        for (View v : row) v.setFocusable(false);
    }

    void setCompact(boolean c) {
        if (compact == c) return;
        compact = c;
        pick();
    }

    void start() {
        try {
            sessions.addOnActiveSessionsChangedListener(this, listener);
        } catch (SecurityException e) {
            return;
        }
        refresh();
    }

    void stop() {
        sessions.removeOnActiveSessionsChangedListener(this);
        onActiveSessionsChanged(null);
    }

    private void refresh() {
        try {
            onActiveSessionsChanged(sessions.getActiveSessions(listener));
        } catch (SecurityException e) {
            onActiveSessionsChanged(null);
        }
    }

    /** Follows every session: any change in one picks again. */
    @Override public void onActiveSessionsChanged(List<MediaController> list) {
        for (MediaController c : watched) c.unregisterCallback(this);
        watched.clear();
        if (list != null) {
            for (MediaController c : list) {
                c.registerCallback(this);
                watched.add(c);
            }
        }
        pick();
    }

    @Override public void onPlaybackStateChanged(PlaybackState state) { pick(); }
    @Override public void onMetadataChanged(MediaMetadata metadata) { pick(); }
    @Override public void onSessionDestroyed() { refresh(); }

    /** The playing session, else a paused one, else none. */
    private void pick() {
        MediaController pick = null;
        for (MediaController c : watched) {
            int s = state(c);
            if (c.getSessionToken().equals(dismissed)) {
                // Right after Stop the app still reports playing for a moment: wait for the pause first.
                if (s != PlaybackState.STATE_PLAYING) dismissedPaused = true;
                if (s != PlaybackState.STATE_PLAYING || !dismissedPaused) continue;
                dismissed = null;
            }
            if (s == PlaybackState.STATE_PLAYING) { pick = c; break; }
            if (pick == null && s == PlaybackState.STATE_PAUSED) pick = c;
        }
        current = pick;
        boolean wasGone = bar.getVisibility() == View.GONE, focused = hasFocus();
        if (pick == null) {
            lead.playing = miniLead.playing = false;
            if (mini.hasFocus()) home.nowPlayingFocus(1);
            bar.setVisibility(View.GONE);
            controls.setVisibility(View.GONE);
            mini.setVisibility(View.GONE);
            release();
            if (!wasGone) afterLayout(focused);
            return;
        }
        MediaMetadata m = pick.getMetadata();
        String text = lines(m == null ? null : m.getString(MediaMetadata.METADATA_KEY_TITLE),
                m == null ? null : m.getString(MediaMetadata.METADATA_KEY_ARTIST), appLabel(pick.getPackageName()));
        int br = text.indexOf('\n');
        Lead show = compact ? miniLead : lead;
        (compact ? lead : miniLead).playing = false;
        show.playing = state(pick) == PlaybackState.STATE_PLAYING;
        show.setArt(art(m, pick.getPackageName()));
        // Titles scroll as a marquee, which runs while a view is selected and restarts on every setText: some apps
        // report their playback state many times a second, so the text is only set when it changes.
        if (compact) {
            set(mini, text.replace("\n", "  ·  "));
            mini.setCompoundDrawablesRelative(miniLead, null, null, null);
        } else {
            set(title, br > 0 ? text.substring(0, br) : text);
            set(sub, br > 0 ? text.substring(br + 1) : "");
        }
        show.invalidateSelf();
        // Invisible, not gone, while compact: the header keeps its height, so the grid does not jump under the focus.
        bar.setVisibility(compact ? View.INVISIBLE : View.VISIBLE);
        controls.setVisibility(compact ? View.INVISIBLE : View.VISIBLE);
        mini.setVisibility(compact ? View.VISIBLE : View.GONE);
        if (wasGone) afterLayout(false);
    }

    /**
     * The header grows or shrinks with the bar and the grid moves: once that layout is done, refocus the dock tile so
     * the glass highlight and the big app name follow it instead of staying at the old place. Only when focus is on
     * the dock or was on the bar; focus in the top bar stays where it is.
     */
    private void afterLayout(boolean wasFocused) {
        relayoutFocused = wasFocused;
        bar.getViewTreeObserver().addOnGlobalLayoutListener(this);
    }

    @Override public void onGlobalLayout() {
        bar.getViewTreeObserver().removeOnGlobalLayoutListener(this);
        if (relayoutFocused || home.nowPlayingFocus(2)) home.nowPlayingFocus(0);
    }

    private static void set(TextView v, String text) {
        if (!text.contentEquals(v.getText())) v.setText(text);
        v.setSelected(true);
    }

    /** Two lines, as Android TV's card has them: the title, then the artist and the app; the app alone without a title. */
    static String lines(String title, String artist, String app) {
        if (title == null || title.trim().isEmpty()) return app;
        if (artist == null || artist.trim().isEmpty()) return title.trim() + "\n" + app;
        return title.trim() + "\n" + artist.trim() + "  ·  " + app;
    }

    /** The album art when the session has one, else the app's own icon. */
    private Drawable art(MediaMetadata m, String pkg) {
        Bitmap b = m == null ? null : m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (b == null && m != null) b = m.getBitmap(MediaMetadata.METADATA_KEY_ART);
        if (b != null) return new BitmapDrawable(home.getResources(), b);
        try {
            return home.getPackageManager().getApplicationIcon(pkg);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    private String appLabel(String pkg) {
        PackageManager pm = home.getPackageManager();
        try {
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    /** Buffering, connecting and skipping between tracks count as playing, so the bar holds still through Next. */
    private static int state(MediaController c) {
        PlaybackState p = c.getPlaybackState();
        int s = p == null ? PlaybackState.STATE_NONE : p.getState();
        return s == PlaybackState.STATE_BUFFERING || s >= PlaybackState.STATE_CONNECTING ? PlaybackState.STATE_PLAYING : s;
    }

    /**
     * The bar's start: the art, then three equalizer bars that bounce while playing, or a pause sign while paused,
     * in the bar's text colour (dark on the focused white bar). It is its own frame callback.
     */
    private static final class Lead extends Drawable implements Runnable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final ColorStateList colors;
        private final int artSize;
        private final float gap, w, h;
        private final Path round = new Path();
        private Drawable art;
        boolean playing;

        /**
         * @param gapDp the space between the art and the bars; the layout gives the bars the same space to the text
         * @param barDp the width of a bar, and four fifths of it between bars; heightDp the bars' full height
         */
        Lead(ColorStateList colors, float density, int artDp, int gapDp, int barDp, int heightDp) {
            this.colors = colors;
            artSize = Math.round(artDp * density);
            gap = gapDp * density;
            w = barDp * density;
            h = heightDp * density;
            // The art, the gap, then the three bars: nothing trails, so the layout's gap is the bars-to-text gap.
            setBounds(0, 0, Math.round(artSize + gap + 4.6f * w), Math.round(Math.max(artSize, h)));
            // 12dp at 44dp: the bar's 21dp corner less its 9dp inset, so the two curves run parallel.
            round.addRoundRect(0, 0, artSize, artSize, artSize * 0.27f, artSize * 0.27f, Path.Direction.CW);
        }

        void setArt(Drawable d) {
            art = d;
            if (d != null) d.setBounds(0, 0, artSize, artSize);
        }

        @Override public void draw(Canvas c) {
            if (art != null && artSize > 0) {
                c.save();
                c.clipPath(round);
                art.draw(c);
                c.restore();
            }
            paint.setColor(colors.getColorForState(getState(), 0xFFFFFFFF));
            float x = artSize + gap, bottom = (getBounds().height() + h) / 2;
            // Paused: the first two of the same bars, full height, so play and pause share one spacing.
            double t = SystemClock.uptimeMillis() / 160.0;
            for (int i = 0; i < (playing ? 3 : 2); i++, x += 1.8f * w) {
                float level = playing ? (float) (0.3 + 0.7 * Math.abs(Math.sin(t + i * 1.3))) : 1;
                c.drawRoundRect(x, bottom - h * level, x + w, bottom, w / 2, w / 2, paint);
            }
            if (playing) scheduleSelf(this, SystemClock.uptimeMillis() + 60);
        }

        @Override public void run() { invalidateSelf(); }
        @Override public int getIntrinsicWidth() { return getBounds().width(); }
        @Override public int getIntrinsicHeight() { return getBounds().height(); }
        @Override public boolean isStateful() { return true; }
        @Override protected boolean onStateChange(int[] state) { invalidateSelf(); return true; }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
