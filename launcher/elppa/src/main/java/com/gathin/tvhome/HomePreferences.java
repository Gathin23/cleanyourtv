package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class HomePreferences {
  private final SharedPreferences store;

  HomePreferences(Context c) {
    store = c.getSharedPreferences("elppa_home", Context.MODE_PRIVATE);
  }

  boolean demo() {
    return store.getBoolean("demo", false);
  }

  boolean motion() {
    return store.getBoolean("motion", true);
  }

  boolean rotate() {
    return store.getBoolean("rotate", true);
  }

  void toggle(String key, boolean value) {
    store.edit().putBoolean(key, value).apply();
  }

  Set<String> excludedSources() {
    return new HashSet<>(store.getStringSet("excluded_sources", new HashSet<>()));
  }

  Set<String> hiddenApps() {
    return new HashSet<>(store.getStringSet("hidden_apps", new HashSet<>()));
  }

  Set<String> dismissed() {
    return new HashSet<>(store.getStringSet("dismissed", new HashSet<>()));
  }

  void setExcluded(Set<String> sources) {
    store.edit().putStringSet("excluded_sources", new HashSet<>(sources)).apply();
  }

  void showApp(String pkg, boolean show) {
    Set<String> hidden = hiddenApps();
    if (show) hidden.remove(pkg);
    else hidden.add(pkg);
    store.edit().putStringSet("hidden_apps", hidden).apply();
  }

  void dismiss(String key) {
    Set<String> set = dismissed();
    if (set.size() >= 200) set.clear();
    set.add(key);
    store.edit().putStringSet("dismissed", set).apply();
  }

  void clearDismissed() {
    store.edit().remove("dismissed").apply();
  }

  List<String> order() {
    String value = store.getString("app_order", "");
    return value.isEmpty() ? new ArrayList<>() : new ArrayList<>(Arrays.asList(value.split("\n")));
  }

  void order(List<String> value) {
    store.edit().putString("app_order", String.join("\n", value)).apply();
  }
}
