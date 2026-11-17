package io.github.pbanddev.tatami;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;

/**
 * Shows the floor plan full screen from its own tile, without changing the TV's screensaver.
 * OK or Menu opens the effects; Back closes.
 *
 * <p>{@code --es look yojohan|nagaya|both} shows that look here without keeping it, for timing one look:
 * {@code am start -n io.github.pbanddev.tatami/.PreviewActivity --es look nagaya}.
 */
public final class PreviewActivity extends Activity {
    private FloorPlanView plan;
    /** The look given as an extra, or null for the one in the settings. */
    private LookSchedule.Mode look;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        look = LookSchedule.Mode.of(getIntent().getStringExtra(SettingsActivity.EXTRA_LOOK));
        plan = new FloorPlanView(this);
        setContentView(plan);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Effects effects = Effects.load(this);   // picks up changes made in the effects list
        plan.setEffects(look == null ? effects : effects.withLook(look));
        plan.onResume();
    }

    @Override
    protected void onPause() {
        plan.onPause();
        super.onPause();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (opensEffects(keyCode)) {
            event.startTracking();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    /**
     * Only a press that began here: a launcher that opens apps on the press leaves its release to this screen, which
     * would otherwise open the effects at once.
     */
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (opensEffects(keyCode)) {
            if (event.isTracking() && !event.isCanceled()) startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    private static boolean opensEffects(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_MENU;
    }
}
