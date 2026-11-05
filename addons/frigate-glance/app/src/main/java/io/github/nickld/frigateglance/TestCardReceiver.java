package io.github.nickld.frigateglance;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Shows a test card from ADB without bringing this app to the front, so a card can be checked over
 * whatever is playing: am broadcast -n io.github.nickld.frigateglance/.TestCardReceiver [--es camera NAME].
 * Only the shell can send it (WRITE_SECURE_SETTINGS, as the launcher's ThemeProvider).
 */
public class TestCardReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        GlanceService.showTestCard(context, intent.getStringExtra("camera"));
    }
}
