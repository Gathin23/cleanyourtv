package io.github.raqs31.nowplaying;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The card over Home: an accessibility overlay window, so no "display over other apps" permission. It never
 * takes focus or touches, so the D-pad keeps moving around Home.
 */
final class OverlayCard {
    private static final String TAG = "NowPlaying";
    private static final int WIDTH_DP = 320;
    private static final int ART_DP = 64;
    private static final int MARGIN_RIGHT_DP = 56;
    private static final int MARGIN_TOP_DP = 128;

    private final Context context;
    private final WindowManager wm;
    private final float dp;
    private LinearLayout root;
    private ImageView art;
    private TextView title;
    private TextView artist;
    private TextView state;

    OverlayCard(Context context) {
        this.context = context;
        this.wm = context.getSystemService(WindowManager.class);
        this.dp = context.getResources().getDisplayMetrics().density;
    }

    void show(MediaController c) {
        if (root == null) {
            build();
            try {
                wm.addView(root, params());
            } catch (RuntimeException e) {
                Log.w(TAG, "card not shown: " + e.getMessage());
                root = null;
                return;
            }
        }
        Bitmap cover = SessionTracker.art(c);
        if (cover != null) art.setImageBitmap(cover);
        else art.setImageDrawable(SessionTracker.appIcon(context, c));
        title.setText(SessionTracker.title(c));
        artist.setText(SessionTracker.artist(c));
        boolean paused = SessionTracker.state(c) == PlaybackState.STATE_PAUSED;
        state.setText(paused ? R.string.paused : R.string.card_keys);
    }

    void hide() {
        if (root == null) return;
        try {
            wm.removeView(root);
        } catch (RuntimeException alreadyGone) {
            // removed with the service's windows
        }
        root = null;
    }

    private void build() {
        root = new LinearLayout(context);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setBackgroundResource(R.drawable.card_glass);
        int pad = px(12);
        root.setPadding(pad, pad, pad, pad);

        art = new ImageView(context);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(art, new LinearLayout.LayoutParams(px(ART_DP), px(ART_DP)));

        LinearLayout text = new LinearLayout(context);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setPadding(px(12), 0, 0, 0);
        title = line(17, Color.WHITE, Typeface.BOLD);
        artist = line(14, 0xFFB8C0C8, Typeface.NORMAL);
        state = line(12, 0xFF8A939C, Typeface.NORMAL);
        text.addView(title);
        text.addView(artist);
        text.addView(state);
        root.addView(text, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
    }

    private TextView line(int sp, int color, int style) {
        TextView t = new TextView(context);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.DEFAULT, style);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    private WindowManager.LayoutParams params() {
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                px(WIDTH_DP), WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        // Under the top bar's pills, right-aligned with them: the empty sky between the clock and the dock.
        p.gravity = Gravity.TOP | Gravity.RIGHT;
        p.x = px(MARGIN_RIGHT_DP);
        p.y = px(MARGIN_TOP_DP);
        p.setTitle("Now Playing");
        return p;
    }

    private int px(int dps) {
        return Math.round(dps * dp);
    }
}
