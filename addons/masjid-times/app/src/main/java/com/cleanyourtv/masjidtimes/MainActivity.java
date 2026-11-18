package com.cleanyourtv.masjidtimes;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.widget.TextView;

import org.json.JSONObject;

import java.text.DateFormat;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;

/** Today's timetable and the fetch status, so the owner can check what Home is reading. OK fetches again. */
public final class MainActivity extends Activity {
    private TextView text;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            show();
            handler.postDelayed(this, 5000);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        text = new TextView(this);
        text.setTextColor(Color.WHITE);
        text.setBackgroundColor(0xFF0B2A1E);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        text.setGravity(Gravity.CENTER);
        text.setLineSpacing(0, 1.3f);
        setContentView(text);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Timetable.refreshIfStale(this, false);
        handler.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            Timetable.refreshIfStale(this, true);
            text.setText("Fetching the timetable…");
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void show() {
        StringBuilder s = new StringBuilder();
        String name = getString(R.string.masjid_name).trim();
        s.append(name.isEmpty() ? "Masjid Times" : name).append("\n\n");
        if (Timetable.guid(this).isEmpty()) {
            s.append("No mosque set. Run install.ps1 on your computer.");
            text.setText(s);
            return;
        }
        JSONObject model = Timetable.load(this);
        if (model == null) {
            s.append("No timetable yet.");
        } else {
            ZonedDateTime now = ZonedDateTime.now();
            List<Prayers.Day> days = Timetable.days(model, now.toLocalDate());
            if (days.isEmpty()) {
                s.append("No row for today in the timetable.");
            } else {
                Prayers.Day d = days.get(0);
                for (int i = 0; i < Prayers.NAMES.length; i++) {
                    s.append(Prayers.NAMES[i]).append("    ").append(d.start[i]);
                    if (!d.jamaat[i].isEmpty()) s.append("    jamaat ").append(d.jamaat[i]);
                    s.append('\n');
                }
            }
            Prayers.Event e = Timetable.next(model, now);
            if (e != null) s.append("\nOn Home: ").append(e.topLine).append('\n');
            s.append("\nUpdated ").append(DateFormat.getDateTimeInstance().format(new Date(Timetable.updatedAt(this))));
        }
        if (!Timetable.lastError.isEmpty()) s.append("\nLast fetch failed: ").append(Timetable.lastError);
        s.append("\n\nPress OK to fetch again.");
        text.setText(s);
    }
}
