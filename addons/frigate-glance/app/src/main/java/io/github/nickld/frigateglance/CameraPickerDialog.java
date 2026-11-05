package io.github.nickld.frigateglance;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Map;
import java.util.function.Consumer;

/** "Which camera?" for a test card: one row per camera, OK picks it, Back closes. */
final class CameraPickerDialog extends Dialog {
    /** cameras: Frigate's camera name to the name shown. */
    CameraPickerDialog(Context context, Map<String, String> cameras, Consumer<String> onPicked) {
        super(context);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        float dp = context.getResources().getDisplayMetrics().density;
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundResource(R.drawable.card_glass);
        int pad = Math.round(24 * dp);
        box.setPadding(pad, pad, pad, pad);
        TextView title = new TextView(context);
        title.setText("Show a test card for");
        title.setTextSize(20);
        title.setTextColor(0xFFFFFFFF);
        title.setPadding(Math.round(16 * dp), 0, 0, Math.round(8 * dp));
        box.addView(title);
        LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        for (Map.Entry<String, String> cam : cameras.entrySet()) {
            list.addView(new SettingRow(context, cam.getValue(), "", () -> {
                dismiss();
                onPicked.accept(cam.getKey());
            }), new LinearLayout.LayoutParams(Math.round(360 * dp), LinearLayout.LayoutParams.WRAP_CONTENT));
        }
        ScrollView scroll = new ScrollView(context);
        scroll.addView(list);
        box.addView(scroll);
        setContentView(box);
        Window w = getWindow();
        if (w != null) w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        if (list.getChildCount() > 0) list.getChildAt(0).requestFocus();
    }
}
