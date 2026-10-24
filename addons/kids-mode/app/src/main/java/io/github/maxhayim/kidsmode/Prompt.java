package io.github.maxhayim.kidsmode;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Asks for the PIN or password. A PIN is typed on a number pad worked with the D-pad (the remote's number keys
 * work too, where it has them); a password with the TV's keyboard.
 */
final class Prompt {
    interface Done {
        void done(String value);
    }

    private Prompt() {
    }

    static void ask(Activity a, String title, boolean password, Done done) {
        if (password) askPassword(a, title, done);
        else askPin(a, title, done);
    }

    private static void askPassword(Activity a, String title, Done done) {
        EditText field = new EditText(a);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        field.setSingleLine(true);
        new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(title)
                .setView(field)
                .setPositiveButton("OK", (d, w) -> done.done(field.getText().toString()))
                .setNegativeButton("Cancel", null)
                .show();
        field.requestFocus();
    }

    private static void askPin(Activity a, String title, Done done) {
        float dp = a.getResources().getDisplayMetrics().density;
        Dialog d = new Dialog(a, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar);
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = Math.round(24 * dp);
        box.setPadding(pad, pad, pad, pad);
        TextView t = new TextView(a);
        t.setText(title);
        t.setTextSize(20);
        t.setTextColor(0xFFFFFFFF);
        box.addView(t);
        TextView dots = new TextView(a);
        dots.setTextSize(30);
        dots.setTextColor(0xFFFFC83D);
        dots.setLetterSpacing(0.3f);
        dots.setMinHeight(Math.round(48 * dp));
        dots.setGravity(Gravity.CENTER);
        box.addView(dots);
        StringBuilder pin = new StringBuilder();
        Runnable show = () -> {
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < pin.length(); i++) b.append('●');
            dots.setText(b);
        };
        Runnable ok = () -> {
            d.dismiss();
            done.done(pin.toString());
        };
        String[] keys = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "⌫", "0", "OK"};
        Button first = null;
        for (int r = 0; r < 4; r++) {
            LinearLayout row = new LinearLayout(a);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int col = 0; col < 3; col++) {
                String k = keys[r * 3 + col];
                Button b = new Button(a);
                b.setText(k);
                b.setTextSize(22);
                b.setAllCaps(false);
                GradientDrawable bg = new GradientDrawable();
                bg.setCornerRadius(12 * dp);
                bg.setColor(0x22FFFFFF);
                b.setBackground(bg);
                b.setOnFocusChangeListener((v, has) -> bg.setColor(has ? 0x66FFC83D : 0x22FFFFFF));
                b.setTextColor(0xFFFFFFFF);
                b.setOnClickListener(v -> {
                    if (k.equals("OK")) ok.run();
                    else if (k.equals("⌫")) {
                        if (pin.length() > 0) pin.setLength(pin.length() - 1);
                    } else if (pin.length() < Lock.MAX_PIN) pin.append(k);
                    show.run();
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Math.round(84 * dp), Math.round(60 * dp));
                int m = Math.round(5 * dp);
                lp.setMargins(m, m, m, m);
                row.addView(b, lp);
                if (first == null) first = b;
            }
            box.addView(row);
        }
        d.setContentView(box, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        d.setOnKeyListener((dialog, code, e) -> {
            if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (code >= KeyEvent.KEYCODE_0 && code <= KeyEvent.KEYCODE_9) {
                if (pin.length() < Lock.MAX_PIN) pin.append((char) ('0' + code - KeyEvent.KEYCODE_0));
                show.run();
                return true;
            }
            if (code == KeyEvent.KEYCODE_DEL) {
                if (pin.length() > 0) pin.setLength(pin.length() - 1);
                show.run();
                return true;
            }
            return false;
        });
        d.show();
        View f = first;
        if (f != null) f.requestFocus();
    }
}
