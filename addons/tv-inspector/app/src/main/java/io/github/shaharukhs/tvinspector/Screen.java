package io.github.shaharukhs.tvinspector;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** The plain dark column every screen of the add-on is built from. */
abstract class Screen extends Activity {
    static final int BACKGROUND = Color.rgb(21, 32, 43);
    LinearLayout column;

    void layout() {
        ScrollView scroll = new ScrollView(this);
        column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(48, 32, 48, 32);
        column.setLayoutDirection(View.LAYOUT_DIRECTION_LOCALE);
        scroll.setBackgroundColor(BACKGROUND);
        scroll.addView(column);
        setContentView(scroll);
    }

    TextView text(String value, int size) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextSize(size); view.setTextColor(Color.WHITE);
        view.setGravity(Gravity.START); view.setTextDirection(View.TEXT_DIRECTION_LOCALE);
        view.setPadding(12, 14, 12, 14);
        column.addView(view, new LinearLayout.LayoutParams(-1, -2));
        return view;
    }

    /** A focusable block, so the D-pad walks the page and the scroll view follows the focus. */
    TextView section(String title, String body) {
        TextView view = text(title + "\n" + body, 18);
        view.setFocusable(true);
        StateListDrawable background = new StateListDrawable();
        GradientDrawable focused = new GradientDrawable();
        focused.setColor(Color.rgb(44, 62, 80)); focused.setCornerRadius(12);
        background.addState(new int[]{android.R.attr.state_focused}, focused);
        background.addState(new int[]{}, new ColorDrawable(Color.TRANSPARENT));
        view.setBackground(background);
        return view;
    }

    Button button(String value, Runnable action) {
        Button view = new Button(this); view.setText(value);
        view.setOnClickListener(v -> action.run());
        column.addView(view, new LinearLayout.LayoutParams(-1, -2));
        return view;
    }
}
