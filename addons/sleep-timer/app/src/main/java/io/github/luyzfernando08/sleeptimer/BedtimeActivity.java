package io.github.luyzfernando08.sleeptimer;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;

/**
 * Bedtime in one press, for a remote button ("178:io.github.luyzfernando08.sleeptimer/.BedtimeActivity" in the
 * launcher's app_keys): starts a 30-minute timer and, when the Brightness add-on is installed, switches its night
 * mode on; pressed again while the timer runs, it cancels the timer and switches night mode off. The quick bar
 * then shows the timer, and Left/Right still change it. No window of its own.
 */
public class BedtimeActivity extends Activity {
    static final int DEFAULT_MINUTES = 30;
    private static final ComponentName NIGHT_MODE = new ComponentName("io.github.maxhayim.brightness",
            "io.github.maxhayim.brightness.NightToggleActivity");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        boolean bedtime = Timer.end(this) == 0;
        if (bedtime) Timer.start(this, DEFAULT_MINUTES);
        else Timer.cancel(this);
        try {
            startActivity(new Intent().setComponent(NIGHT_MODE)
                    .putExtra("night", bedtime ? "on" : "off")
                    .putExtra("bar", false)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException | SecurityException noBrightness) {
            // Then bedtime is the timer alone.
        }
        startActivity(new Intent(this, QuickActivity.class));
        finish();
    }
}
