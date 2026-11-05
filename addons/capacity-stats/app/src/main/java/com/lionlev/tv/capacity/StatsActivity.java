package com.lionlev.tv.capacity;

import android.app.Activity;
import android.os.*;
import android.view.*;
import android.widget.*;
import android.graphics.Color;
import android.text.BidiFormatter;

public final class StatsActivity extends Activity {
    private final Handler ui = new Handler(Looper.getMainLooper());
    private HandlerThread worker;
    private volatile Handler background;
    private TextView reading;
    private LinearLayout column;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(48, 32, 48, 32);
        column.setLayoutDirection(View.LAYOUT_DIRECTION_LOCALE);
        scroll.setBackgroundColor(Color.rgb(24,33,41));
        scroll.addView(column);
        setContentView(scroll);
        text(getString(R.string.app_name), 30);
        reading = text(getString(R.string.loading), 24);
        text(getString(R.string.note), 18);
        button(getString(R.string.back), this::finish);
    }
    private TextView text(String value, int size) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextSize(size); view.setTextColor(Color.WHITE);
        view.setGravity(Gravity.START); view.setTextDirection(View.TEXT_DIRECTION_LOCALE);
        view.setPadding(12,18,12,18);
        column.addView(view, new LinearLayout.LayoutParams(-1,-2));
        return view;
    }
    private Button button(String value, Runnable action) {
        Button view = new Button(this); view.setText(value);
        view.setOnClickListener(v -> action.run());
        column.addView(view, new LinearLayout.LayoutParams(-1,-2));
        return view;
    }
    private String number(String value) { return BidiFormatter.getInstance().unicodeWrap(value, android.text.TextDirectionHeuristics.LTR); }
    @Override public void onResume() {
        super.onResume();

        worker = new HandlerThread("addon-stats"); worker.start();
        Handler current = new Handler(worker.getLooper()); background = current;
        current.post(new Runnable() {
            @Override public void run() {
                if (background != current) return;
                String value = sample();
                ui.post(() -> { if (background == current) reading.setText(value); });
                if (background == current) current.postDelayed(this, 10000);
            }
        });
    }
    @Override public void onPause() {
        background = null;
        if (worker != null) { worker.quitSafely(); worker = null; }
        ui.removeCallbacksAndMessages(null);

        super.onPause();
    }
    private String sample() {
        StringBuilder result = new StringBuilder();
        try (java.io.FileReader reader = new java.io.FileReader("/proc/meminfo")) {
            long[] memory = MemoryCapacity.readMb(reader);
            if (memory[0] >= 0) {
                result.append(getString(R.string.memory, number(StatsCapacity.memory(memory[0], memory[1]) + " MB")));
                if (memory[1] > 0) result.append("\n").append(getString(R.string.memory_used,
                        number(StatsCapacity.memory(Math.max(0, memory[1] - memory[0]), memory[1]) + " MB")));
            }
        } catch (java.io.IOException ignored) { }
        java.io.File data = Environment.getDataDirectory();
        if (result.length() > 0) result.append("\n");
        result.append(getString(R.string.storage, number(StatsCapacity.storage(data.getFreeSpace(), data.getTotalSpace()))));
        return result.toString();
    }

}
