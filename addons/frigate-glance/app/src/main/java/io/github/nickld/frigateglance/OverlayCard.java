package io.github.nickld.frigateglance;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.os.Handler;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The card on screen: a window over every app that never takes focus or touches, so the remote keeps
 * controlling what is playing. Called from the service's thread; does its view work on the main thread.
 */
final class OverlayCard implements CardView {
    /** Where the picture comes from; the service answers from Frigate. */
    interface Source {
        byte[] snapshot(String camera) throws IOException;

        /** Width over height of the camera's frame, so boxes line up; 16/9 when unknown. */
        float aspect(String camera);

        /** RTSP address for live video, or null when live video is off. */
        String liveUrl(String camera);

        /** True while Frigate presents a certificate other than the trusted one. */
        boolean newCertificate();
    }

    /** Told when a card appears and leaves, so the key service filters keys only meanwhile. */
    interface ShowingListener {
        void onShowing(boolean showing);
    }

    static final int WIDTH_DP = 320;
    static final int MAX_HEIGHT_DP = 180;
    private static final int MARGIN_DP = 24;
    static final long SNAPSHOT_REFRESH_MS = 1000;

    private final Context context;
    private final WindowManager wm;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService loader = Executors.newSingleThreadExecutor();
    private final Source source;
    private final ShowingListener showingListener;
    private final float dp;

    private GlanceConfig config;
    private LinearLayout root;
    private FrameLayout picture;
    /** Over an empty picture: why there is no frame yet. */
    private TextView problem;
    private ImageView image;
    private TextureView video;
    private LiveVideo live;
    private boolean refreshing;
    private int refreshLoop;
    private BoxView boxes;
    /** Peek's timer bar: red and full while the event goes on, white and shrinking once it has ended. */
    private View timerBar;
    private ModeBadge badge;
    private boolean countingDown;
    private TextView info;
    /** One label per action ("◀ Dismiss", "Hold ▶ Snooze 1h"), so a hold can draw its pill around just one. */
    private LinearLayout footer;
    private final java.util.Map<Integer, TextView> actionLabels = new java.util.HashMap<>();
    private String footerText = "";
    private android.animation.ValueAnimator holdAnim;
    private volatile String reviewId;
    private boolean snapshotLogged;

    OverlayCard(Context context, GlanceConfig config, Source source, ShowingListener showingListener) {
        this.context = context;
        this.wm = context.getSystemService(WindowManager.class);
        this.config = config;
        this.source = source;
        this.showingListener = showingListener;
        this.dp = context.getResources().getDisplayMetrics().density;
    }

    void setConfig(GlanceConfig c) {
        config = c;
    }

    /** The last card shown, for the settings screen's Overview. */
    static volatile String lastTitle = "";
    static volatile long lastShownAt;

    @Override
    public void show(CardModel m) {
        reviewId = m.reviewId;
        lastTitle = m.title + (m.detail.isEmpty() ? "" : " · " + m.detail);
        lastShownAt = System.currentTimeMillis();
        main.post(() -> {
            boolean fresh = root == null;
            if (fresh) build();
            fill(m, true);
            if (fresh) {
                try {
                    wm.addView(root, params());
                } catch (RuntimeException e) {
                    Log.w(ConfigStore.TAG, "card not shown (display over other apps not allowed?): " + e.getMessage());
                    root = null;
                    return;
                }
                slideIn();
                showingListener.onShowing(true);
            }
        });
        loadSnapshot(m.reviewId, m.camera);
        main.post(() -> startPicture(m.reviewId, m.camera));
    }

    /** Live video when it is on and starts; otherwise a snapshot refreshed every second. */
    private void startPicture(String id, String camera) {
        if (root == null || !id.equals(reviewId)) return;
        stopPicture();
        String url = source.liveUrl(camera);
        // Something already playing holds the TV's video decoder; a second stream fails there and makes the
        // playing app stall for a moment (TCL 65Q650G with YouTube). Snapshots then, live video over menus.
        AudioManager audio = context.getSystemService(AudioManager.class);
        if (url != null && audio != null && audio.isMusicActive()) {
            Log.i(ConfigStore.TAG, "media playing; snapshots instead of live video");
            url = null;
        }
        if (url == null) {
            refresh(id, camera);
            return;
        }
        live = new LiveVideo(context, new LiveVideo.Listener() {
            @Override
            public void onPlaying() {
                if (badge != null) badge.setLive(true);
                showProblem(null);
                if (video != null) video.animate().alpha(1f).setDuration(300).start();
            }

            @Override
            public void onGaveUp() {
                if (badge != null) badge.setLive(false);
                if (video != null) video.setAlpha(0f);
                refresh(id, camera);
            }
        });
        live.start(url, video);
    }

