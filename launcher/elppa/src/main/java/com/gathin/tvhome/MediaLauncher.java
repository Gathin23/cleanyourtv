package com.gathin.tvhome;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

final class MediaLauncher {
  private MediaLauncher() {}

  static boolean open(Context context, MediaEntry e) {
    try {
      Intent parsed = Intent.parseUri(e.intentUri, Intent.URI_INTENT_SCHEME);
      // Discard supplied flags, selectors, components and extras; keep the link scoped to its
      // publisher.
      Uri uri = parsed.getData();
      if (uri != null && allowedScheme(uri.getScheme())) {
        Intent view =
            new Intent(Intent.ACTION_VIEW, uri)
                .setPackage(e.source)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
          context.startActivity(view);
          return true;
        } catch (ActivityNotFoundException | SecurityException ignored) {
        }
      }
    } catch (java.net.URISyntaxException | IllegalArgumentException ignored) {
    }
    return openApp(context, e.source);
  }

  static boolean allowedScheme(String s) {
    return s != null
        && !s.equalsIgnoreCase("file")
        && !s.equalsIgnoreCase("javascript")
        && !s.equalsIgnoreCase("data")
        && !s.equalsIgnoreCase("intent");
  }

  static boolean openApp(Context c, String pkg) {
    Intent i = c.getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
    if (i == null) i = c.getPackageManager().getLaunchIntentForPackage(pkg);
    if (i == null) return false;
    try {
      c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      return true;
    } catch (ActivityNotFoundException | SecurityException ignored) {
      return false;
    }
  }
}
