package io.github.maxhayim.startupbranding;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

/**
 * Undo: turn the welcome screen off, put every setting back to its default, or delete the pictures. The last
 * two take OK twice, so one stray press doesn't undo an evening's work.
 */
public class ResetActivity extends Activity {
    private final Handler main = new Handler(Looper.getMainLooper());
    private Page page;
    /** The row waiting for its second OK, or -1. */
    private int armed = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        page = new Page(this, "Restore Defaults", false);
        page.subtitle.setText("Nothing here touches the TV's own settings: only this module's.");
        build(0);
    }

    @Override
    protected void onPause() {
        main.removeCallbacksAndMessages(null);
        armed = -1;
        super.onPause();
    }

    private void build(int focus) {
        page.rows.removeAllViews();
        Look l = Prefs.load(this);
        int pictures = Images.list(this).size();
        page.add("Turn Off the Welcome Screen", "Keeps your picture and text for later.", () -> {
            Look x = Prefs.load(this);
            x.mode = Look.Mode.OFF;
            Prefs.save(this, x);
            done("The welcome screen is off.", 0);
        }).setValue(l.mode == Look.Mode.OFF ? "Off" : "");
        page.add("Restore Defaults", "Every setting back as it was installed: text, colour, time, scaling, nickname.",
                () -> twice(1, () -> {
                    Prefs.clear(this);
                    done("Settings are back to their defaults.", 1);
                })).setValue(armed == 1 ? "OK again" : "");
        page.add("Delete Pictures", pictures == 0 ? "No pictures on the TV." : "Removes the " + pictures
                        + (pictures == 1 ? " picture" : " pictures") + " in this module's folder.",
                pictures == 0 ? null : () -> twice(2, () -> {
                    boolean ok = Images.deleteAll(this);
                    Look x = Prefs.load(this);
                    x.image = "";
                    Prefs.save(this, x);
                    done(ok ? "Pictures deleted." : "Some pictures couldn't be deleted.", 2);
                })).setValue(armed == 2 ? "OK again" : "");
        page.focus(focus);
    }

    private void twice(int row, Runnable action) {
        if (armed != row) {
            armed = row;
            build(row);
            main.removeCallbacksAndMessages(null);
            main.postDelayed(() -> {
                armed = -1;
                build(page.focused());
            }, 5000);
            return;
        }
        armed = -1;
        main.removeCallbacksAndMessages(null);
        action.run();
    }

    private void done(String message, int focus) {
        page.subtitle.setText(message);
        build(focus);
    }
}
