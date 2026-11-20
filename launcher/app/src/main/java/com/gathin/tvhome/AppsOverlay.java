package com.gathin.tvhome;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * desktop: every installed app as icons in a full-screen grid over the darkened wallpaper, like a desktop's app
 * launcher. OK opens an app, Back or Home closes the grid. Focus never leaves it while it is open.
 */
final class AppsOverlay extends FrameLayout {

    private static final int COLUMNS = 7;

    private final Runnable onClosed;
    private final float density;
    private boolean closing;

    private static final class App {
        final String label;
        final ComponentName component;
        final boolean tv;
        final Drawable icon;
        final int[] backdrop;

        App(String label, ComponentName component, boolean tv, Drawable icon) {
            this.label = label;
            this.component = component;
            this.tv = tv;
            this.icon = icon;
            this.backdrop = AppAdapter.backdrop(icon, AppAdapter.dominantColor(icon));
        }
    }

    AppsOverlay(Context ctx, Set<String> hidden, Runnable onClosed) {
        super(ctx);
        this.onClosed = onClosed;
        density = ctx.getResources().getDisplayMetrics().density;
        setBackgroundColor(0x990B0D10);
        setClickable(true);

        TextView title = new TextView(ctx);
        title.setText(R.string.app_library);
        title.setTextColor(0xCCFFFFFF);
        title.setTextSize(18);
        title.setTypeface(getResources().getFont(R.font.outfit_medium));
        LayoutParams tlp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_HORIZONTAL | Gravity.TOP);
        tlp.topMargin = dp(40);
        addView(title, tlp);

        List<App> apps = load(ctx, hidden);
        RecyclerView grid = new RecyclerView(ctx);
        grid.setLayoutManager(new GridLayoutManager(ctx, COLUMNS));
        grid.setClipToPadding(false);
        grid.setClipChildren(false);
        grid.setPadding(0, dp(16), 0, dp(40));
        grid.setOverScrollMode(View.OVER_SCROLL_NEVER);
        grid.setAdapter(new Adapter(apps));
        LayoutParams glp = new LayoutParams(dp(COLUMNS * 116), ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER_HORIZONTAL);
        glp.topMargin = dp(84);
        addView(grid, glp);
        if (!apps.isEmpty()) grid.post(() -> {
            RecyclerView.ViewHolder first = grid.findViewHolderForAdapterPosition(0);
            if (first != null) first.itemView.requestFocus();
        });
    }

    private int dp(float v) {
        return Math.round(v * density);
    }

    /** TV entries first (as the stock launcher opens them), then phone apps that have no TV entry; alphabetical. */
    private static List<App> load(Context ctx, Set<String> hidden) {
        PackageManager pm = ctx.getPackageManager();
        Map<String, App> byPkg = new HashMap<>();
        String[] categories = {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER};
        for (String category : categories) {
            boolean tv = Intent.CATEGORY_LEANBACK_LAUNCHER.equals(category);
            for (ResolveInfo ri : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category), 0)) {
                if (ri.activityInfo == null || !ri.activityInfo.exported) continue;
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(ctx.getPackageName()) || hidden.contains(pkg) || byPkg.containsKey(pkg)) continue;
                byPkg.put(pkg, new App(Cards.cleanLabel(String.valueOf(ri.loadLabel(pm)), pkg),
                        new ComponentName(pkg, ri.activityInfo.name), tv, ri.loadIcon(pm)));
            }
        }
        List<App> apps = new ArrayList<>(byPkg.values());
        Collections.sort(apps, (a, b) -> a.label.compareToIgnoreCase(b.label));
        return apps;
    }

    void show() {
        setAlpha(0f);
        setScaleX(1.04f);
        setScaleY(1.04f);
        animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(180).start();
    }

    void close() {
        if (closing) return;
        closing = true;
        animate().alpha(0f).setDuration(140).withEndAction(() -> {
            ViewGroup parent = (ViewGroup) getParent();
            if (parent != null) parent.removeView(this);
            onClosed.run();
        }).start();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == KeyEvent.ACTION_UP) close();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    /** Keep the D-pad inside the grid: past its edges focus stays where it is instead of reaching Home below. */
    @Override
    public View focusSearch(View focused, int direction) {
        View next = super.focusSearch(focused, direction);
        for (View v = next; v != null; v = v.getParent() instanceof View ? (View) v.getParent() : null) {
            if (v == this) return next;
        }
        return focused;
    }

    private void open(App app) {
        Intent i = new Intent(Intent.ACTION_MAIN).setComponent(app.component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        if (app.tv) i.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        try {
            getContext().startActivity(i);
        } catch (ActivityNotFoundException | SecurityException ignored) {
            return;
        }
        close();
    }

    private final class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final List<App> apps;

        Adapter(List<App> apps) {
            this.apps = apps;
        }

        @Override
        public int getItemCount() {
            return apps.size();
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Context ctx = parent.getContext();
            LinearLayout item = new LinearLayout(ctx);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER_HORIZONTAL);
            item.setPadding(dp(6), dp(10), dp(6), dp(8));
            item.setFocusable(true);
            item.setClickable(true);
            GradientDrawable plate = new GradientDrawable();
            plate.setCornerRadius(dp(16));
            plate.setColor(0x00FFFFFF);
            item.setBackground(plate);
            item.setOnFocusChangeListener((v, focused) -> plate.setColor(focused ? 0x2EFFFFFF : 0x00FFFFFF));

            int side = dp(64);
            FrameLayout tile = new FrameLayout(ctx);
            tile.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), MacDock.radiusPx(view.getWidth()));
                }
            });
            tile.setClipToOutline(true);
            ImageView icon = new ImageView(ctx);
            int iconSide = Math.round(side * 0.62f);
            tile.addView(icon, new LayoutParams(iconSide, iconSide, Gravity.CENTER));
            item.addView(tile, new LinearLayout.LayoutParams(side, side));

            TextView name = new TextView(ctx);
            name.setTextColor(0xEBFFFFFF);
            name.setTextSize(13);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            name.setGravity(Gravity.CENTER_HORIZONTAL);
            name.setTypeface(getResources().getFont(R.font.outfit_regular));
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(dp(100), ViewGroup.LayoutParams.WRAP_CONTENT);
            nlp.topMargin = dp(8);
            item.addView(name, nlp);
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(dp(4), dp(4), dp(4), dp(4));
            item.setLayoutParams(lp);
            return new RecyclerView.ViewHolder(item) { };
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            App app = apps.get(position);
            LinearLayout item = (LinearLayout) holder.itemView;
            FrameLayout tile = (FrameLayout) item.getChildAt(0);
            tile.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, app.backdrop));
            ((ImageView) tile.getChildAt(0)).setImageDrawable(app.icon);
            ((TextView) item.getChildAt(1)).setText(app.label);
            item.setContentDescription(app.label);
            item.setOnClickListener(v -> open(app));
        }
    }
}
