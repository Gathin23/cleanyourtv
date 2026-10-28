package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

final class VolumeOverlay {
    private static final String TAG = "VolumeOverlay";

    static final int DIRECTION_UP = 1;
    static final int DIRECTION_DOWN = 2;
    static final int DIRECTION_MUTE = 3;

    private static WindowManager wm;
    private static LinearLayout overlayView;
    private static TextView iconView;
    private static TextView labelView;
    private static TextView signView;

    private static final Handler handler = new Handler(Looper.getMainLooper());
    private static final Runnable dismissRunnable = VolumeOverlay::hide;
    private static boolean isShowing = false;

    static void show(Context context, int direction) {
        handler.post(() -> {
            try {
                if (wm == null) {
                    wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
                }

                if (overlayView == null) {
                    overlayView = new LinearLayout(context);
                    overlayView.setOrientation(LinearLayout.VERTICAL);
                    overlayView.setGravity(Gravity.CENTER);

                    int padH = dp(context, 10);
                    int padV = dp(context, 14);
                    overlayView.setPadding(padH, padV, padH, padV);

                    GradientDrawable bg = new GradientDrawable();
                    bg.setColor(Color.parseColor("#EE1C1C1E"));
                    bg.setCornerRadius(dp(context, 25));
                    bg.setStroke(dp(context, 1), Color.parseColor("#38FFFFFF"));
                    overlayView.setBackground(bg);

                    // Top: Speaker icon
                    iconView = new TextView(context);
                    iconView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
                    iconView.setTextColor(Color.WHITE);
                    iconView.setGravity(Gravity.CENTER);
                    overlayView.addView(iconView);

                    // Middle: "VOL" label
                    labelView = new TextView(context);
                    labelView.setText("VOL");
                    labelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
                    labelView.setTypeface(Typeface.DEFAULT_BOLD);
                    labelView.setTextColor(Color.parseColor("#99FFFFFF"));
                    labelView.setLetterSpacing(0.06f);
                    labelView.setGravity(Gravity.CENTER);
                    LinearLayout.LayoutParams lpLabel = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    );
                    lpLabel.topMargin = dp(context, 3);
                    lpLabel.bottomMargin = dp(context, 1);
                    labelView.setLayoutParams(lpLabel);
                    overlayView.addView(labelView);

                    // Bottom: "+" or "−" or "✕"
                    signView = new TextView(context);
                    signView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
                    signView.setTypeface(Typeface.DEFAULT_BOLD);
                    signView.setGravity(Gravity.CENTER);
                    overlayView.addView(signView);
                }

                // Update content based on direction
                if (direction == DIRECTION_UP) {
                    iconView.setText("\uD83D\uDD0A"); // 🔊
                    labelView.setText("VOL");
                    labelView.setTextColor(Color.parseColor("#99FFFFFF"));
                    signView.setText("+");
                    signView.setTextColor(Color.WHITE);
                } else if (direction == DIRECTION_DOWN) {
                    iconView.setText("\uD83D\uDD09"); // 🔉
                    labelView.setText("VOL");
                    labelView.setTextColor(Color.parseColor("#99FFFFFF"));
                    signView.setText("−"); // unicode minus
                    signView.setTextColor(Color.WHITE);
                } else if (direction == DIRECTION_MUTE) {
                    iconView.setText("\uD83D\uDD07"); // 🔇
                    labelView.setText("MUTE");
                    labelView.setTextColor(Color.parseColor("#FF6B6B"));
                    signView.setText("✕");
                    signView.setTextColor(Color.parseColor("#FF6B6B"));
                }

                if (!isShowing && wm != null) {
                    if (overlayView.getParent() != null) {
                        try {
                            wm.removeViewImmediate(overlayView);
                        } catch (Exception ignored) {}
                    }
                    WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                        dp(context, 50),
                        dp(context, 96),
                        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                        PixelFormat.TRANSLUCENT
                    );
                    lp.gravity = Gravity.CENTER_VERTICAL | Gravity.END;
                    lp.x = dp(context, 32);
                    wm.addView(overlayView, lp);
                    isShowing = true;
                    Log.d(TAG, "Vertical side overlay shown, dir=" + direction);
                }

                handler.removeCallbacks(dismissRunnable);
                handler.postDelayed(dismissRunnable, 1800);
            } catch (Exception e) {
                Log.e(TAG, "show error", e);
            }
        });
    }

    private static void hide() {
        if (isShowing && wm != null && overlayView != null) {
            try {
                if (overlayView.isAttachedToWindow()) {
                    wm.removeViewImmediate(overlayView);
                }
            } catch (Exception e) {
                Log.e(TAG, "hide error", e);
            }
            isShowing = false;
            Log.d(TAG, "Overlay hidden");
        }
    }

    private static int dp(Context ctx, int val) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, val, ctx.getResources().getDisplayMetrics());
    }
}
