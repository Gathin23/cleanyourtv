package io.github.maxhayim.manager;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The tile and module card open this: the project's add-ons, themes and switches, as its repository lists them,
 * with what is installed on this TV. A section on the left, its items on the right; OK opens one, with its
 * screenshot. Nothing is installed from here: each item shows its creator's install command for a computer.
 */
public class MainActivity extends Activity {
    private static final String[] SECTIONS = {"Add-ons", "Themes", "Switches"};
    private static final Catalog.Kind[] KINDS = {Catalog.Kind.ADDON, Catalog.Kind.THEME, Catalog.Kind.SWITCH};

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService work = Executors.newSingleThreadExecutor();
    private LinearLayout menu, rows;
    private TextView status;
    private int section;
    private boolean loaded;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(0xFF0D1117);
        page.setPadding(px(48), px(32), px(48), px(24));
        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextSize(28);
        title.setTextColor(0xFFFFFFFF);
        status = new TextView(this);
        status.setTextSize(15);
        status.setTextColor(0xFF8B949E);
        status.setPadding(0, 0, 0, px(16));
        page.addView(title);
        page.addView(status);
        LinearLayout panes = new LinearLayout(this);
        menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        panes.addView(menu, new LinearLayout.LayoutParams(px(200), ViewGroup.LayoutParams.WRAP_CONTENT));
        View divider = new View(this);
        divider.setBackgroundColor(0x22FFFFFF);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(px(1), ViewGroup.LayoutParams.MATCH_PARENT);
        dlp.setMargins(px(16), 0, px(16), 0);
        panes.addView(divider, dlp);
        ScrollView scroll = new ScrollView(this);
        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(rows);
        panes.addView(scroll, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        page.addView(panes, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(page);
        for (int i = 0; i < SECTIONS.length; i++) {
            int index = i;
            Row item = new Row(this, SECTIONS[i], null, () -> {
                if (rows.getChildCount() > 0) rows.getChildAt(0).requestFocus();
            });
            item.setOnFocusChangeListener((v, has) -> {
                if (has && section != index) {
                    section = index;
                    fill();
                }
            });
            menu.addView(item, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        menu.getChildAt(0).requestFocus();
        status.setText("Reading the project's repository…");
    }

    @Override
    protected void onResume() {
        super.onResume();
        work.execute(() -> {
            Runnable show = () -> main.post(() -> {
                if (isFinishing()) return;
                loaded = true;
                fill();
            });
            // Once with the lists, again when it knows which add-ons are on this TV.
            Repo.load(this, show);
            show.run();
        });
        if (loaded) fill();
    }

    @Override
    protected void onDestroy() {
        work.shutdownNow();
        super.onDestroy();
    }

    private void fill() {
        rows.removeAllViews();
        Catalog.Kind kind = KINDS[section];
        List<Catalog.Item> items = kind == Catalog.Kind.ADDON ? Repo.addons : kind == Catalog.Kind.THEME ? Repo.themes : Repo.switches;
        int installed = 0;
        for (Catalog.Item a : Repo.addons) if (Repo.installed(this, a)) installed++;
        if (loaded) {
            status.setText((Repo.fresh ? "" : "Offline: last time's copy. ") + Repo.addons.size() + " add-ons ("
                    + installed + " on this TV), " + Repo.themes.size() + " themes, " + Repo.switches.size() + " switches.");
        }
        if (items.isEmpty() && loaded) {
            rows.addView(new Row(this, "Couldn't read the repository", "Check the TV's connection; Back and open again.", null));
            return;
        }
        for (Catalog.Item i : items) {
            Row r = new Row(this, kind == Catalog.Kind.SWITCH ? i.id : i.name, shorten(i.what), () ->
                    startActivity(new Intent(this, DetailActivity.class).putExtra("kind", i.kind.name()).putExtra("id", i.id)));
            if (kind == Catalog.Kind.ADDON && Repo.installed(this, i)) r.setValue("Installed");
            rows.addView(r, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    private static String shorten(String s) {
        return s == null ? null : s.length() > 110 ? s.substring(0, 108).trim() + "…" : s;
    }

    private int px(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
