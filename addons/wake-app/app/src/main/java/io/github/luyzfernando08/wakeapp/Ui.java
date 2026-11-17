package io.github.luyzfernando08.wakeapp;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** The two screens share one look: a dark page, soft rounded cards, a teal accent on what is chosen. */
final class Ui {
    private Ui() { }

    static final int ACCENT = 0xFF4FB3A9;

    static int dp(Context ctx, int v) {
        return Math.round(v * ctx.getResources().getDisplayMetrics().density);
    }

    static LinearLayout page(Context ctx) {
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(0xFF0B0D12);
        int pad = dp(ctx, 48);
        root.setPadding(pad, pad, pad, pad);
        return root;
    }

    static void heading(LinearLayout root, int title, int hint) {
        Context ctx = root.getContext();
        TextView t = new TextView(ctx);
        t.setText(title);
        t.setTextColor(Color.WHITE);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 34);
        root.addView(t);
        TextView h = new TextView(ctx);
        h.setText(hint);
        h.setTextColor(0xB3FFFFFF);
        h.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        h.setPadding(0, dp(ctx, 8), 0, dp(ctx, 24));
        root.addView(h);
    }

    /** A focusable, rounded background: brighter when focused, with a teal outline when {@code chosen}. */
    static Drawable card(Context ctx, int radiusDp, boolean chosen) {
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_focused}, shape(ctx, 0x40FFFFFF, radiusDp, chosen));
        bg.addState(new int[]{}, shape(ctx, 0x14FFFFFF, radiusDp, chosen));
        return bg;
    }

    private static GradientDrawable shape(Context ctx, int color, int radiusDp, boolean chosen) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(ctx, radiusDp));
        if (chosen) d.setStroke(dp(ctx, 3), ACCENT);
        return d;
    }

    static TextView row(LinearLayout root) {
        Context ctx = root.getContext();
        TextView row = new TextView(ctx);
        row.setTextColor(Color.WHITE);
        row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setSingleLine(true);
        row.setPadding(dp(ctx, 24), dp(ctx, 14), dp(ctx, 24), dp(ctx, 14));
        row.setFocusable(true);
        row.setClickable(true);
        row.setBackground(card(ctx, 28, false));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(ctx, 560),
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(ctx, 10);
        root.addView(row, lp);
        return row;
    }

    /** The app's TV banner when it has one, else its icon on a dark tile; rounded either way. */
    static ImageView art(Context ctx, String pkg, int widthDp, int heightDp) {
        PackageManager pm = ctx.getPackageManager();
        Drawable d = null;
        boolean banner = false;
        if (pkg != null && !pkg.isEmpty()) {
            try {
                Intent main = pm.getLeanbackLaunchIntentForPackage(pkg);
                ResolveInfo info = main == null ? null : pm.resolveActivity(main, 0);
                if (info != null) d = info.activityInfo.loadBanner(pm);
                if (d == null) d = pm.getApplicationBanner(pkg);
                banner = d != null;
                if (d == null) d = pm.getApplicationIcon(pkg);
            } catch (PackageManager.NameNotFoundException e) {
                d = null;
            }
        }
        ImageView view = new ImageView(ctx);
        if (d == null) d = ctx.getDrawable(R.drawable.banner);
        view.setImageDrawable(d);
        view.setBackgroundColor(0xFF1B1F27);
        if (banner || pkg == null || pkg.isEmpty()) {
            view.setScaleType(ImageView.ScaleType.CENTER_CROP);
        } else {
            view.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int pad = dp(ctx, heightDp / 5);
            view.setPadding(pad, pad, pad, pad);
        }
        final float radius = dp(ctx, 12);
        view.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline outline) {
                outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius);
            }
        });
        view.setClipToOutline(true);
        view.setLayoutParams(new LinearLayout.LayoutParams(dp(ctx, widthDp), dp(ctx, heightDp)));
        return view;
    }
}
