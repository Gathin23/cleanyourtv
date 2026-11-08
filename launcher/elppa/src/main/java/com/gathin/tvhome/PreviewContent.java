package com.gathin.tvhome;

import android.content.Context;
import java.util.ArrayList;
import java.util.List;

final class PreviewContent {
  private PreviewContent() {}

  static List<MediaEntry> entries(Context c) {
    String root = "android.resource://" + c.getPackageName() + "/";
    List<MediaEntry> entries = new ArrayList<>();
    String[] names = {
      "Beyond the horizon",
      "A world waking up",
      "Under the northern skies: a journey beyond the horizon"
    };
    int[] art = {R.drawable.elppa_orbit, R.drawable.elppa_sunrise, R.drawable.elppa_aurora};
    for (int i = 0; i < names.length; i++)
      entries.add(
          new MediaEntry(
              "demo:" + i,
              "demo",
              names[i],
              i == 2 ? "Sample title · A journey around our planet" : "Sample title",
              root + art[i],
              root + art[i],
              "",
              i == 0 ? 0 : 1,
              0,
              System.currentTimeMillis(),
              i == 0 ? 35 : 0,
              100,
              false));
    return entries;
  }
}
