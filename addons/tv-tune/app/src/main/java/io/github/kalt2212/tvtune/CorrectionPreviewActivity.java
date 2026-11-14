package io.github.kalt2212.tvtune;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

/** A temporary app-owned RGB preview. Never reads or writes system picture settings. */
public final class CorrectionPreviewActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Preview view;
    private boolean resumed;
    private final Runnable pause = () -> {
        if (view != null) { view.paused = true; view.invalidate(); }
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    };

    @Override public void onCreate(Bundle state) { super.onCreate(state); load(getIntent()); }
    @Override public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent.hasExtra("source") || intent.hasExtra("observed_r") || intent.hasExtra("observed_g") || intent.hasExtra("observed_b")) {
            setIntent(intent); load(intent);
        }
    }

    private void load(Intent intent) {
        handler.removeCallbacks(pause);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        view = null;
        try {
            boolean supplied = intent.hasExtra("observed_r") || intent.hasExtra("observed_g") || intent.hasExtra("observed_b");
            String source = intent.getStringExtra("source");
            boolean camera = "camera".equals(source);
            if (source != null && !camera && !"demo".equals(source)) throw new IllegalArgumentException("source");
            if (camera && !supplied) throw new IllegalArgumentException("sample");
            CorrectionProfile profile = supplied ? CorrectionProfile.fromNeutral(
                sample(intent, "observed_r"), sample(intent, "observed_g"),
                sample(intent, "observed_b")) : CorrectionProfile.fromNeutral(144, 128, 112);
            view = new Preview(this, profile, camera); setContentView(view); arm();
        } catch (IllegalArgumentException error) {
            TextView message = new TextView(this); message.setText(R.string.correction_invalid);
            message.setTextColor(0xfff1f5fb); message.setBackgroundColor(0xff0b1424);
            message.setTextSize(24); message.setPadding(64, 64, 64, 64); setContentView(message);
        }
    }

    private static double sample(Intent intent, String name) {
        Object value = intent.getExtras() == null ? null : intent.getExtras().get(name);
        if (!(value instanceof String) || ((String) value).length() > 64)
            throw new IllegalArgumentException("sample");
        return Double.parseDouble((String) value);
    }

    private void arm() {
        handler.removeCallbacks(pause);
        if (resumed && view != null && !view.paused) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            handler.postDelayed(pause, 120000);
        }
    }

    @Override public boolean onKeyDown(int key, KeyEvent event) {
        if (view != null && (key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER)) {
            if (event.getRepeatCount() == 0) {
                if (view.paused) view.paused = false;
                else view.enabled = !view.enabled;
                view.invalidate(); arm();
            }
            return true;
        }
        return super.onKeyDown(key, event);
    }
    @Override public void onResume() { super.onResume(); resumed = true; arm(); }
    @Override public void onPause() { resumed = false; pause.run(); handler.removeCallbacks(pause); super.onPause(); }
    @Override public void onDestroy() { handler.removeCallbacksAndMessages(null); super.onDestroy(); }

    private static final class Preview extends View {
        private static final int[] COLORS = {0xff202020, 0xff404040, 0xff808080, 0xffc0c0c0,
            0xffff0000, 0xff00ff00, 0xff0000ff, 0xff40a080,
            0xffffff00, 0xff00ffff, 0xffff00ff, 0xffc08040};
        private final Paint paint = new Paint();
        private final CorrectionProfile profile;
        private final boolean camera;
        private boolean enabled, paused;
        Preview(Context context, CorrectionProfile profile, boolean camera) {
            super(context); this.profile = profile; this.camera = camera;
            setContentDescription(context.getString(R.string.correction_title));
        }
        private void label(Canvas canvas, String text, float x, float y, float size, int color) {
            paint.setColor(color); paint.setTextSize(size); paint.setAntiAlias(true);
            paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); canvas.drawText(text, x, y, paint);
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas); float w = getWidth(), h = getHeight(), u = Math.min(w / 960f, h / 540f);
            canvas.drawColor(paused ? 0xff111111 : 0xff0b1424);
            if (paused) { label(canvas, getResources().getString(R.string.dimmed), w * .1f, h * .5f, 20 * u, 0xfff1f5fb); return; }
            label(canvas, getResources().getString(R.string.correction_title), w * .05f, h * .07f, 28 * u, 0xfff1f5fb);
            label(canvas, getResources().getString(camera ? R.string.correction_camera : R.string.correction_demo), w * .05f, h * .13f, 15 * u, 0xffffca9e);
            double[] observed = profile.observed(), gains = profile.gains();
            label(canvas, getResources().getString(R.string.correction_sample, observed[0], observed[1], observed[2]), w * .05f, h * .18f, 14 * u, 0xffa4b4cc);
            label(canvas, getResources().getString(R.string.correction_gains, gains[0], gains[1], gains[2]), w * .05f, h * .23f, 14 * u, 0xffa4b4cc);
            float margin = w * .05f, panel = (w - margin * 3) / 2, cellW = panel / 4, cellH = h * .36f / 3;
            for (int side = 0; side < 2; side++) {
                float left = margin + side * (panel + margin);
                label(canvas, getResources().getString(side == 0 || !enabled ? R.string.correction_original : R.string.correction_enabled), left, h * .28f, 18 * u, 0xff88cfff);
                for (int i = 0; i < COLORS.length; i++) {
                    paint.setAntiAlias(false); paint.setColor(side == 1 && enabled ? profile.apply(COLORS[i]) : COLORS[i]);
                    float x = left + i % 4 * cellW, y = h * .30f + i / 4 * cellH;
                    canvas.drawRect(x + 2, y + 2, x + cellW - 2, y + cellH - 2, paint);
                }
            }
            label(canvas, getResources().getString(R.string.correction_controls), margin, h * .77f, 17 * u, 0xfff1f5fb);
            label(canvas, getResources().getString(R.string.correction_scope), margin, h * .84f, 14 * u, 0xffffca9e);
            label(canvas, getResources().getString(R.string.correction_accuracy), margin, h * .90f, 14 * u, 0xffffca9e);
        }
    }
}
