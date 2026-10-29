package io.github.maxhayim.startupbranding;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.InputFilter;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * The layout every screen shares: black, a title and a line under it, then rows worked with the D-pad. {@link #rows}
 * scrolls; a screen with a side panel (the picture screen) adds it to {@link #side}.
 */
final class Page {
    final LinearLayout root, rows, side;
    final TextView title, subtitle;
    private final Activity a;

    Page(Activity a, String titleText, boolean withSide) {
        this.a = a;
        root = new LinearLayout(a);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setBackgroundColor(0xFF000000);
        root.setPadding(px(56), px(36), px(56), px(28));

        LinearLayout main = new LinearLayout(a);
        main.setOrientation(LinearLayout.VERTICAL);
        title = new TextView(a);
        title.setText(titleText);
        title.setTextSize(28);
        title.setTextColor(0xFFFFFFFF);
        main.addView(title);
        subtitle = new TextView(a);
        subtitle.setTextSize(15);
        subtitle.setTextColor(0xFF8B949E);
        subtitle.setPadding(0, px(4), 0, px(16));
        main.addView(subtitle);
        ScrollView scroll = new ScrollView(a);
        scroll.setVerticalScrollBarEnabled(false);
        rows = new LinearLayout(a);
        rows.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(rows);
        main.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(main, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        side = new LinearLayout(a);
        side.setOrientation(LinearLayout.VERTICAL);
        side.setPadding(px(36), px(56), 0, 0);
        if (withSide) root.addView(side, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.1f));
        a.setContentView(root);
    }

    Row add(String name, String detail, Runnable onClick) {
        Row r = new Row(a, name, detail, onClick);
        rows.addView(r, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return r;
    }

    /** A small heading between groups of rows. */
    void heading(String s) {
        TextView t = new TextView(a);
        t.setText(s);
        t.setTextSize(14);
        t.setTextColor(0xFFF2B84B);
        t.setPadding(px(16), px(14), 0, px(4));
        rows.addView(t);
    }

    /** Keeps focus on the row it was on (by index) after the rows are rebuilt, else the first. */
    void focus(int index) {
        int i = 0, n = 0;
        for (int k = 0; k < rows.getChildCount(); k++) {
            if (!rows.getChildAt(k).isFocusable()) continue;
            if (n == index || n == 0) i = k;
            if (n == index) break;
            n++;
        }
        if (rows.getChildCount() > 0) rows.getChildAt(i).requestFocus();
    }

    /** The index among focusable rows of the one with focus, or 0. */
    int focused() {
        int n = 0;
        for (int k = 0; k < rows.getChildCount(); k++) {
            if (!rows.getChildAt(k).isFocusable()) continue;
            if (rows.getChildAt(k).hasFocus()) return n;
            n++;
        }
        return 0;
    }

    interface Typed {
        void typed(String s);
    }

    /** One line of text, typed with the TV's keyboard. Clear empties it. */
    void ask(String titleText, String current, int max, Typed done) {
        EditText field = new EditText(a);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        field.setSingleLine(true);
        field.setFilters(new InputFilter[]{new InputFilter.LengthFilter(max)});
        field.setText(current);
        field.setSelection(field.getText().length());
        new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(titleText)
                .setView(field)
                .setPositiveButton("OK", (d, w) -> done.typed(field.getText().toString()))
                .setNeutralButton("Clear", (d, w) -> done.typed(""))
                .setNegativeButton("Cancel", null)
                .show();
        field.requestFocus();
    }

    int px(int dp) {
        return Math.round(dp * a.getResources().getDisplayMetrics().density);
    }
}
