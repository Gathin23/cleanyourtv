package io.github.smartgenius2.neondream;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** The tile and the card: the options (scene, colours, clock), Preview, and a way to pick the screensaver. */
public class SettingsActivity extends Activity {

    private Prefs prefs;
    private Button scene, palette, clock;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = new Prefs(this);
        float dp = getResources().getDisplayMetrics().density;
        int pad = (int) (dp * 48);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setPadding(pad * 2, pad, pad * 2, pad);
        root.setBackgroundColor(0xFF0B0420);

        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextColor(0xFFFF2A6D);
        title.setTextSize(34);
        title.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        title.setPadding(0, 0, 0, pad / 2);
        root.addView(title);

        scene = row(root, v -> { prefs.cycleScene(); refresh(); });
        palette = row(root, v -> { prefs.cyclePalette(); refresh(); });
        clock = row(root, v -> { prefs.toggleClock(); refresh(); });
        Button preview = row(root, v -> startActivity(new Intent(this, PreviewActivity.class)));
        preview.setText(R.string.preview);
        Button choose = row(root, v -> chooseScreensaver());
        choose.setText(R.string.choose);

        TextView hint = new TextView(this);
        hint.setText(R.string.hint);
        hint.setTextColor(0xFF9A8FB8);
        hint.setTextSize(16);
        hint.setPadding(0, pad / 2, 0, 0);
        root.addView(hint);

        setContentView(root);
        scene.requestFocus();
        refresh();
    }

    private Button row(LinearLayout root, View.OnClickListener l) {
        float dp = getResources().getDisplayMetrics().density;
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setTextSize(22);
        b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        b.setTextColor(Color.WHITE);
        b.setBackgroundResource(R.drawable.row);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams((int) (dp * 520),
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = (int) (dp * 8);
        root.addView(b, lp);
        return b;
    }

    private void refresh() {
        scene.setText(getString(R.string.scene, getString("neon_grid".equals(prefs.scene())
                ? R.string.scene_grid : R.string.scene_city)));
        String p = prefs.palette();
        palette.setText(getString(R.string.palette, getString("amber".equals(p) ? R.string.amber
                : "green".equals(p) ? R.string.green : R.string.magenta)));
        clock.setText(getString(R.string.clock, getString(prefs.clock() ? R.string.on : R.string.off)));
    }

    /** Opens the TV's screensaver picker; the menu lives in a different place on different TVs. */
    private void chooseScreensaver() {
        String[] actions = {Settings.ACTION_DREAM_SETTINGS, Settings.ACTION_DISPLAY_SETTINGS, Settings.ACTION_SETTINGS};
        for (String a : actions) {
            try {
                startActivity(new Intent(a));
                return;
            } catch (ActivityNotFoundException | SecurityException ignored) {
                // try the next one
            }
        }
    }
}
