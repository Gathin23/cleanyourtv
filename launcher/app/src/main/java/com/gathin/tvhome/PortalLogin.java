package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.provider.Settings;

/**
 * wifi_status_warnings: while the Wi-Fi pill is orange (connected, not validated: a hotel or holiday-park Wi-Fi
 * waiting for its login page), OK on it opens that page in a browser instead of Wi-Fi settings. Typing the
 * portal's address on a remote is the hard part, and Android's own sign-in notification is easy to miss on a TV.
 *
 * The page asked for is the plain-HTTP address Android itself probes to detect the portal (captive_portal_http_url,
 * Google's generate_204 by default): the portal intercepts exactly that request, so the browser lands on its login
 * page, and nothing is fetched that the TV was not already fetching. The launcher makes no request itself.
 */
final class PortalLogin {
    /** Android's default probe, used when the setting is unset or not plain HTTP (a portal cannot rewrite HTTPS). */
    static final String DEFAULT_PROBE = "http://connectivitycheck.gstatic.com/generate_204";
    /** Android TV's stand-in for apps the TV does not ship: it answers a web link with "no app", not a browser. */
    static final String STUB_PACKAGE = "com.google.android.tv.frameworkpackagestubs";

    private PortalLogin() {
    }

    /** The address to open: the configured probe when it is plain HTTP, otherwise Android's default. */
    static String url(String configured) {
        String url = configured == null ? "" : configured.trim();
        return url.regionMatches(true, 0, "http://", 0, 7) && url.length() > 7 ? url : DEFAULT_PROBE;
    }

    /** Whether the app Android would open a web link with is a browser: not nothing, and not the stub. */
    static boolean isBrowser(String pkg) {
        return pkg != null && !pkg.equals(STUB_PACKAGE);
    }

    /** The login page in a browser, or null where the TV has none, so the pill keeps opening Wi-Fi settings. */
    static Intent intent(Context ctx) {
        String url = url(Settings.Global.getString(ctx.getContentResolver(), "captive_portal_http_url"));
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE);
        ResolveInfo r = ctx.getPackageManager().resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY);
        return isBrowser(r != null ? r.activityInfo.packageName : null) ? i : null;
    }
}
