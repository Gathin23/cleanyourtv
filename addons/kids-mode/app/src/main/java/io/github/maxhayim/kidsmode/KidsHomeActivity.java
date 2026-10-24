package io.github.maxhayim.kidsmode;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Home. With Kids Mode off it hands straight over to the normal home app, so Home looks as it always did. With it
 * on, the kids' screen: big tiles for the allowed apps and a Grown-ups tile that asks for the PIN. Back does
 * nothing here.
 */
public class KidsHomeActivity extends Activity {
    private static final int COLUMNS = 4;

    private LinearLayout grid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!Store.on(this)) {
            handOver();
            return;
        }
        build();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (!Store.on(this)) handOver();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!Store.on(this)) {
            handOver();
            return;
        }
        if (grid == null) build();
        else fill();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.KEYCODE_BACK && Store.on(this)) return true;
        return super.dispatchKeyEvent(e);
    }

    /** Kids Mode off: the normal home app, as if this one weren't here. */
    private void handOver() {
        ComponentName home = Store.normalHome(this);
        if (home == null) {
            List<Apps.App> others = Apps.otherHomes(this);
            if (!others.isEmpty()) {
                home = others.get(0).component;
                Store.setNormalHome(this, home);
            }
        }
        if (home != null) {
            try {
                startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setComponent(home)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION));
                overridePendingTransition(0, 0);
                finish();
                return;
            } catch (ActivityNotFoundException | SecurityException ignored) {
                // The normal home app is gone: the parents' screen lets them pick another.
            }
        }
        startActivity(new Intent(this, ParentActivity.class));
        finish();
    }

    private void build() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(0xFF14213D);
        page.setPadding(px(56), px(36), px(56), px(24));
        TextView title = new TextView(this);
        title.setText("Kids");
        title.setTextSize(40);
        title.setTextColor(0xFFFFC83D);
        page.addView(title);
        ScrollView scroll = new ScrollView(this);
        grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setClipChildren(false);
        scroll.setClipChildren(false);
        scroll.addView(grid);
        page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(page);
        fill();
    }

    private void fill() {
        grid.removeAllViews();
        Set<String> allowed = Store.allowed(this);
        List<Apps.App> apps = new ArrayList<>();
        for (Apps.App a : Apps.tvApps(this)) if (allowed.contains(a.pkg)) apps.add(a);
        List<View> tiles = new ArrayList<>();
        for (Apps.App a : apps) tiles.add(tile(a.label, Apps.art(this, a), () -> open(a)));
        tiles.add(tile("Grown-ups", getDrawable(R.drawable.ic_lock), this::grownUps));
        LinearLayout row = null;
        for (int i = 0; i < tiles.size(); i++) {
            if (i % COLUMNS == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setClipChildren(false);
                grid.addView(row);
            }
            row.addView(tiles.get(i));
        }
        tiles.get(0).requestFocus();
    }

    private View tile(String label, Drawable art, Runnable onClick) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        int w = px(196), h = px(110);
        FrameLayout card = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(px(16));
        bg.setColor(0xFF22335A);
        card.setBackground(bg);
        card.setClipToOutline(true);
        ImageView img = new ImageView(this);
        img.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (art != null) img.setImageDrawable(art);
        card.addView(img, new FrameLayout.LayoutParams(-1, -1));
        cell.addView(card, new LinearLayout.LayoutParams(w, h));
        TextView name = new TextView(this);
        name.setText(label);
        name.setTextSize(16);
        name.setTextColor(0xFFFFFFFF);
        name.setGravity(Gravity.CENTER);
        name.setPadding(0, px(8), 0, 0);
        cell.addView(name);
        cell.setFocusable(true);
        cell.setClickable(true);
        cell.setOnClickListener(v -> onClick.run());
        cell.setOnFocusChangeListener((v, has) -> {
            float s = has ? 1.1f : 1f;
            v.animate().scaleX(s).scaleY(s).setDuration(150).start();
            bg.setStroke(has ? px(4) : 0, 0xFFFFC83D);
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(px(14), px(18), px(14), px(10));
        cell.setLayoutParams(lp);
        return cell;
    }

    private void open(Apps.App a) {
        try {
            startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                    .setComponent(a.component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
        } catch (ActivityNotFoundException | SecurityException ignored) {
            // Uninstalled meanwhile: the next visit leaves it out.
        }
    }

    private void grownUps() {
        long wait = Store.waitLeft(this);
        if (wait > 0) {
            toast("Too many tries. Try again in " + ((wait + 999) / 1000) + " seconds.");
            return;
        }
        Prompt.ask(this, Store.usesPassword(this) ? "Password" : "PIN", Store.usesPassword(this), v -> {
            if (Store.check(this, v)) {
                Store.setOn(this, false);
                handOver();
            } else {
                long w = Store.waitLeft(this);
                toast(w > 0 ? "Not right. Try again in " + ((w + 999) / 1000) + " seconds." : "Not right.");
            }
        });
    }

    private void toast(String s) {
        android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_LONG).show();
    }

    private int px(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
