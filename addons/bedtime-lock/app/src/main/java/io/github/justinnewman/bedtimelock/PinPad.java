package io.github.justinnewman.bedtimelock;

import android.view.KeyEvent;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.Arrays;

/**
 * Four digit boxes driven by the remote: a number key types a digit and moves on; Up and Down pick the digit in the
 * current box, Right or OK moves on, Left goes back. The fourth digit hands the PIN to the listener.
 */
final class PinPad {
    interface Listener {
        void onPin(String pin);
    }

    private final TextView[] cells = new TextView[Pin.LENGTH];
    private final int[] digits = new int[Pin.LENGTH];
    private final Listener listener;
    private int at;

    PinPad(ViewGroup row, Listener listener) {
        for (int i = 0; i < Pin.LENGTH; i++) cells[i] = (TextView) row.getChildAt(i);
        this.listener = listener;
        clear();
    }

    void clear() {
        Arrays.fill(digits, -1);
        at = 0;
        render();
    }

    /** True when the key belongs to the PIN pad (both its down and its up), so the caller passes nothing on. */
    boolean onKey(KeyEvent event) {
        int code = event.getKeyCode();
        int typed = Pin.digitOf(code);
        boolean ours = typed >= 0 || code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN
                || code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT
                || code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER || code == KeyEvent.KEYCODE_DEL;
        if (!ours) return false;
        if (event.getAction() != KeyEvent.ACTION_DOWN) return true;
        if (typed >= 0) {
            digits[at] = typed;
            next();
        } else if (code == KeyEvent.KEYCODE_DPAD_UP) {
            digits[at] = Pin.up(digits[at]);
        } else if (code == KeyEvent.KEYCODE_DPAD_DOWN) {
            digits[at] = Pin.down(digits[at]);
        } else if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DEL) {
            if (digits[at] < 0 && at > 0) at--;
            digits[at] = -1;
        } else if (digits[at] >= 0) {
            next();
        }
        render();
        return true;
    }

    private void next() {
        if (at < Pin.LENGTH - 1) {
            at++;
            return;
        }
        StringBuilder pin = new StringBuilder(Pin.LENGTH);
        for (int d : digits) pin.append(d);
        clear();
        listener.onPin(pin.toString());
    }

    /** The box being entered shows its digit while Up and Down pick it; the boxes before it show a dot. */
    private void render() {
        for (int i = 0; i < Pin.LENGTH; i++) {
            cells[i].setSelected(i == at);
            cells[i].setText(digits[i] < 0 ? "" : i == at ? String.valueOf(digits[i]) : "•");
        }
    }
}
