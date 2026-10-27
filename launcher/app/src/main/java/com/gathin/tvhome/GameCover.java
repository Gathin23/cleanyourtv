package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Tela da Central de Jogos desenhada por cima de tudo, inclusive do HDMI: cobre o "procurando sinal" da
 * TCL quando a TV troca para o PC, e a área de trabalho do Windows quando o jogo fecha.
 * O programa do PC controla por adb:
 *   am broadcast -n com.gathin.tvhome/.GameCover --es mode show --es title "..." --es text "..." --es img URL
 *   am broadcast -n com.gathin.tvhome/.GameCover --es mode hide
 * Precisa de: appops set com.gathin.tvhome SYSTEM_ALERT_WINDOW allow (feito na instalação).
 */
public class GameCover extends BroadcastReceiver {
    private static final String TAG = "GameCover";
    private static FrameLayout view;
    private static ImageView art;
    private static TextView title, text;
    private static String imgUrl;
    private static final Handler ui = new Handler(Looper.getMainLooper());

    @Override
    public void onReceive(Context ctx, Intent in) {
        Context app = ctx.getApplicationContext();
        String mode = in.getStringExtra("mode");
        try {
            if ("hide".equals(mode)) hide(app, in.getIntExtra("delay", 0));
            else show(app, in.getStringExtra("title"), in.getStringExtra("text"), in.getStringExtra("img"));
        } catch (Exception e) {
            Log.w(TAG, "cover " + mode + ": " + e);
        }
    }

    private static void show(Context app, String t, String sub, String img) {
        ui.removeCallbacksAndMessages(null);
        if (view == null) {
            view = build(app);
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                    PixelFormat.OPAQUE);
            view.setAlpha(1f);
            ((WindowManager) app.getSystemService(Context.WINDOW_SERVICE)).addView(view, lp);
        }
        view.animate().cancel();
        view.setAlpha(1f);
        title.setText(t == null ? "" : t);
        text.setText(sub == null ? "" : sub);
        if (img != null && !img.isEmpty() && !img.equals(imgUrl)) {
            imgUrl = img;
            art.setImageDrawable(null);
            loadArt(img);
        }
        Log.i(TAG, "show " + t);
    }

    private static void hide(Context app, int delayMs) {
        ui.removeCallbacksAndMessages(null);
        ui.postDelayed(() -> {
            if (view == null) return;
            final View v = view;
            v.animate().alpha(0f).setDuration(350).withEndAction(() -> {
                try { ((WindowManager) app.getSystemService(Context.WINDOW_SERVICE)).removeView(v); } catch (Exception ignored) {}
            }).start();
            view = null;
            imgUrl = null;
            Log.i(TAG, "hide");
        }, delayMs);
    }

    private static FrameLayout build(Context c) {
        FrameLayout root = new FrameLayout(c);
        root.setBackgroundColor(Color.rgb(10, 14, 36));
        art = new ImageView(c);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(art, new FrameLayout.LayoutParams(-1, -1));
        // Mesmo escurecimento da tela de carregamento da aba Jogos.
        View shade = new View(c);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.argb(235, 8, 10, 30), Color.argb(140, 8, 10, 30), Color.argb(60, 8, 10, 30)}));
        root.addView(shade, new FrameLayout.LayoutParams(-1, -1));
        View bottom = new View(c);
        bottom.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{Color.argb(230, 8, 10, 30), Color.argb(0, 8, 10, 30)}));
        root.addView(bottom, new FrameLayout.LayoutParams(-1, -1));

        float d = c.getResources().getDisplayMetrics().density;
        LinearLayout info = new LinearLayout(c);
        info.setOrientation(LinearLayout.VERTICAL);
        FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START);
        ip.leftMargin = (int) (64 * d);
        ip.bottomMargin = (int) (64 * d);
        title = new TextView(c);
        title.setTextColor(Color.WHITE);
        title.setTextSize(36);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        info.addView(title);
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, (int) (16 * d), 0, 0);
        ProgressBar spin = new ProgressBar(c);
        spin.setIndeterminate(true);
        spin.getIndeterminateDrawable().setTint(Color.WHITE);
        row.addView(spin, new LinearLayout.LayoutParams((int) (24 * d), (int) (24 * d)));
        text = new TextView(c);
        text.setTextColor(Color.argb(220, 255, 255, 255));
        text.setTextSize(16);
        text.setPadding((int) (12 * d), 0, 0, 0);
        row.addView(text);
        info.addView(row);
        root.addView(info, ip);
        return root;
    }

    private static void loadArt(String url) {
        new Thread(() -> {
            try {
                HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
                con.setConnectTimeout(5000);
                con.setReadTimeout(8000);
                Bitmap bmp;
                try (InputStream is = con.getInputStream()) { bmp = BitmapFactory.decodeStream(is); }
                if (bmp != null) ui.post(() -> { if (url.equals(imgUrl) && art != null) art.setImageBitmap(bmp); });
            } catch (Exception e) {
                Log.w(TAG, "art: " + e);
            }
        }).start();
    }
}
