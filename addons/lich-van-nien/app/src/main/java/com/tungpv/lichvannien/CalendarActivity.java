package com.tungpv.lichvannien;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;

import java.util.Calendar;

/** The screen behind the tile: Left/Right move a day, OK returns to today, Back leaves. */
public class CalendarActivity extends Activity {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refresh();
            // Next run at the start of the next minute.
            handler.postDelayed(this, 60_000 - System.currentTimeMillis() % 60_000 + 50);
        }
    };
    private CalendarView view;
    private int offset;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        view = new CalendarView(this);
        setContentView(view);
    }

    @Override
    protected void onResume() {
        super.onResume();
        DayUpdater.update(this);
        handler.post(tick);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(tick);
        super.onPause();
    }

    private void refresh() {
        Calendar c = DayUpdater.now();
        int hour = c.get(Calendar.HOUR_OF_DAY);
        c.add(Calendar.DAY_OF_MONTH, offset);
        VnCalendar.Day day = VnCalendar.of(c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.MONTH) + 1, c.get(Calendar.YEAR));
        view.show(day, offset == 0 ? VnCalendar.hourIndex(hour) : -1, hour);
    }

    /** The lunar algorithm is trusted for 1900–2199; stop there. */
    private static boolean inRange(int offsetDays) {
        Calendar c = DayUpdater.now();
        c.add(Calendar.DAY_OF_MONTH, offsetDays);
        int year = c.get(Calendar.YEAR);
        return c.get(Calendar.ERA) == java.util.GregorianCalendar.AD && year >= 1900 && year <= 2199;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                int next = offset + (keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1);
                if (inRange(next)) {
                    offset = next;
                    refresh();
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                offset = 0;
                refresh();
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }
}
