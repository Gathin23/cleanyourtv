package com.majnioui.tv.prayer;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class PrayerAlertActivity extends Activity {
    // Ultra-compact card dimensions: sleek, narrow, and unobtrusive for TV watching
    private static final int CARD_WIDTH_DP = 285;
    private static final int MARGIN_RIGHT_DP = 24;
    private static final int MARGIN_TOP_DP = 20;
    private static final int TIMEOUT_SECONDS = 10;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private int secondsRemaining = TIMEOUT_SECONDS;
    private TextView timerView;

    private final Runnable countdownRunnable = new Runnable() {
        @Override
        public void run() {
            secondsRemaining--;
            if (secondsRemaining <= 0) {
                finish();
            } else {
                if (timerView != null) {
                    timerView.setText(getString(R.string.prayer_alert_dismiss, secondsRemaining));
                }
                handler.postDelayed(this, 1000);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        String rawName = getIntent().getStringExtra(PrayerScheduler.EXTRA_PRAYER_NAME);
        String time = getIntent().getStringExtra(PrayerScheduler.EXTRA_PRAYER_TIME);
        String place = getIntent().getStringExtra(PrayerScheduler.EXTRA_PLACE);
        if (rawName == null) rawName = "Fajr";
        if (time == null) time = "";
        if (place == null) place = getString(R.string.place);

        int idx = PrayerScheduler.getPrayerIndex(rawName);
        String displayName = idx >= 0 ? getString(PrayerScheduler.NAME_RES[idx]) : rawName;
        int iconRes = idx >= 0 ? PrayerScheduler.ICONS[idx] : R.drawable.icon;
        String arabicText = idx >= 0 ? PrayerScheduler.ARABIC[idx] : "";

        Typeface font = getResources().getFont(R.font.outfit);
        int accent = getColor(R.color.accent);

        // Compact Slim Notification Card
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(13), dp(10), dp(14), dp(10));

        // Dark acrylic translucent background (frameless / borderless)
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF212161A); // 95% opacity dark slate
        bg.setCornerRadius(dp(14));
        card.setBackground(bg);

        // 1. Mini Prayer Icon container (frameless)
        LinearLayout iconBox = new LinearLayout(this);
        iconBox.setGravity(Gravity.CENTER);
        int boxSize = dp(38);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(boxSize, boxSize);
        blp.rightMargin = dp(11);
        iconBox.setLayoutParams(blp);
        GradientDrawable iconBoxBg = new GradientDrawable();
        iconBoxBg.setColor(0x282BB673);
        iconBoxBg.setCornerRadius(dp(10));
        iconBox.setBackground(iconBoxBg);

        ImageView iconView = new ImageView(this);
        iconView.setImageResource(iconRes);
        iconView.setImageTintList(ColorStateList.valueOf(accent));
        iconBox.addView(iconView, new LinearLayout.LayoutParams(dp(22), dp(22)));
        card.addView(iconBox);

        // 2. Text Column (Title + Prayer Info)
        LinearLayout infoCol = new LinearLayout(this);
        infoCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        infoCol.setLayoutParams(clp);

        // Row 1: Prayer Name + Arabic script
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView nameView = new TextView(this);
        nameView.setText(getString(R.string.prayer_alert_title, displayName));
        nameView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        nameView.setTextColor(Color.WHITE);
        nameView.setTypeface(Build.VERSION.SDK_INT >= 28 ? Typeface.create(font, 700, false) : font);
        titleRow.addView(nameView);

        if (!arabicText.isEmpty()) {
            TextView arabicView = new TextView(this);
            arabicView.setText(arabicText);
            arabicView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            arabicView.setTextColor(0xCC2BB673);
            arabicView.setPadding(dp(6), 0, 0, 0);
            titleRow.addView(arabicView);
        }

        infoCol.addView(titleRow);

        // Row 2: Time · Place
        LinearLayout subRow = new LinearLayout(this);
        subRow.setOrientation(LinearLayout.HORIZONTAL);
        subRow.setGravity(Gravity.CENTER_VERTICAL);
        subRow.setPadding(0, dp(1), 0, 0);

        TextView timeView = new TextView(this);
        timeView.setText(time);
        timeView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        timeView.setTextColor(0xFF4ADE80); // soft vibrant green
        timeView.setTypeface(Build.VERSION.SDK_INT >= 28 ? Typeface.create(font, 600, false) : font);
        subRow.addView(timeView);

        TextView dot = new TextView(this);
        dot.setText(" · ");
        dot.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        dot.setTextColor(0x66FFFFFF);
        subRow.addView(dot);

        TextView placeView = new TextView(this);
        placeView.setText(place);
        placeView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        placeView.setTextColor(0xB3FFFFFF);
        placeView.setTypeface(Build.VERSION.SDK_INT >= 28 ? Typeface.create(font, 400, false) : font);
        subRow.addView(placeView);

        infoCol.addView(subRow);
        card.addView(infoCol);

        // 3. Mini Countdown Pill on the right
        timerView = new TextView(this);
        timerView.setText(getString(R.string.prayer_alert_dismiss, secondsRemaining));
        timerView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        timerView.setTextColor(0x80FFFFFF);
        timerView.setTypeface(Build.VERSION.SDK_INT >= 28 ? Typeface.create(font, 400, false) : font);
        timerView.setPadding(dp(8), 0, 0, 0);
        card.addView(timerView);

        ViewGroup.LayoutParams vlp = new ViewGroup.LayoutParams(dp(CARD_WIDTH_DP), ViewGroup.LayoutParams.WRAP_CONTENT);
        setContentView(card, vlp);

        applyWindowPosition();
    }

    private void applyWindowPosition() {
        Window window = getWindow();
        if (window != null) {
            window.setGravity(Gravity.TOP | Gravity.END);
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.width = dp(CARD_WIDTH_DP);
            lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
            lp.x = dp(MARGIN_RIGHT_DP);
            lp.y = dp(MARGIN_TOP_DP);
            window.setAttributes(lp);
        }
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        applyWindowPosition();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyWindowPosition();
        handler.removeCallbacks(countdownRunnable);
        secondsRemaining = TIMEOUT_SECONDS;
        if (timerView != null) {
            timerView.setText(getString(R.string.prayer_alert_dismiss, secondsRemaining));
        }
        handler.postDelayed(countdownRunnable, 1000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(countdownRunnable);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        finish();
        return super.onKeyDown(keyCode, event);
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
