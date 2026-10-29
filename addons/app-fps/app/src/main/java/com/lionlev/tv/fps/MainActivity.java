package com.lionlev.tv.fps;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

public final class MainActivity extends Activity {
    private Button toggle;
    private TextView status;
    private TextView permission;
    private final View[] choices = new View[4];
    private SharedPreferences preferences;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() {
        public void run() { updateStatus(); handler.postDelayed(this, 500); }
    };
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    public void onCreate(Bundle state) {
        super.onCreate(state);
        preferences = getSharedPreferences("overlay", 0);
        FrameLayout root = new FrameLayout(this);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xff101d2b, 0xff080d17, 0xff15232c}));
        setContentView(root);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        // Focused tiles grow beyond their row; keep the complete rounded stroke visible.
        panel.setClipChildren(false);
        panel.setClipToPadding(false);
        panel.setPadding(dp(28), dp(24), dp(28), dp(24));
        panel.setBackground(surface(0xd91a2533, 0x30ffffff, 26));
        panel.setElevation(dp(12));
        FrameLayout.LayoutParams place = new FrameLayout.LayoutParams(
                Math.min(dp(660), getResources().getDisplayMetrics().widthPixels - dp(64)), -2, Gravity.CENTER);
        root.addView(panel, place);
        status = text(R.string.state_off, 14, 0xffa3b4c5);
        LinearLayout.LayoutParams statusSize = new LinearLayout.LayoutParams(-1, -2);
        statusSize.setMargins(0, dp(6), 0, dp(24));
        panel.addView(status, statusSize);
        panel.addView(text(R.string.corner, 17, 0xffb9c8d7));
        int[] labels = {R.string.top_left, R.string.top_right, R.string.bottom_left, R.string.bottom_right};
        for (int row = 0; row < 2; row++) {
            LinearLayout line = new LinearLayout(this);
            // Choices map to physical screen corners, even in Hebrew.
            line.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
            line.setClipChildren(false);
            line.setClipToPadding(false);
            LinearLayout.LayoutParams lineSize = new LinearLayout.LayoutParams(-1, dp(86));
            lineSize.topMargin = dp(10);
            panel.addView(line, lineSize);
            for (int col = 0; col < 2; col++) {
                final int corner = row * 2 + col;
                LinearLayout tile = new LinearLayout(this);
                tile.setId(View.generateViewId());
                tile.setOrientation(LinearLayout.VERTICAL);
                tile.setGravity(Gravity.CENTER);
                tile.setFocusable(true);
                tile.setClickable(true);
                tile.setContentDescription(getString(labels[corner]));
                tile.setBackground(tileBackground());
                tile.setOnFocusChangeListener((view, focused) -> view.animate().scaleX(focused ? 1.025f : 1f)
                        .scaleY(focused ? 1.025f : 1f).setDuration(120).start());
                LinearLayout.LayoutParams tileSize = new LinearLayout.LayoutParams(0, -1, 1);
                if (col == 0) tileSize.rightMargin = dp(10);
                line.addView(tile, tileSize);
                tile.addView(new CornerPreview(corner), new LinearLayout.LayoutParams(dp(48), dp(28)));
                TextView label = text(labels[corner], 16, 0xffedf6ff);
                label.setGravity(Gravity.CENTER);
                LinearLayout.LayoutParams labelSize = new LinearLayout.LayoutParams(-1, -2);
                labelSize.topMargin = dp(7);
                tile.addView(label, labelSize);
                choices[corner] = tile;
                tile.setOnClickListener(view -> {
                    preferences.edit().putInt("corner", corner).apply();
                    updateChoices();
                    if (FpsService.current != null) FpsService.current.applyPosition();
                });
            }
        }
        toggle = new Button(this);
        toggle.setId(View.generateViewId());
        toggle.setAllCaps(false);
        toggle.setTextSize(18);
        toggle.setTextColor(0xfff0fff9);
        toggle.setBackground(tileBackground());
        LinearLayout.LayoutParams toggleSize = new LinearLayout.LayoutParams(-1, dp(54));
        toggleSize.topMargin = dp(24);
        panel.addView(toggle, toggleSize);
        toggle.setOnClickListener(view -> changeEnabled());
        toggle.setOnKeyListener((view, key, event) -> event.getAction() == KeyEvent.ACTION_DOWN
                && (key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT));
        permission = text(R.string.permission, 14, 0xff85dfc7);
        permission.setId(View.generateViewId());
        permission.setGravity(Gravity.CENTER);
        permission.setFocusable(true);
        permission.setClickable(true);
        permission.setBackground(tileBackground());
        LinearLayout.LayoutParams permissionSize = new LinearLayout.LayoutParams(-1, dp(36));
        permissionSize.topMargin = dp(10);
        panel.addView(permission, permissionSize);
        permission.setOnClickListener(view -> openPermission());
        for (int i = 0; i < 4; i++) {
            int corner = i;
            choices[i].setNextFocusLeftId(choices[i % 2 == 1 ? i - 1 : i].getId());
            choices[i].setNextFocusRightId(choices[i % 2 == 0 ? i + 1 : i].getId());
            choices[i].setNextFocusUpId(choices[i < 2 ? i : i - 2].getId());
            choices[i].setNextFocusDownId(i < 2 ? choices[i + 2].getId() : toggle.getId());
            choices[i].setOnKeyListener((view, key, event) -> event.getAction() == KeyEvent.ACTION_DOWN &&
                    (key == KeyEvent.KEYCODE_DPAD_LEFT && corner % 2 == 0
                    || key == KeyEvent.KEYCODE_DPAD_RIGHT && corner % 2 == 1
                    || key == KeyEvent.KEYCODE_DPAD_UP && corner < 2));
        }
        updateChoices();
        updateStatus();
        toggle.requestFocus();
    }

    private TextView text(int resource, int size, int color) {
        TextView view = new TextView(this);
        view.setText(resource);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setTextDirection(View.TEXT_DIRECTION_LOCALE);
        return view;
    }
    private GradientDrawable surface(int color, int border, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color); drawable.setStroke(dp(1), border); drawable.setCornerRadius(dp(radius));
        return drawable;
    }
    private StateListDrawable tileBackground() {
        StateListDrawable drawable = new StateListDrawable();
        drawable.addState(new int[]{android.R.attr.state_focused}, surface(0xff293e4d, 0xffa2f3dc, 14));
        drawable.addState(new int[]{android.R.attr.state_selected}, surface(0x304bc4a6, 0xff71d4bd, 14));
        drawable.addState(new int[]{}, surface(0x15ffffff, 0x24ffffff, 14));
        return drawable;
    }
    private void updateChoices() {
        int selected = preferences.getInt("corner", 0);
        for (int i = 0; i < 4; i++) choices[i].setSelected(i == selected);
        toggle.setNextFocusUpId(choices[Math.max(0, Math.min(3, selected))].getId());
    }
    private void openPermission() {
        startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
    }
    private void changeEnabled() {
        if (FpsService.current != null) {
            stopService(new Intent(this, FpsService.class));
            Toast.makeText(this, R.string.stopped, Toast.LENGTH_SHORT).show();
            updateStatus();
            return;
        }
        if (!Settings.canDrawOverlays(this)) {
            new AlertDialog.Builder(this).setMessage(R.string.error)
                    .setPositiveButton(R.string.permission, (dialog, which) -> openPermission())
                    .setNegativeButton(android.R.string.cancel, null).show();
            return;
        }
        if (!LocalSampler.available(this) && !HelperState.ready()) {
            new AlertDialog.Builder(this).setTitle(R.string.helper_needed).setMessage(R.string.helper_setup)
                    .setPositiveButton(android.R.string.ok, null).show();
            return;
        }
        new AlertDialog.Builder(this).setTitle(R.string.start).setMessage(LocalSampler.available(this) ? R.string.standalone_warning : R.string.enable_warning)
                .setPositiveButton(R.string.start, (dialog, which) -> {
                    startForegroundService(new Intent(this, FpsService.class));
                    handler.postDelayed(this::updateStatus, 250);
                }).setNegativeButton(android.R.string.cancel, null).show();
    }
    private void updateStatus() {
        FpsService service = FpsService.current;
        String action = getString(service == null ? R.string.start : R.string.stop);
        if (!action.contentEquals(toggle.getText())) toggle.setText(action);
        String message = getString(service == null ? (LocalSampler.available(this) || HelperState.ready() ? R.string.state_ready : R.string.state_off)
                : service.connected() ? R.string.state_connected : R.string.state_waiting);
        if (!message.contentEquals(status.getText())) status.setText(message);
        permission.setVisibility(Settings.canDrawOverlays(this) ? View.GONE : View.VISIBLE);
        toggle.setNextFocusDownId(permission.getVisibility() == View.VISIBLE ? permission.getId() : toggle.getId());
    }
    protected void onResume() { super.onResume(); handler.post(refresh); }
    protected void onPause() { handler.removeCallbacksAndMessages(null); super.onPause(); }

    private final class CornerPreview extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int corner;
        CornerPreview(int corner) { super(MainActivity.this); this.corner = corner; setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
        protected void onDraw(Canvas canvas) {
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(1)); paint.setColor(0x7795b6c8);
            canvas.drawRoundRect(dp(1), dp(1), getWidth()-dp(1), getHeight()-dp(1), dp(4), dp(4), paint);
            paint.setStyle(Paint.Style.FILL); paint.setColor(0xff90e7cf);
            float x = corner % 2 == 0 ? dp(5) : getWidth()-dp(17);
            float y = corner < 2 ? dp(5) : getHeight()-dp(10);
            canvas.drawRoundRect(x, y, x+dp(12), y+dp(5), dp(2), dp(2), paint);
        }
    }
}
