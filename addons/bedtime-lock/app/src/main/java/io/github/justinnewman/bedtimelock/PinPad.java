package io.github.justinnewman.bedtimelock;

import android.view.KeyEvent;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * Six boxes that fill with dots. Every key that types a digit (Pin.digitOf: number keys, or the arrows and OK as
 * odd digits) adds one dot and nothing else, so the screen never shows which key it was. Delete takes the
 * last one back; the sixth hands the code to the listener.
 */
final class PinPad {
    interface Listener {
        void onPin(String pin);
    }

    private final TextView[] cells = new TextView[Pin.LENGTH];
    private final StringBuilder typed = new StringBuilder(Pin.LENGTH);
    private final Listener listener;

    PinPad(ViewGroup row, Listener listener) {
        for (int i = 0; i < Pin.LENGTH; i++) cells[i] = (TextView) row.getChildAt(i);
        this.listener = listener;
        clear();
    }

    void clear() {
        typed.setLength(0);
        render();
    }

    /** Whether anything has been typed, so Back can clear it instead of leaving. */
    boolean started() {
        return typed.length() > 0;
    }

    /** True when the key belongs to the pad (both its down and its up), so the caller passes nothing on. */
    boolean onKey(KeyEvent event) {
        int digit = Pin.digitOf(event.getKeyCode());
        boolean delete = event.getKeyCode() == KeyEvent.KEYCODE_DEL;
        if (digit < 0 && !delete) return false;
        if (event.getAction() != KeyEvent.ACTION_DOWN || event.getRepeatCount() > 0) return true;
        if (delete) {
            if (typed.length() > 0) typed.setLength(typed.length() - 1);
            render();
            return true;
        }
        typed.append(digit);
        if (typed.length() < Pin.LENGTH) {
            render();
            return true;
        }
        String pin = typed.toString();
        clear();
        listener.onPin(pin);
        return true;
    }

    private void render() {
        for (int i = 0; i < Pin.LENGTH; i++) {
            cells[i].setSelected(i == typed.length());
            cells[i].setText(i < typed.length() ? "•" : "");
        }
    }
}
