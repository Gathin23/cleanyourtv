# Xiaomi MiTV-MOOR2: Home redirect with Google TV kept enabled

Tested on one MiTV-MOOR2, Google TV / Android 11, firmware
`RTM5.220609.003.1300`, on 24 October 2026. Launcher v2.7 was built from
[`c965ef4b`](https://github.com/Gathin23/cleanyourtv/commit/c965ef4b11acc0ab34d6964f1dda745904111a46).
This is a one-session Home-routing report; the `.2674` package list in
[`debloat.txt`](debloat.txt) was not tested on this firmware.

## Configuration

Both `com.google.android.apps.tv.launcherx` and
`com.google.android.tungsten.setupwraith` stayed enabled. In
[`config.xml`](../../launcher/app/src/main/res/values/config.xml):

```xml
<string name="stock_home_redirect" translatable="false">com.google.android.apps.tv.launcherx</string>
<bool name="key_filter">false</bool>
```

Only `com.gathin.tvhome/.HomeKeyService` was enabled, through Settings → System →
Accessibility → Home button. `InputKeyService` stayed off, `app_keys` stayed empty,
and `restore_key_services` stayed false. No key mappings or automatic service-repair
permission were added.

On the tested revision, installing with `STOCK_LAUNCHER=none` installed the APK but
failed the registered-HOME check. After confirming the installed launcher opened,
the Home button service was enabled manually; no Home guard was overridden.
Android still resolves Google TV as HOME. Window-state events bring CleanYourTV
forward when stock Home appears, so check the visible screen as well as resolution.

## Checks and access to Google TV

- Home from Settings, YouTube and Netflix: 15/15 injected checks, five per app;
  the owner also confirmed the requested physical Home checks.
- Three ADB restarts: CleanYourTV was the focused window automatically, without a
  manual Home press or launcher start after boot.
- Two physical sleep/wake cycles, Input from a streaming app, app shortcuts,
  held-volume repeat and real streaming playback: passed by owner report.
- Accessibility → Home button **OFF**, then Home: original Google TV and its
  search/profiles/watchlist/recommendations worked. **ON**, then Home: clean grid.
  This is the local way to suspend redirection. Force-stopping the launcher can
  also disable its helper; re-enable it locally if needed.

HDMI was explicitly untested; casting, voice search and other peripheral paths
were not individually confirmed during this routing pass. Settings/Wi-Fi became
slow during the session, stayed slow with the helper off, and cleared after a
restart; final helper-on checks were smooth. The cause and recurrence are unknown.
This report makes no performance claim or several-day debloat recommendation.
