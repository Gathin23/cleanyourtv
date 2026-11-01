package io.github.maxhayim.remoteaccess;

import android.content.Context;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * One settings row: a name with a line saying what it does on the left, its value on the right; OK runs it.
 */
final class Row extends LinearLayout {
    private final TextView value;
    private final TextView desc;

    Row(Context context, String name, String description, Runnable onClick) {
        super(context);
        float dp = context.getResources().getDisplayMetrics().density;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        int ph = Math.round(16 * dp), pv = Math.round(10 * dp);
        setPadding(ph, pv, ph, pv);
        setBackgroundResource(R.drawable.row_background);
        setFocusable(onClick != null);
        setClickable(onClick != null);
        if (onClick != null) setOnClickListener(v -> onClick.run());

        LinearLayout text = new LinearLayout(context);
        text.setOrientation(VERTICAL);
        TextView label = new TextView(context);
        label.setText(name);
        label.setTextSize(17);
        label.setTextColor(0xFFFFFFFF);
        text.addView(label);
        desc = new TextView(context);
        desc.setTextSize(12);
        desc.setTextColor(0xFF8B949E);
        desc.setPadding(0, Math.round(2 * dp), Math.round(16 * dp), 0);
        text.addView(desc);
        setDetail(description);
        addView(text, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        value = new TextView(context);
        value.setTextSize(17);
        value.setTextColor(0xFFC9D1D9);
        value.setGravity(Gravity.END);
        value.setMinWidth(Math.round(90 * dp));
        addView(value, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
    }

    void setDetail(String d) {
        desc.setText(d == null ? "" : d);
        desc.setVisibility(d == null || d.isEmpty() ? GONE : VISIBLE);
    }

    void setValue(String v) {
        value.setText(v);
    }
}
