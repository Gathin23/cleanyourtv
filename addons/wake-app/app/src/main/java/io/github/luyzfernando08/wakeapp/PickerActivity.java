package io.github.luyzfernando08.wakeapp;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Every app that can be opened from a launcher, as a grid of cards with its picture; OK chooses one. */
public class PickerActivity extends Activity {
    private static final int CELL_DP = 190, ART_W = 168, ART_H = 94;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout page = Ui.page(this);
        Ui.heading(page, R.string.pick_title, R.string.pick_hint);

        int widthDp = Math.round(getResources().getDisplayMetrics().widthPixels
                / getResources().getDisplayMetrics().density);
        int columns = Math.max(3, (widthDp - 96) / CELL_DP);
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(columns);

        ScrollView scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
        scroll.addView(grid);
        page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, 0, 1f));

        String current = Prefs.app(this);
        View focus = null;
        for (String[] entry : apps()) {
            boolean chosen = entry[1].equals(current);
            View cell = cell(entry[0], entry[1], chosen);
            cell.setOnClickListener(v -> {
                Prefs.setApp(this, entry[1]);
                finish();
            });
            grid.addView(cell);
            if (focus == null || chosen) focus = cell;
        }
        setContentView(page);
        if (focus != null) focus.requestFocus();
    }

    private View cell(String label, String pkg, boolean chosen) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = Ui.dp(this, 10);
        cell.setPadding(pad, pad, pad, pad);
        cell.setFocusable(true);
        cell.setClickable(true);
        cell.setBackground(Ui.card(this, 16, chosen));
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = Ui.dp(this, CELL_DP - 12);
        lp.setMargins(Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6));
        cell.setLayoutParams(lp);

        cell.addView(Ui.art(this, pkg, ART_W, ART_H));
        TextView name = new TextView(this);
        name.setText(label);
        name.setTextColor(chosen ? Ui.ACCENT : Color.WHITE);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setGravity(Gravity.CENTER);
        name.setPadding(0, Ui.dp(this, 8), 0, 0);
        cell.addView(name, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        // A small lift on the focused card, so the choice reads from across the room.
        cell.setOnFocusChangeListener((v, focused) ->
                v.animate().scaleX(focused ? 1.06f : 1f).scaleY(focused ? 1.06f : 1f).setDuration(120).start());
        return cell;
    }

    /** {label, package} for each launchable app except this one and the home screens, sorted by label. */
    private List<String[]> apps() {
        PackageManager pm = getPackageManager();
        List<ResolveInfo> found = pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0);
        if (found.isEmpty()) {
            found = pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0);
        }
        List<String[]> apps = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        for (ResolveInfo info : found) {
            String pkg = info.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || seen.contains(pkg) || Launch.isHome(this, pkg)) continue;
            seen.add(pkg);
            apps.add(new String[]{info.loadLabel(pm).toString(), pkg});
        }
        Collections.sort(apps, (a, b) -> a[0].toLowerCase(Locale.ROOT).compareTo(b[0].toLowerCase(Locale.ROOT)));
        return apps;
    }
}