    /**
     * A new snapshot a second after the last one arrived (or failed), never more than one in flight, so a slow
     * Frigate is not buried in requests. One loop per card: a newer loop or stopPicture ends the old one.
     */
    private void refresh(String id, String camera) {
        refreshing = true;
        int loop = ++refreshLoop;
        main.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!refreshing || loop != refreshLoop || root == null || !id.equals(reviewId)) return;
                Runnable again = this;
                loadSnapshot(id, camera, () -> main.postDelayed(again, SNAPSHOT_REFRESH_MS));
            }
        }, SNAPSHOT_REFRESH_MS);
    }

    private void stopPicture() {
        refreshing = false;
        refreshLoop++;
        if (live != null) {
            live.release();
            live = null;
        }
        if (video != null) video.setAlpha(0f);
        if (badge != null) badge.setLive(false);
    }

    @Override
    public void update(CardModel m) {
        main.post(() -> {
            if (root != null && m.reviewId.equals(reviewId)) fill(m, false);
        });
    }

    /** A hold key went down: a pill outline draws itself around its action over HOLD_MS, green once closed. */
    void showHold(int action) {
        main.post(() -> {
            clearHold();
            TextView label = actionLabels.get(action);
            if (label == null || root == null) return;
            PillProgress pill = new PillProgress(Math.max(1f, 1.5f * dp * scale()));
            label.setBackground(pill);
            holdAnim = android.animation.ValueAnimator.ofFloat(0f, 1f).setDuration(KeyRules.HOLD_MS);
            holdAnim.setInterpolator(new LinearInterpolator());
            holdAnim.addUpdateListener(a -> pill.setProgress((float) a.getAnimatedValue()));
            holdAnim.start();
        });
    }

    /** The hold key came up: the pill goes. */
    void endHold() {
        main.post(this::clearHold);
    }

    private void clearHold() {
        if (holdAnim != null) holdAnim.cancel();
        holdAnim = null;
        for (TextView t : actionLabels.values()) t.setBackground(null);
    }

    @Override
    public void hide() {
        reviewId = null;
        main.post(() -> {
            if (root == null) return;
            View gone = root;
            stopPicture();
            root = null;
            video = null;
            showingListener.onShowing(false);
            gone.animate().translationX(slideDistance()).alpha(0f).setDuration(200)
                    .withEndAction(() -> removeQuietly(gone)).start();
        });
    }

    private void build() {
        root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundResource(R.drawable.card_glass);
        int pad = px(8);
        root.setPadding(pad, pad, pad, pad);

        // Camera and what was seen above the picture, the two buttons in a small line under it.
        info = text(13, 0xFFC9D1D9, Typeface.DEFAULT);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(-1, -2);
        ip.bottomMargin = px(5);
        root.addView(info, ip);

        picture = new FrameLayout(context);
        picture.setBackgroundResource(R.drawable.picture_frame);
        picture.setClipToOutline(true);
        image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.FIT_XY);
        video = new TextureView(context);
        video.setAlpha(0f);
        boxes = new BoxView(context);
        picture.addView(image, new FrameLayout.LayoutParams(-1, -1));
        picture.addView(video, new FrameLayout.LayoutParams(-1, -1));
        picture.addView(boxes, new FrameLayout.LayoutParams(-1, -1));
        problem = text(12, 0xFFFFFFFF, Typeface.DEFAULT);
        problem.setSingleLine(false);
        problem.setMaxLines(4);
        problem.setGravity(Gravity.CENTER);
        problem.setPadding(px(20), 0, px(20), 0);
        problem.setVisibility(View.GONE);
        picture.addView(problem, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));
        timerBar = new View(context);
        timerBar.setPivotX(0f);
        picture.addView(timerBar, new FrameLayout.LayoutParams(-1, px(3), Gravity.TOP));
        badge = new ModeBadge(context, 14 * scale());
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        bp.setMargins(0, px(8), px(6), 0);
        picture.addView(badge, bp);
        root.addView(picture, new LinearLayout.LayoutParams(px(WIDTH_DP), px(WIDTH_DP * 9 / 16)));

        footer = new LinearLayout(context);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footerText = "";
        actionLabels.clear();
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(-1, -2);
        fp.topMargin = px(4);
        root.addView(footer, fp);
    }

    private void fill(CardModel m, boolean newCard) {
        if (newCard) {
            // At most 320 dp wide and 180 dp tall (a 16:9 frame fills both), in the camera's own shape so boxes line up.
            sizePicture(source.aspect(m.camera));
            image.setImageDrawable(null);
            showProblem(null);
            countingDown = false;
        }
        SpannableStringBuilder t = new SpannableStringBuilder(m.title);
        t.setSpan(new StyleSpan(Typeface.BOLD), 0, t.length(), 0);
        t.setSpan(new ForegroundColorSpan(0xFFFFFFFF), 0, t.length(), 0);
        if (!m.detail.isEmpty()) t.append("  ").append(m.detail);
        info.setText(t);
        java.util.Map<Integer, String> parts = CardText.parts(config.dismissKey, config.snoozeKey, config.snoozeMinutes,
                config.fullscreen ? config.fullscreenKey : null);
        String text = String.join(" · ", parts.values());
        if (!text.equals(footerText)) {
            footerText = text;
            footer.removeAllViews();
            actionLabels.clear();
            int padH = px(5), padV = px(2);
            for (java.util.Map.Entry<Integer, String> e : parts.entrySet()) {
                if (footer.getChildCount() > 0) footer.addView(text(10, 0xFF8B949E, Typeface.DEFAULT));
                TextView label = text(10, 0xFF8B949E, Typeface.DEFAULT);
                label.setText(e.getValue());
                label.setPadding(padH, padV, padH, padV);
                footer.addView(label);
                actionLabels.put(e.getKey(), label);
            }
            for (int k = 0; k < footer.getChildCount(); k++) {
                TextView sep = (TextView) footer.getChildAt(k);
                if (!actionLabels.containsValue(sep)) sep.setText("·");
            }
        }
        boxes.setBoxes(config.boxes ? m.boxes : null);
        updateTimerBar(m.countdownMs);
    }

    private void updateTimerBar(long countdownMs) {
        if (countdownMs <= 0) {
            countingDown = false;
            timerBar.animate().cancel();
            timerBar.setScaleX(1f);
            timerBar.setBackgroundColor(0xD9DC3C3C);
            return;
        }
        if (countingDown) return;
        countingDown = true;
        timerBar.setBackgroundColor(0xBFFFFFFF);
        timerBar.setScaleX(1f);
        timerBar.animate().scaleX(0f).setDuration(countdownMs).setInterpolator(new LinearInterpolator()).start();
    }

    private void sizePicture(float aspect) {
        int h = Math.min(px(MAX_HEIGHT_DP), Math.round(px(WIDTH_DP) / aspect));
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) picture.getLayoutParams();
        int w = Math.round(h * aspect);
        if (lp.height == h && lp.width == w) return;
        lp.height = h;
        lp.width = w;
        picture.setLayoutParams(lp);
    }

    void loadSnapshot(String id, String camera) {
        loadSnapshot(id, camera, null);
    }

    private void loadSnapshot(String id, String camera, Runnable done) {
        loader.execute(() -> {
            if (!id.equals(reviewId)) return;   // the card it was for has gone
            try {
                byte[] jpeg = source.snapshot(camera);
                Bitmap bmp = decode(jpeg);
                main.post(() -> {
                    if (root == null || !id.equals(reviewId) || bmp == null) return;
                    // The frame's own shape, in case Frigate's camera sizes were not known when the card opened.
                    sizePicture((float) bmp.getWidth() / bmp.getHeight());
                    image.setImageBitmap(bmp);
                    showProblem(null);
                });
            } catch (IOException | RuntimeException e) {
                if (!snapshotLogged) Log.w(ConfigStore.TAG, "frigate error: snapshot " + camera + ": " + e.getMessage());
                snapshotLogged = true;
                String why = CardText.noPicture(source.newCertificate(), e.getMessage());
                main.post(() -> {
                    // A frame from a moment ago beats a message; only an empty picture says why it is empty.
                    if (root != null && id.equals(reviewId) && image.getDrawable() == null) showProblem(why);
                });
            } finally {
                if (done != null) main.post(done);
            }
        });
    }

    /** null hides the message. */
    private void showProblem(String why) {
        if (problem == null) return;
        problem.setText(why);
        problem.setVisibility(why == null ? View.GONE : View.VISIBLE);
    }

    /** Current review id on screen, or null. */
    String reviewId() {
        return reviewId;
    }

    private Bitmap decode(byte[] jpeg) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length, o);
        int sample = 1;
        while (o.outWidth / (sample * 2) >= px(WIDTH_DP)) sample *= 2;
        BitmapFactory.Options d = new BitmapFactory.Options();
        d.inSampleSize = sample;
        d.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length, d);
    }

    private WindowManager.LayoutParams params() {
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        p.gravity = gravity(config.corner);
        p.x = Math.round(MARGIN_DP * dp);   // the gap from the screen edge does not grow with the card
        p.y = Math.round(MARGIN_DP * dp);
        p.setTitle("Frigate Glance");
        return p;
    }

    static int gravity(String corner) {
        switch (corner) {
            case "top-left": return Gravity.TOP | Gravity.LEFT;
            case "bottom-left": return Gravity.BOTTOM | Gravity.LEFT;
            case "bottom-right": return Gravity.BOTTOM | Gravity.RIGHT;
            default: return Gravity.TOP | Gravity.RIGHT;
        }
    }

    private void slideIn() {
        root.setTranslationX(slideDistance());
        root.setAlpha(0f);
        root.animate().translationX(0f).alpha(1f).setDuration(250).start();
    }

    private float slideDistance() {
        float d = px(WIDTH_DP + MARGIN_DP);
        return config.corner.endsWith("left") ? -d : d;
    }

    private TextView text(int sp, int color, Typeface face) {
        TextView t = new TextView(context);
        t.setTextSize(sp * scale());
        t.setTextColor(color);
        t.setTypeface(face);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        return t;
    }

    /** Card dimensions in pixels, at the card size chosen in the settings (cardScale). */
    private int px(int dps) {
        return Math.round(dps * dp * scale());
    }

    private float scale() {
        return config.cardScale / 100f;
    }

    private void removeQuietly(View v) {
        try {
            wm.removeView(v);
        } catch (RuntimeException ignored) {
            // already gone
        }
    }
}
