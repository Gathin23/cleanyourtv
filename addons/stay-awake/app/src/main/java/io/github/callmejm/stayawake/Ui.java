package io.github.callmejm.stayawake;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Rounded rows that light up when focused. */
final class Ui {
    private Ui() {}

    static TextView text(Context ctx, int sp, int color) {
        TextView view = new TextView(ctx);
        view.setTextColor(color);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        return view;
    }

    static TextView row(LinearLayout parent, int widthDp) {
        Context ctx = parent.getContext();
        TextView row = text(ctx, 20, Color.WHITE);
        row.setGravity(Gravity.CENTER);
        row.setPadding(dp(ctx, 24), dp(ctx, 14), dp(ctx, 24), dp(ctx, 14));
        row.setFocusable(true);
        row.setClickable(true);
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_focused}, pill(ctx, 0x403DDC97));
        bg.addState(new int[]{}, pill(ctx, 0x14FFFFFF));
        row.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(ctx, widthDp),
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(ctx, 10);
        parent.addView(row, lp);
        return row;
    }

    private static GradientDrawable pill(Context ctx, int color) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(ctx, 28));
        return d;
    }

    static int dp(Context ctx, int v) {
        return Math.round(v * ctx.getResources().getDisplayMetrics().density);
    }
}
