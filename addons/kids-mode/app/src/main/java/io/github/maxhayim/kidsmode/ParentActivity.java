package io.github.maxhayim.kidsmode;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;
import java.util.Set;

/**
 * The tile and module card open this: the parents' screen. The first time it asks for a new PIN; after that it
 * asks for the PIN before showing anything. Start Kids Mode is the first row; below are the kids' apps (OK adds
 * or removes one), the lock, the normal home app, and whether the guard and Home are set up.
 */
public class ParentActivity extends Activity {
    private LinearLayout rows;
    private TextView status;
    /** The PIN was given since this screen came to the front. */
    private boolean unlocked;

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
        ScrollView scroll = new ScrollView(this);
        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(rows);
        page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(page);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!Store.appsChosen(this)) Store.setAllowed(this, Apps.defaults(this));
        if (!Store.hasLock(this)) {
            rows.removeAllViews();
            status.setText("Choose a PIN of 4 to 8 digits. It's needed to leave Kids Mode and to change it.");
            newLock(false);
            return;
        }
        if (unlocked) {
            build();
            return;
        }
        rows.removeAllViews();
        status.setText("");
        long wait = Store.waitLeft(this);
        if (wait > 0) {
            status.setText("Too many tries. Try again in " + ((wait + 999) / 1000) + " seconds.");
            return;
        }
        Prompt.ask(this, Store.usesPassword(this) ? "Password" : "PIN", Store.usesPassword(this), v -> {
            if (Store.check(this, v)) {
                unlocked = true;
                build();
            } else {
                long w = Store.waitLeft(this);
                status.setText(w > 0 ? "Not right. Try again in " + ((w + 999) / 1000) + " seconds." : "Not right.");
                add(new Row(this, "Try again", null, this::onResume));
            }
        });
    }

    @Override
    protected void onStop() {
        // Leaving the screen locks it again.
        unlocked = false;
        super.onStop();
    }

    /** Asks for a new PIN (or password) twice; the second must match. */
    private void newLock(boolean password) {
        String what = password ? "password" : "PIN";
        Prompt.ask(this, "New " + what, password, first -> {
            if (password ? !Lock.validPassword(first) : !Lock.validPin(first)) {
                status.setText(password ? "A password needs at least 6 characters." : "A PIN is 4 to 8 digits.");
                add(new Row(this, "Try again", null, () -> newLock(password)));
                return;
            }
            Prompt.ask(this, "The " + what + " again", password, second -> {
                if (!first.equals(second)) {
                    status.setText("The two didn't match.");
                    add(new Row(this, "Try again", null, () -> newLock(password)));
                    return;
                }
                Store.setLock(this, first, password);
                unlocked = true;
                build();
            });
        });
    }

    /** Rebuilds the list; focus stays on the row it was on. */
    private void build() {
        int focused = rows.indexOfChild(rows.getFocusedChild());
        rows.removeAllViews();
        boolean home = Apps.isHome(this), guard = Apps.guardOn(this);
        Set<String> allowed = Store.allowed(this);
        if (!home || !guard) {
            status.setText("Not set up yet: " + (!home ? "Kids Mode isn't the TV's home app" : "the guard is off")
                    + ". install.sh sets both; see the README.");
        } else {
            status.setText(Store.on(this) ? "Kids Mode is on."
                    : allowed.size() + (allowed.size() == 1 ? " app" : " apps") + " on the kids' screen.");
        }
        Row start = add(new Row(this, "Start Kids Mode", "Home shows only the kids' apps until the PIN is given.", () -> {
            Store.setOn(this, true);
            startActivity(new Intent(this, KidsHomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            finish();
        }));
        start.setValue(home && guard ? "" : "Not set up");

        heading("Kids' apps");
        List<Apps.App> apps = Apps.tvApps(this);
        for (Apps.App a : apps) {
            Row r = add(new Row(this, a.label, null, () -> {
                Set<String> s = Store.allowed(this);
                if (!s.remove(a.pkg)) s.add(a.pkg);
                Store.setAllowed(this, s);
                build();
            }));
            r.setValue(allowed.contains(a.pkg) ? "✓ On the kids' screen" : "");
        }

        heading("Lock");
        boolean pw = Store.usesPassword(this);
        add(new Row(this, pw ? "Change the password" : "Change the PIN", null, () -> newLock(pw)));
        add(new Row(this, pw ? "Use a PIN instead" : "Use a password instead", null, () -> newLock(!pw)));

        heading("Setup");
        List<Apps.App> homes = Apps.otherHomes(this);
        Row normal = add(new Row(this, "Normal home app", "What Home shows while Kids Mode is off. OK picks the next one.", () -> {
            if (homes.isEmpty()) return;
            int i = 0;
            for (int k = 0; k < homes.size(); k++) if (homes.get(k).component.equals(Store.normalHome(this))) i = k + 1;
            Store.setNormalHome(this, homes.get(i % homes.size()).component);
            build();
        }));
        String normalName = "Not found";
        for (Apps.App h : homes) if (h.component.equals(Store.normalHome(this))) normalName = h.label;
        if (Store.normalHome(this) == null && !homes.isEmpty()) normalName = homes.get(0).label;
        normal.setValue(normalName);
        Row guardRow = add(new Row(this, "Guard", "Sends other apps back to the kids' screen. OK opens Accessibility settings.",
                () -> open(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))));
        guardRow.setValue(guard ? "On" : "Off");
        Row homeRow = add(new Row(this, "Kids Mode is the home app", "OK opens the TV's home app setting, where it has one.",
                () -> open(new Intent(Settings.ACTION_HOME_SETTINGS))));
        homeRow.setValue(home ? "Yes" : "No");

        View keep = focused >= 0 && focused < rows.getChildCount() ? rows.getChildAt(focused) : null;
        (keep != null && keep.isFocusable() ? keep : start).requestFocus();
    }

    private void open(Intent i) {
        try {
            startActivity(i);
        } catch (ActivityNotFoundException | SecurityException e) {
            status.setText("This TV has no screen for that; install.sh sets it from a computer.");
        }
    }

    private void heading(String text) {
        TextView h = new TextView(this);
        h.setText(text);
        h.setTextSize(13);
        h.setTextColor(0xFFFFC83D);
        h.setPadding(px(16), px(18), 0, px(4));
        rows.addView(h);
    }

    private Row add(Row r) {
        rows.addView(r, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return r;
    }

    private int px(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
