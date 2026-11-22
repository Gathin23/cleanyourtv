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

## With Google TV off: the remote's buttons and a held volume key

The same owner came back on 2026-11-21 with the list applied instead of the redirect: both
`com.google.android.apps.tv.launcherx` and `com.google.android.tungsten.setupwraith` turned off,
CleanYourTV owning Home directly, launcher 3.6 (versionCode 15) built from
[`f63a1ebc`](https://github.com/Gathin23/cleanyourtv/commit/f63a1ebc), firmware
`RTM5.220609.003.2674`, and both key services bound (`HOME_KEY_SERVICE=1 INPUT_KEY_SERVICE=1`).
The theme's `config.json`:

```json
{
  "key_filter": true,
  "volume_pauses_filter": true,
  "consume_volume_keys": false,
  "app_keys": ["190:com.google.android.youtube.tv"]
}
```

Measured with the physical remote:

- the first volume press changes one step;
- a second press, or a hold, within the next four seconds ramps the volume, and the ramp stops
  when the button is released;
- five seconds after the last change the YouTube, Settings, Input, Netflix and Prime Video
  buttons answer again;
- the same behaviour held after a reboot, with CleanYourTV still the direct Home.

This is a trade rather than a repair: while the pause runs, the buttons that need key filtering do
not answer. Without `volume_pauses_filter` the two settings exclude each other on this firmware:
`key_filter` true keeps the buttons and costs the ramp, so a held Volume key steps once per press,
which is what the MOOR4 and MOOR6 sessions in [`debloat.txt`](debloat.txt) recorded, while
`key_filter` false keeps the ramp and leaves those buttons dead because the stock home was what
answered them. The mechanism, and what the four seconds cover, are in
[A key helper is bound and a held remote button stopped repeating](../../docs/TROUBLESHOOTING.md#a-key-helper-is-bound-and-a-held-remote-button-stopped-repeating).

One key stays dead in this configuration: with Google TV off nothing answers the remote's Guide
(EPG) button, which two owners on the same issue confirmed. The launcher can be given that key in
`app_keys` ([`themes/README.md`](../../themes/README.md)); it was not tried on this unit.

HDMI was explicitly untested; casting, voice search and other peripheral paths
were not individually confirmed during this routing pass. Settings/Wi-Fi became
slow during the session, stayed slow with the helper off, and cleared after a
restart; final helper-on checks were smooth. The cause and recurrence are unknown.
This report makes no performance claim or several-day debloat recommendation.
