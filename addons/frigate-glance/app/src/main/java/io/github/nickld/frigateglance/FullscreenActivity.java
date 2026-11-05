package io.github.nickld.frigateglance;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * One camera full screen, opened from a card with the full screen key: live video, or a snapshot every second
 * when it cannot start. Back closes it and the app that was playing comes back.
 */
public class FullscreenActivity extends Activity {
    static final String EXTRA_CAMERA = "camera";
    static final String EXTRA_TITLE = "title";

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService loader = Executors.newSingleThreadExecutor();
    private LiveVideo live;
    private ImageView image;
    private TextureView video;
    private ModeBadge badge;
    private String camera;
    private boolean refreshing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        camera = getIntent().getStringExtra(EXTRA_CAMERA);
        float dp = getResources().getDisplayMetrics().density;
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        float aspect = GlanceService.aspect(camera);
        FrameLayout picture = new FrameLayout(this);
        image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.FIT_XY);
        video = new TextureView(this);
        video.setAlpha(0f);
        picture.addView(image, new FrameLayout.LayoutParams(-1, -1));
        picture.addView(video, new FrameLayout.LayoutParams(-1, -1));
        root.addView(picture, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        // Fill the height in the camera's own shape, centred, so the picture is never stretched.
        root.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int h = b - t, w = Math.min(r - l, Math.round(h * aspect));
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) picture.getLayoutParams();
            if (lp.width != w || lp.height != Math.round(w / aspect)) {
                lp.width = w;
                lp.height = Math.round(w / aspect);
                picture.setLayoutParams(lp);
            }
        });
        TextView title = new TextView(this);
        title.setText(getIntent().getStringExtra(EXTRA_TITLE) + "   Back closes");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(16);
        title.setShadowLayer(6, 0, 0, 0xFF000000);
        int pad = Math.round(24 * dp);
        title.setPadding(pad, pad, pad, pad);
        root.addView(title, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START));
        badge = new ModeBadge(this, 24);
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        bp.setMargins(0, pad, pad, 0);
        root.addView(badge, bp);
        setContentView(root);
        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        snapshot();
    }

    @Override
    protected void onResume() {
        super.onResume();
        String url = GlanceService.liveUrl(camera);
        if (url == null) {
            startRefresh();
            return;
        }
        live = new LiveVideo(this, new LiveVideo.Listener() {
            @Override
            public void onPlaying() {
                video.animate().alpha(1f).setDuration(300).start();
                badge.setLive(true);
            }

            @Override
            public void onGaveUp() {
                video.setAlpha(0f);
                badge.setLive(false);
                startRefresh();
            }
        });
        live.start(url, video);
    }

    @Override
    protected void onPause() {
        refreshing = false;
        if (live != null) {
            live.release();
            live = null;
        }
        super.onPause();
        // Leaving (Back, Home, another app) ends the view: it is a glance, not a place to stay.
        finish();
    }

    @Override
    protected void onDestroy() {
        loader.shutdownNow();
        super.onDestroy();
    }

    private void startRefresh() {
        refreshing = true;
        main.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!refreshing) return;
                snapshot();
                main.postDelayed(this, OverlayCard.SNAPSHOT_REFRESH_MS);
            }
        }, OverlayCard.SNAPSHOT_REFRESH_MS);
    }

    private void snapshot() {
        loader.execute(() -> {
            try {
                byte[] jpeg = GlanceService.snapshot(camera);
                Bitmap bmp = jpeg == null ? null : BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length);
                if (bmp != null) main.post(() -> image.setImageBitmap(bmp));
            } catch (IOException | RuntimeException ignored) {
                // keep the last picture
            }
        });
    }
}
