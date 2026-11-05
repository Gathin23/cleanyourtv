package io.github.anhost77.salon;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Réglages: everything the household can change without a computer, in one list the remote walks top to bottom.
 * The phone page (QR code) carries the two things a remote is bad at: the home photo and the TMDB token.
 */
public final class SettingsActivity extends Activity {
    private LinearLayout list;
    private float unit;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        unit = getResources().getDisplayMetrics().heightPixels / 1080f;
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF0B0F16);
        scroll.setFillViewport(true);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(px(160), px(60), px(160), px(80));
        scroll.addView(list);
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Rebuilt each time: the phone may have changed the photo or the key meanwhile.
        int focused = list.indexOfChild(list.findFocus());
        build();
        View back = focused >= 0 && focused < list.getChildCount() ? list.getChildAt(focused) : null;
        if (back != null && back.isFocusable()) {
            back.requestFocus();
            return;
        }
        // First visit: the remote starts on the first line, framed, instead of nowhere.
        for (int i = 0; i < list.getChildCount(); i++) {
            if (list.getChildAt(i).isFocusable()) {
                list.getChildAt(i).requestFocus();
                break;
            }
        }
    }

    private void build() {
        list.removeAllViews();
        list.addView(text(getString(R.string.settings_title), 44, Typeface.BOLD, Color.WHITE));

        header(R.string.settings_phone_section);
        boolean photo = HomePhoto.file(this).isFile();
        row(getString(R.string.settings_phone), getString(R.string.settings_phone_hint), "›",
                v -> startActivity(new Intent(this, PhoneActivity.class)));
        if (photo) row(getString(R.string.settings_photo_remove), getString(R.string.settings_photo_remove_hint), "",
                v -> {
                    HomePhoto.clear(this);
                    build();
                });
        row(getString(R.string.settings_tmdb), getString(Tmdb.enabled() ? R.string.settings_tmdb_on : R.string.settings_tmdb_off),
                "", null);

        header(R.string.settings_city_section);
        row(getString(R.string.settings_city), getSharedPreferences(WeatherActivity.PREFS, MODE_PRIVATE)
                .getString("name", getString(R.string.settings_city_none)), "›",
                v -> startActivity(new Intent(this, WeatherActivity.class)));

        header(R.string.settings_platforms);
        boolean[] jw = {Settings.justWatch()};
        TextView[] jwState = new TextView[1];
        jwState[0] = row(getString(R.string.settings_justwatch), getString(R.string.settings_justwatch_hint),
                getString(jw[0] ? R.string.settings_on : R.string.settings_off), v -> {
                    jw[0] = !jw[0];
                    Settings.setJustWatch(jw[0]);
                    jwState[0].setText(jw[0] ? R.string.settings_on : R.string.settings_off);
                    jwState[0].setTextColor(jw[0] ? 0xFF7CE38B : 0x80FFFFFF);
                });
        jwState[0].setTextColor(jw[0] ? 0xFF7CE38B : 0x80FFFFFF);
        for (String[] p : Tmdb.PLATFORMS) toggle(Settings.PLATFORMS, p[1], p[0], null);

        header(R.string.settings_channels);
        for (String[] c : TvTonight.CHANNELS) toggle(Settings.CHANNELS, c[0], c[1], null);

        header(R.string.settings_radios);
        for (String[] r : Radios.STATIONS) toggle(Settings.RADIOS, r[0], r[0], r[3]);

        TextView credit = text(getString(R.string.settings_credit), 20, Typeface.NORMAL, 0x80FFFFFF);
        credit.setSingleLine(false);
        credit.setPadding(0, px(40), 0, 0);
        list.addView(credit);
    }

    private void header(int label) {
        TextView h = text(getString(label).toUpperCase(java.util.Locale.FRANCE), 22, Typeface.BOLD, 0x99FFFFFF);
        h.setLetterSpacing(0.15f);
        h.setPadding(px(12), px(36), 0, px(10));
        list.addView(h);
    }

    private void toggle(String listName, String id, String name, String hint) {
        boolean[] on = {!Settings.off(listName, id)};
        TextView[] state = new TextView[1];
        state[0] = row(name, hint, on[0] ? getString(R.string.settings_on) : getString(R.string.settings_off), v -> {
            on[0] = !on[0];
            Settings.setOff(listName, id, !on[0]);
            state[0].setText(on[0] ? R.string.settings_on : R.string.settings_off);
            state[0].setTextColor(on[0] ? 0xFF7CE38B : 0x80FFFFFF);
            // The launcher's cards follow at once.
            getContentResolver().notifyChange(TvTonight.URI, null);
            getContentResolver().notifyChange(Radios.URI, null);
            getContentResolver().notifyChange(Picks.URI, null);
        });
        state[0].setTextColor(on[0] ? 0xFF7CE38B : 0x80FFFFFF);
    }

    /** A line of the list: name, a hint under it, the state on the right. Returns the state's view. */
    private TextView row(String name, String hint, String value, View.OnClickListener click) {
        LinearLayout line = new LinearLayout(this);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setPadding(px(24), px(14), px(24), px(14));
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.addView(text(name, 28, Typeface.NORMAL, Color.WHITE));
        if (hint != null && !hint.isEmpty()) words.addView(text(hint, 20, Typeface.NORMAL, 0x99FFFFFF));
        line.addView(words, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView state = text(value, 26, Typeface.BOLD, 0xCCFFFFFF);
        line.addView(state);
        if (click != null) {
            line.setFocusable(true);
            line.setClickable(true);
            line.setOnClickListener(click);
            line.setOnFocusChangeListener((v, has) -> v.setBackground(has ? pill() : null));
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = px(4);
        list.addView(line, lp);
        return state;
    }

    private GradientDrawable pill() {
        GradientDrawable d = new GradientDrawable();
        d.setColor(0x26FFFFFF);
        d.setStroke(Math.max(2, px(3)), Color.WHITE);
        d.setCornerRadius(px(16));
        return d;
    }

    private TextView text(String s, float size, int style, int colour) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * unit);
        t.setTypeface(Typeface.create(Typeface.SANS_SERIF, style));
        t.setTextColor(colour);
        t.setSingleLine(true);
        return t;
    }

    private int px(float at1080) {
        return Math.round(at1080 * unit);
    }
}
