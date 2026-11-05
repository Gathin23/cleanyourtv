package io.github.nickld.frigateglance;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.KeyEvent;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.function.Consumer;

/**
 * "Press the button you want": the next button pressed on the remote becomes the key, if KeyChoice allows it.
 * Back closes the window without a change. Buttons the system or the launcher keep for themselves (Home, the
 * app buttons) never arrive here, so they cannot be picked.
 */
final class KeyPickerDialog extends Dialog {
    private final TextView message;

    KeyPickerDialog(Context context, String what, java.util.Map<String, String> others, Consumer<String> onPicked) {
        super(context);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        float dp = context.getResources().getDisplayMetrics().density;
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundResource(R.drawable.card_glass);
        int pad = Math.round(28 * dp);
        box.setPadding(pad, pad, pad, pad);
        TextView title = new TextView(context);
        title.setText("Press the button you want for " + what);
        title.setTextSize(22);
        title.setTextColor(0xFFFFFFFF);
        message = new TextView(context);
        message.setTextSize(15);
        message.setTextColor(0xFF8B949E);
        message.setPadding(0, Math.round(10 * dp), 0, Math.round(10 * dp));
        message.setText("It only acts while a camera card is on screen. OK, Back and Home can't be used.");
        TextView hint = new TextView(context);
        hint.setText("Back closes this without a change.");
        hint.setTextSize(13);
        hint.setTextColor(0xFF8B949E);
        box.addView(title);
        box.addView(message);
        box.addView(hint);
        setContentView(box);
        Window w = getWindow();
        if (w != null) w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        setOnKeyListener((d, code, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN || event.getRepeatCount() > 0) return true;
            if (code == KeyEvent.KEYCODE_BACK) {
                dismiss();
                return true;
            }
            String name = KeyEvent.keyCodeToString(code);
            String problem = KeyChoice.problem(name, others);
            if (problem != null) {
                message.setText(problem);
                message.setTextColor(0xFFFF7B72);
                return true;
            }
            onPicked.accept(name);
            dismiss();
            return true;
        });
    }
}
