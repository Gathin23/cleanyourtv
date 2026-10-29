package com.gathin.tvhome;

import android.content.ContentResolver;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.CancellationSignal;
import android.util.Log;

import java.util.Timer;
import java.util.TimerTask;

/** Reads an add-on's page for the home widget (config.xml home_widgets) from its card provider. */
final class HomeWidgets {
    private static final String TAG = "TvHomeWidgets";
    static final long LIVE_TIMEOUT_MS = 1_500L;
    /** Add-on rows are read when Home comes to the front and then at most this often, whatever the page speed. */
    static final long ADD_ON_READ_MS = 60_000L;
    private static final Timer LIVE_TIMEOUT = new Timer("widget-row-timeout", true);

    private HomeWidgets() {
    }

    /**
     * The first row of a live card's provider (docs/specs/widget-source.md) as a widget page, or null. The provider
     * must be content://{pkg}.card and belong to {pkg}; anything else is not read.
     */
    static WidgetPages.Item live(ContentResolver resolver, PackageManager pm, String pkg, String label) {
        String authority = pkg + ".card";
        try {
            ProviderInfo info = pm.resolveContentProvider(authority, 0);
            if (info == null || !pkg.equals(info.packageName)) return null;
            // Ends the read early only for a provider that honours the signal; MainActivity bounds the wait itself.
            CancellationSignal cancel = new CancellationSignal();
            TimerTask timeout = new TimerTask() {
                @Override
                public void run() {
                    cancel.cancel();
                }
            };
            LIVE_TIMEOUT.schedule(timeout, LIVE_TIMEOUT_MS);
            try (Cursor c = resolver.query(Uri.parse("content://" + authority), null, null, null, null, cancel)) {
                if (c == null || !c.moveToFirst()) return null;
                return WidgetPages.fromRow(pkg, optString(c, "caption"), optString(c, "line"), optString(c, "kicker"),
                        optString(c, "meta"), optInt(c, "alert") == 1, optInt(c, "urgent") == 1,
                        optString(c, "kind"), label);
            } finally {
                timeout.cancel();
            }
        } catch (RuntimeException e) {
            Log.w(TAG, pkg + " widget row failed", e);
            return null;
        }
    }

    private static String optString(Cursor c, String column) {
        int i = c.getColumnIndex(column);
        return i < 0 || c.isNull(i) ? null : c.getString(i);
    }

    private static int optInt(Cursor c, String column) {
        int i = c.getColumnIndex(column);
        return i < 0 || c.isNull(i) ? 0 : c.getInt(i);
    }
}
