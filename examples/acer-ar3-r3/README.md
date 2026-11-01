# Acer R3 (Android TV 14): debloated, with the launcher on the Home button

SCBC device `R3`, Realtek `rtd2841a` (board `rt2841a`), Android TV 14 / SDK 34, build `UKR1.251116`,
incremental `1236`, India. Done over network ADB, with a screenshot before and after the debloat
batch, after the launcher handover, and one cold boot.

Plain Android TV, not Google TV: `com.google.android.tungsten.setupwraith` is not installed on this
unit, so there is no setup-wizard Home/Wi-Fi handover trap (unlike the Google TV R4G note in
[`docs/devices/acer-r4g.md`](../../docs/devices/acer-r4g.md)). Same SCBC/Realtek platform family as
[`acer-ar55ar2851udpro/`](../acer-ar55ar2851udpro/) and
[`docs/devices/acer-ar55ar2851udfl.md`](../../docs/devices/acer-ar55ar2851udfl.md) (Realtek `rtd2851m`
there, `rtd2841a` here).

## Connect

```bash
export TV_HOST=<tv ip>
```

## Debloat

```bash
TV_HOST=<tv ip> tools/disable.sh examples/acer-ar3-r3/debloat.txt
```

(Windows: `tools\disable.ps1 examples\acer-ar3-r3\debloat.txt`.) 26 packages disabled by default, none
uninstalled. The header of [`debloat.txt`](debloat.txt) says what each group is, what is kept on
purpose, and a firmware quirk with the remote's YouTube button (below). Netflix, Prime Video,
YouTube, YouTube Music and Play Games are commented out by default (personal choice: this set has
them off; uncomment any you use).

Applied and checked on one unit, in one batch with a screenshot before and after, then one cold boot
(power cable out): Home, the Inputs menu's HDMI1/HDMI2/AV listing (`cmd package query-activities`)
and the dock all still worked afterwards. All newly-disabled packages were still disabled
(`pm list packages -d`) after the reboot, confirming this Android TV variant does not re-enable
packages at boot the way the Google TV R4G note describes for a related platform.

## Launcher

`cmd package set-home-activity` worked directly here (no `stock_home_redirect` /
`HomeKeyService` fallback needed, unlike the `acer-ar55ar2851udpro` unit): the stock launcher
`com.google.android.tvlauncher` is disabled as part of the handover and Home resolves straight to
`com.gathin.tvhome/.MainActivity`, confirmed with `dumpsys activity activities | grep mFocusedApp`
and again after a cold boot.

```bash
HOME_KEY_SERVICE=1 launcher/build-and-install.sh
```

(Windows: `launcher\build-and-install.ps1 -TvHost <tv ip> -HomeKeyService`.) `HomeKeyService` is kept
on for the Home button's long-press fallback and the Settings/Input key-mapping hooks, even though no
redirect or key remapping is actually used on this unit.

[`config.json`](config.json) pushed with `tools/theme.sh examples/acer-ar3-r3`: the Live TV app for
the Inputs menu (HDMI1, HDMI2, AV, Live TV all listed and switch correctly), and `tile_menu` on so
holding OK on a tile offers Move / Replace with another app / App info instead of jumping straight to
Android's App info page. The dock itself (which apps, how many columns) is personal choice and not
part of this list; set `dock_apps` and `columns` to taste in your own theme or `config.xml` rebuild.

### Volume ramp with HomeKeyService bound

With `HomeKeyService` enabled at its default `key_filter` (true), holding Volume Up or Down on the
real remote steps once instead of ramping. This is the documented trade-off in
[`docs/TROUBLESHOOTING.md`](../../docs/TROUBLESHOOTING.md#a-key-helper-is-bound-and-a-held-remote-button-stopped-repeating):
once any accessibility service requests key filtering, this firmware's input pipeline drops the
auto-repeat before any app sees it. Since this unit uses no `settings_key_keycodes`/`scancodes`,
`input_key_keycodes` or `app_keys` that would need the filter, `config.json` sets `"key_filter":
false`: `HomeKeyService` stays bound (for the long-press fallback and any future key mapping) but
stops filtering, and the ramp is back. Confirmed on the real remote: a held Volume Up/Down ramps
normally, and `tile_menu`'s Move still works (hold OK, arrow keys carry the tile, Back drops it).

### Bluetooth Connect add-on (optional, personal choice)

The launcher has no built-in Bluetooth pill. On the tested unit the
[Bluetooth Connect](../../addons/bluetooth-connect/) add-on (`com.gathin.btconnect`) was installed
as a personal convenience, purely by the owner's own choice and not part of the debloat or launcher
steps above: it lists paired speakers with their connection state, and OK connects or disconnects.
Skip this section entirely if you have no need for it; nothing else in this list or `config.json`
depends on it. Verified on the tested unit: opening the tile shows the paired speaker with a working
Connect row.

```bash
TV_HOST=<tv ip> addons/bluetooth-connect/install.sh
```

(On Windows, without a POSIX shell: build with `addons/bluetooth-connect`'s own `gradlew`, then
`tools/b64push.py` the APK, `pm install -r` it over `tools/tv.py`, and grant
`BLUETOOTH_CONNECT` with `pm grant com.gathin.btconnect android.permission.BLUETOOTH_CONNECT`.)

### YouTube remote button: hardware-level, cannot be remapped

The remote's dedicated YouTube button sends keycode 2030, intercepted below Android's input layer by
the persistent system service `com.realtek.keyinterceptserver`, which starts
`com.google.android.youtube.tv` by a package name hardcoded in its own compiled code
(`RtkKeyInterceptService.java`). `RtkInput`'s own log line ("`ap decide result is 1`") shows the
vendor driver already claimed and handled the key before Android's normal dispatch, let alone an
accessibility service, ever sees it, so the launcher's `app_keys`/`InputKeyService` remapping cannot
reach it at all. If you uncomment the YouTube line in `debloat.txt` to disable the app, every press of
this button then throws `ActivityNotFoundException` inside that persistent process, which Android
restarts in place: harmless, but the button does nothing, and `logcat` shows a crash/restart on every
press. It cannot be remapped to a different app (such as SmartTube) on this firmware either way. Same
limitation as the sibling `examples/acer-ar55ar2851udpro` list.

## Remote

Standard Android TV remote keys: Home, Back, volume (ramps correctly with `key_filter` off, see
above), Settings and Input all work with the new launcher. The Inputs menu opened and listed
HDMI1/HDMI2/AV/Live TV correctly on the real remote; the Settings pill opens TV Settings. The hold-OK
tile menu (Move/Replace/App info) is confirmed working on the real remote.

## Undo

```bash
TV_HOST=<tv ip> tools/restore.sh examples/acer-ar3-r3/debloat.txt
TV_HOST=<tv ip> tools/theme.sh --reset
```

Uninstall the launcher (`pm uninstall com.gathin.tvhome`) to give the Home button back to the stock
launcher (`pm enable --user 0 com.google.android.tvlauncher` first, since it was disabled as part of
the handover). If you installed the optional Bluetooth Connect add-on, `pm uninstall
com.gathin.btconnect` removes it too; it is unrelated to the rest of the undo.
