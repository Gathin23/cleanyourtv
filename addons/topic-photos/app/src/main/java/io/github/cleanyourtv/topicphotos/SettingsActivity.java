package io.github.cleanyourtv.topicphotos;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Topics, pace, day-and-night photos, and a way to the TV's own screensaver settings. All with the remote. */
public final class SettingsActivity extends Activity {

    private TextView topics, interval, dayNight;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        float d = getResources().getDisplayMetrics().density;
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setPadding((int) (96 * d), (int) (56 * d), (int) (96 * d), (int) (56 * d));
        TextView title = new TextView(this);
        title.setText(R.string.settings_title);
        title.setTextSize(32);
        title.setTextColor(0xFFFFFFFF);
        rows.addView(title);

        topics = row(rows, d);
        topics.setOnClickListener(v -> editTopics());
        hint(rows, d, R.string.topics_hint);
        interval = row(rows, d);
        interval.setOnClickListener(v -> pickInterval());
        dayNight = row(rows, d);
        dayNight.setOnClickListener(v -> {
            Settings.setDayNight(this, !Settings.dayNight(this));
            refresh();
        });
        hint(rows, d, R.string.day_night_hint);
        TextView screensaver = row(rows, d);
        screensaver.setText(R.string.set_screensaver);
        screensaver.setOnClickListener(v -> {
            try {
                startActivity(new Intent(android.provider.Settings.ACTION_DREAM_SETTINGS));
            } catch (ActivityNotFoundException e) {
                startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));
            }
        });
        hint(rows, d, R.string.set_screensaver_hint);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF101418);
        scroll.addView(rows);
        setContentView(scroll);
        refresh();
        topics.requestFocus();
    }

    private TextView row(LinearLayout rows, float d) {
        TextView t = new TextView(this);
        t.setTextSize(20);
        t.setTextColor(getColorStateList(R.color.row_text));
        t.setBackgroundResource(R.drawable.row);
        t.setFocusable(true);
        t.setClickable(true);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding((int) (24 * d), (int) (16 * d), (int) (24 * d), (int) (16 * d));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = (int) (22 * d);
        rows.addView(t, lp);
        return t;
    }

    private void hint(LinearLayout rows, float d, int text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(14);
        t.setTextColor(0xFFACB8C1);
        t.setPadding((int) (24 * d), (int) (6 * d), (int) (24 * d), 0);
        rows.addView(t);
    }

    private void refresh() {
        String typed = Settings.typedTopics(this);
        topics.setText(getString(R.string.topics) + ": "
                + (typed.isEmpty() ? getString(R.string.topics_default) : String.join(", ", Settings.parseTopics(typed))));
        interval.setText(getString(R.string.interval) + ": " + label(Settings.interval(this)));
        dayNight.setText(getString(R.string.day_night) + ": "
                + getString(Settings.dayNight(this) ? R.string.on : R.string.off));
    }

    /** "30 s", "5 min". */
    String label(int seconds) {
        return seconds < 60 ? getString(R.string.seconds, seconds) : getString(R.string.minutes, seconds / 60);
    }

    private void editTopics() {
        EditText field = new EditText(this);
        field.setSingleLine(true);
        field.setText(Settings.typedTopics(this));
        field.setHint(String.join(", ", Settings.DEFAULT_TOPICS.subList(0, 3)));
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        FrameLayout box = new FrameLayout(this);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(field);
        new AlertDialog.Builder(this)
                .setTitle(R.string.topics)
                .setMessage(R.string.topics_hint)
                .setView(box)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    Settings.setTopics(this, field.getText().toString());
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void pickInterval() {
        int[] values = Settings.INTERVALS;
        String[] labels = new String[values.length];
        int checked = -1;
        for (int i = 0; i < values.length; i++) {
            labels[i] = label(values[i]);
            if (values[i] == Settings.interval(this)) checked = i;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.interval)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    Settings.setInterval(this, values[which]);
                    refresh();
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
