package io.github.savioronezero.worldclock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/** Shared preview/dream surface. All saved clocks are visible together, without paging. */
final class ClockScene extends FrameLayout {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Face> faces = new ArrayList<>();
    private final ClockPreferences settings;
    private final LinearLayout grid;
    private final Sky sky;
    private boolean active, listening;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!visible()) return;
            refresh();
            handler.postDelayed(this, ClockModel.nextMinute(System.currentTimeMillis()));
        }
    };
    private final Runnable frame = new Runnable() {
        @Override public void run() {
            if (!visible() || !settings.motion) return;
            long now = SystemClock.uptimeMillis();
            grid.setTranslationX(dp(AmbientMotion.DRIFT_DP) * AmbientMotion.drift(now, 137000));
            grid.setTranslationY(dp(AmbientMotion.DRIFT_DP) * AmbientMotion.drift(now, 173000));
            sky.phase = (float) (Math.floorMod(now, 240000L) * 360.0 / 240000);
            if (settings.background == 0 || settings.background == 1) sky.invalidate();
            handler.postDelayed(this, AmbientMotion.FRAME_MS);
        }
    };
    private final BroadcastReceiver changedTime = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            handler.removeCallbacks(tick);
            if (visible()) tick.run();
        }
    };

    ClockScene(Context context) {
        super(context);
        settings = new ClockPreferences(ClockPreferences.store(context));
        setBackgroundColor(Color.BLACK);
        sky = new Sky(context, settings.background);
        if (settings.background != 3) addView(sky, new FrameLayout.LayoutParams(-1, -1));
        grid = new LinearLayout(context);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setAlpha(AmbientMotion.clockAlpha(settings.brightness));
        // The 48dp inset leaves room for the entire +/-12dp drift, even with all six clocks.
        FrameLayout.LayoutParams gridParams = new FrameLayout.LayoutParams(-1, -1);
        gridParams.setMargins(dp(48), dp(40), dp(48), dp(40));
        addView(grid, gridParams);
        int columns = AmbientMotion.columns(settings.ids.size());
        for (int rowIndex = 0; rowIndex < AmbientMotion.rows(settings.ids.size()); rowIndex++) {
            int start = rowIndex * columns;
            LinearLayout row = new LinearLayout(context);
            grid.addView(row, new LinearLayout.LayoutParams(-1, 0, 1));
            for (int col = 0; col < columns; col++) {
                if (start + col >= settings.ids.size()) {
                    row.addView(new View(context), new LinearLayout.LayoutParams(0, -1, 1));
                    continue;
                }
                Face face = new Face(settings.ids.get(start + col));
                faces.add(face);
                row.addView(face.root, new LinearLayout.LayoutParams(0, -1, 1));
            }
        }
    }

    void start() {
        if (active) return;
        active = true;
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        filter.addAction(Intent.ACTION_DATE_CHANGED);
        if (Build.VERSION.SDK_INT >= 33) getContext().registerReceiver(changedTime, filter, Context.RECEIVER_NOT_EXPORTED);
        else getContext().registerReceiver(changedTime, filter);
        listening = true;
        schedule();
    }

    void stop() {
        active = false;
        handler.removeCallbacks(tick);
        handler.removeCallbacks(frame);
        if (listening) {
            getContext().unregisterReceiver(changedTime);
            listening = false;
        }
    }

    private boolean visible() { return active && getWindowVisibility() == VISIBLE; }
    private void schedule() {
        handler.removeCallbacks(tick);
        handler.removeCallbacks(frame);
        if (!visible()) return;
        tick.run();
        if (settings.motion) frame.run();
    }
    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        // The framework can deliver this while the View constructor is still running.
        if (grid != null) schedule();
    }
    @Override protected void onDetachedFromWindow() { stop(); super.onDetachedFromWindow(); }

    private void refresh() {
        Instant instant = Instant.now();
        ZoneId local = ZoneId.systemDefault();
        Locale locale = Locale.getDefault();
        boolean twentyFour = settings.format == 2 || (settings.format == 0
                && android.text.format.DateFormat.is24HourFormat(getContext()));
        for (Face face : faces) {
            ClockModel.Reading reading = ClockModel.at(face.id, instant, local, twentyFour, locale);
            if (reading.period.isEmpty()) face.time.setText(reading.time);
            else {
                SpannableString display = new SpannableString(reading.time + " " + reading.period);
                display.setSpan(new RelativeSizeSpan(0.35f), reading.time.length() + 1, display.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                face.time.setText(display);
            }
            face.date.setText(reading.date);
            String day = reading.dayDifference == 0 ? getContext().getString(R.string.same_day)
                    : reading.dayDifference == 1 ? getContext().getString(R.string.tomorrow)
                    : reading.dayDifference == -1 ? getContext().getString(R.string.yesterday)
                    : getResources().getQuantityString(R.plurals.day_difference, Math.abs(reading.dayDifference), reading.dayDifference);
            face.detail.setText(getContext().getString(R.string.zone_detail, reading.offset, day));
        }
    }

    private final class Face {
        final String id;
        final LinearLayout root;
        final TextView time, date, detail;
        Face(String id) {
            this.id = id;
            root = new LinearLayout(getContext());
            root.setOrientation(LinearLayout.VERTICAL);
            root.setGravity(Gravity.CENTER);
            root.setPadding(dp(12), dp(12), dp(12), dp(12));
            TextView city = label(ClockModel.LOCAL.equals(id) ? getContext().getString(R.string.local) : ClockModel.city(id), 26);
            autoSize(city, 16, 26);
            root.addView(city, new LinearLayout.LayoutParams(-1, dp(36)));
            time = label("", 88);
            time.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
            time.setTextDirection(View.TEXT_DIRECTION_LTR);
            autoSize(time, 20, 88);
            root.addView(time, new LinearLayout.LayoutParams(-1, dp(settings.ids.size() > 3 ? 104 : 136)));
            date = label("", 18);
            autoSize(date, 13, 18);
            root.addView(date, new LinearLayout.LayoutParams(-1, dp(30)));
            detail = label("", 13);
            autoSize(detail, 10, 13);
            root.addView(detail, new LinearLayout.LayoutParams(-1, dp(26)));
        }
    }

    private void autoSize(TextView text, int minimumDp, int preferredSp) {
        // An SP minimum grows with accessibility font scale and can exceed a fixed-height label.
        // Keep a readable physical floor; the preferred maximum still follows the user's font scale.
        int minimum = dp(minimumDp);
        int preferred = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,
                preferredSp, getResources().getDisplayMetrics()));
        text.setAutoSizeTextTypeUniformWithConfiguration(minimum, Math.max(minimum, preferred),
                Math.max(1, dp(1)), TypedValue.COMPLEX_UNIT_PX);
    }

    private TextView label(String value, int size) {
        TextView view = new TextView(getContext());
        view.setText(value); view.setTextSize(size); view.setTextColor(Color.rgb(237, 245, 252));
        view.setMaxLines(1); view.setGravity(Gravity.CENTER);
        return view;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private static final class Sky extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Matrix matrix = new Matrix();
        private final int background;
        private final WorldMap map;
        private final float[] stars = new float[96 * 3];
        private Shader shader;
        float phase;
        Sky(Context context, int background) {
            super(context);
            this.background = background;
            map = background == 2 ? new WorldMap(context) : null;
            Random random = new Random(7319);
            for (int i = 0; i < stars.length; i++) stars[i] = random.nextFloat();
        }
        @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            shader = new LinearGradient(0, 0, Math.max(1, w), Math.max(1, h),
                    new int[]{Color.rgb(3, 8, 18), Color.rgb(13, 28, 43), Color.rgb(5, 19, 29)}, null, Shader.TileMode.CLAMP);

        }
        @Override protected void onDraw(Canvas canvas) {
            if (background == 0 && shader != null) {
                matrix.setRotate(phase, getWidth() / 2f, getHeight() / 2f);
                shader.setLocalMatrix(matrix);
                paint.setShader(shader);
                canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
            } else if (map != null) {
                canvas.drawColor(Color.rgb(3, 8, 15));
                paint.setShader(null);
                paint.setColor(Color.rgb(25, 44, 59));
                map.draw(canvas, paint, getWidth(), getHeight());
            } else if (background == 1) {
                canvas.drawColor(Color.rgb(3, 6, 14));
                paint.setShader(null);
                paint.setColor(Color.rgb(86, 106, 128));
                float shift = (float) Math.sin(Math.toRadians(phase)) * getWidth() * 0.015f;
                for (int i = 0; i < stars.length; i += 3) {
                    float x = (stars[i] * getWidth() + shift + getWidth()) % getWidth();
                    canvas.drawCircle(x, stars[i + 1] * getHeight(), 0.6f + stars[i + 2] * 1.3f, paint);
                }
            }
        }
    }
}
