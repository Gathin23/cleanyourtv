package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import java.text.Collator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class AppCatalog {
  static final class App {
    final String pkg, label;
    final ComponentName component;
    final ResolveInfo info;

    App(ResolveInfo info, PackageManager pm) {
      this.info = info;
      pkg = info.activityInfo.packageName;
      label = Cards.cleanLabel(String.valueOf(info.loadLabel(pm)), pkg);
      component = new ComponentName(pkg, info.activityInfo.name);
    }

    Drawable art(PackageManager pm) {
      try {
        Drawable banner = info.activityInfo.loadBanner(pm);
        if (banner == null) banner = info.activityInfo.applicationInfo.loadBanner(pm);
        return banner == null ? info.loadIcon(pm) : banner;
      } catch (RuntimeException ignored) {
        return null;
      }
    }
  }

  static List<App> load(Context c, HomePreferences prefs, boolean all) {
    PackageManager pm = c.getPackageManager();
    List<ResolveInfo> found =
        new ArrayList<>(
            pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0));
    List<String> order = prefs.order();
    for (ResolveInfo item :
        pm.queryIntentActivities(
            new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)) {
      if (all || (item.activityInfo != null && order.contains(item.activityInfo.packageName)))
        found.add(item);
    }
    List<App> result = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    Set<String> hidden = prefs.hiddenApps();
    for (ResolveInfo item : found) {
      if (item.activityInfo == null || !item.activityInfo.exported || !item.activityInfo.enabled)
        continue;
      String pkg = item.activityInfo.packageName;
      if (pkg.equals(c.getPackageName()) || (!all && hidden.contains(pkg)) || !seen.add(pkg))
        continue;
      result.add(new App(item, pm));
    }
    Collator collator = Collator.getInstance();
    result.sort(
        (a, b) -> {
          int ai = order.indexOf(a.pkg), bi = order.indexOf(b.pkg);
          if (ai >= 0 || bi >= 0)
            return Integer.compare(
                ai < 0 ? Integer.MAX_VALUE : ai, bi < 0 ? Integer.MAX_VALUE : bi);
          return collator.compare(a.label, b.label);
        });
    return result;
  }
}
