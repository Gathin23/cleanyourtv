package com.gathin.artframe;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.KeyEvent;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Every option as one row: Left and Right (or OK) change it, and it is saved at once. Also opened from the TV's
 * screensaver menu.
 */
public class SettingsActivity extends Activity {
    private Prefs prefs;
    private Library lib;
    private final List<Runnable> refreshers = new ArrayList<>();

    interface Value {
        String text();
    }

    interface Change {
        void step(SharedPreferences.Editor e, int step);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = new Prefs(this);
        lib = Library.get(this);
        int pad = Ui.dp(this, 40);
        LinearLayout list = Ui.column(this);
        list.setPadding(pad, Ui.dp(this, 28), pad, Ui.dp(this, 28));
        list.addView(Ui.heading(this, getString(R.string.settings)), Ui.gap(this, 0, 6));
        list.addView(Ui.text(this, getString(R.string.settings_hint), 15, Ui.MUTED), Ui.gap(this, 0, 18));

        section(list, R.string.section_play);
        defaultRow(list);
        row(list, R.string.set_seconds, () -> secondsLabel(prefs.seconds()),
                (e, s) -> e.putInt("seconds", Prefs.next(Prefs.SECONDS, prefs.seconds(), s)));
        row(list, R.string.set_order, () -> getString(prefs.shuffle() ? R.string.shuffle : R.string.in_order),
                (e, s) -> e.putBoolean("shuffle", !prefs.shuffle()));

        row(list, R.string.set_home_bg, () -> onOff(prefs.homeBackground()), (e, s) -> {
            e.putBoolean("home_background", !prefs.homeBackground()).remove("home_bg_key");
            e.commit();
            HomeBackground.follow(this, null);
        });
        row(list, R.string.set_wake, () -> wakeLabel(prefs.wake()), (e, s) -> {
            e.putString("wake", Prefs.next(Prefs.WAKE, prefs.wake(), s));
            e.commit();
            WakeService.sync(this);
        });

        section(list, R.string.section_look);
        row(list, R.string.set_matte, () -> onOff(prefs.matte()), (e, s) -> e.putBoolean("matte", !prefs.matte()));
        row(list, R.string.set_matte_colour, () -> Matte.label(this, prefs.matteColour()),
                (e, s) -> e.putString("matte_colour", Matte.next(Matte.COLOURS, prefs.matteColour(), s)));
        row(list, R.string.set_matte_size, () -> Matte.label(this, prefs.matteSize()),
                (e, s) -> e.putString("matte_size", Matte.next(Matte.SIZES, prefs.matteSize(), s)));
        row(list, R.string.set_matte_texture, () -> Matte.label(this, prefs.matteTexture()),
                (e, s) -> e.putString("matte_texture", Matte.next(Matte.TEXTURES, prefs.matteTexture(), s)));
        row(list, R.string.set_depth, () -> Matte.label(this, prefs.depth()),
                (e, s) -> e.putString("depth", Matte.next(Matte.DEPTHS, prefs.depth(), s)));
        row(list, R.string.set_border, () -> onOff(prefs.border()), (e, s) -> e.putBoolean("border", !prefs.border()));
        row(list, R.string.set_caption, () -> captionLabel(prefs.caption()),
                (e, s) -> e.putString("caption", Prefs.next(Prefs.CAPTIONS, prefs.caption(), s)));
        row(list, R.string.set_motion, () -> onOff(prefs.motion()), (e, s) -> e.putBoolean("motion", !prefs.motion()));
        row(list, R.string.set_crossfade, () -> onOff(prefs.crossfade()), (e, s) -> e.putBoolean("crossfade", !prefs.crossfade()));
        row(list, R.string.set_clock, () -> onOff(prefs.clock()), (e, s) -> e.putBoolean("clock", !prefs.clock()));
        row(list, R.string.set_uhd, () -> onOff(prefs.uhd()), (e, s) -> e.putBoolean("uhd", !prefs.uhd()));

        section(list, R.string.section_schedule);
        row(list, R.string.set_schedule, () -> onOff(prefs.schedule()), (e, s) -> e.putBoolean("schedule", !prefs.schedule()));
        row(list, R.string.set_art_from, () -> hour(prefs.artFrom()),
                (e, s) -> e.putInt("art_from", Math.floorMod(prefs.artFrom() + s, 24)));
        row(list, R.string.set_art_until, () -> hour(prefs.artUntil()),
                (e, s) -> e.putInt("art_until", Math.floorMod(prefs.artUntil() + s, 24)));
        row(list, R.string.set_outside, () -> getString("black".equals(prefs.outside()) ? R.string.outside_black : R.string.outside_sleep),
                (e, s) -> e.putString("outside", "black".equals(prefs.outside()) ? "sleep" : "black"));
        row(list, R.string.set_dim, () -> onOff(prefs.dim()), (e, s) -> e.putBoolean("dim", !prefs.dim()));
        row(list, R.string.set_dim_from, () -> hour(prefs.dimFrom()),
                (e, s) -> e.putInt("dim_from", Math.floorMod(prefs.dimFrom() + s, 24)));
        row(list, R.string.set_dim_until, () -> hour(prefs.dimUntil()),
                (e, s) -> e.putInt("dim_until", Math.floorMod(prefs.dimUntil() + s, 24)));
        row(list, R.string.set_dim_level, () -> getString(R.string.percent_darker, prefs.dimLevel()),
                (e, s) -> e.putInt("dim_level", Prefs.next(Prefs.DIM_LEVELS, prefs.dimLevel(), s)));

        list.addView(Ui.text(this, getString(R.string.settings_footer), 14, Ui.MUTED), Ui.gap(this, 0, 0));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(list);
        Ui.screen(this, scroll);
        if (list.getChildCount() > 3) list.getChildAt(3).requestFocus();
    }

    private void section(LinearLayout list, int title) {
        TextView t = Ui.text(this, getString(title), 15, Ui.ACCENT);
        LinearLayout.LayoutParams lp = Ui.gap(this, 0, 8);
        lp.topMargin = Ui.dp(this, 14);
        list.addView(t, lp);
    }

    /** The art button's choice: everything, each collection, or the chosen piece. */
    private void defaultRow(LinearLayout list) {
        row(list, R.string.set_button, () -> lib.describeDefault(this), (e, s) -> {
            List<String[]> options = new ArrayList<>();
            options.add(new String[]{Library.DEFAULT_ALL, ""});
            for (String c : lib.collections()) options.add(new String[]{Library.DEFAULT_COLLECTION, c});
            if (Library.DEFAULT_PIECE.equals(lib.defaultKind()) && lib.find(lib.defaultId()) != null) {
                options.add(new String[]{Library.DEFAULT_PIECE, lib.defaultId()});
            }
            int i = 0;
            for (int k = 0; k < options.size(); k++) {
                if (options.get(k)[0].equals(lib.defaultKind()) && options.get(k)[1].equals(lib.defaultId())) i = k;
            }
            String[] next = options.get(Math.floorMod(i + s, options.size()));
            lib.setDefault(next[0], next[1]);
            HomeBackground.follow(this, null);
        });
    }

    private void row(LinearLayout list, int label, Value value, Change change) {
        TextView row = Ui.button(this, "", null);
        Runnable refresh = () -> row.setText(getString(label) + ":  " + value.text());
        Runnable[] holder = new Runnable[1];
        holder[0] = refresh;
        java.util.function.IntConsumer apply = step -> {
            SharedPreferences.Editor e = prefs.edit();
            change.step(e, step);
            e.apply();
            for (Runnable r : refreshers) r.run();
        };
        row.setOnClickListener(v -> apply.accept(1));
        row.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                apply.accept(-1);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                apply.accept(1);
                return true;
            }
            return false;
        });
        refreshers.add(holder[0]);
        refresh.run();
        row.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(this, 760), LinearLayout.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, 760), LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.dp(this, 8);
        list.addView(row, lp);
    }

    private String onOff(boolean on) {
        return getString(on ? R.string.on : R.string.off);
    }

    private String hour(int h) {
        return String.format(Locale.getDefault(), "%02d:00", h);
    }

    private String secondsLabel(int s) {
        if (s == 0) return getString(R.string.never);
        if (s < 3600) return getString(R.string.minutes, s / 60);
        return getString(R.string.hours, s / 3600);
    }

    private String wakeLabel(String w) {
        switch (w) {
            case "button": return getString(R.string.wake_button);
            case "any": return getString(R.string.wake_any);
            default: return getString(R.string.off);
        }
    }

    private String captionLabel(String c) {
        switch (c) {
            case "off": return getString(R.string.off);
            case "always": return getString(R.string.caption_always);
            default: return getString(R.string.caption_change);
        }
    }

}
