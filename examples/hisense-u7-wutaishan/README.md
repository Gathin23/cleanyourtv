# Hisense U7 (55U7SG, wutaishan)

Google TV on Android 14, US. The list is in `debloat.txt`. This page covers connecting, the remote and the launcher settings.

## Connecting

USB debugging doesn't open port 5555 on this set. Use Wireless debugging: pair once with the code from
Pair device with pairing code, then connect to the port on the main Wireless debugging screen (`tools/native-adb.md`).
It turns itself off on every reboot or Wi-Fi drop and comes back on a new port.

## Remote (SmartRC, Bluetooth)

- Input sends `KEY_F14`. Hisense's own switcher (`com.vt.tv.input`) handles it, no helper needed. `config.json` sets
  `handle_view_inputs` to `false` so the TV doesn't ask which app to open.
- Gear sends scancode 598 (`0x256`) and no keycode. To use it, put 598 in `settings_key_scancodes` in
  `launcher/app/src/main/res/values/config.xml` and install with `HOME_KEY_SERVICE=1`. A theme can't set scancodes.
- Volume stops repeating while a key helper filters keys, same as hengshan. I kept the ramp, so `config.json` sets
  `key_filter` to `false`. The gear does nothing. The Settings button on Home opens the same panel.
- Mic is hold-to-talk, same as stock.

## Launcher settings

`config.json` hides the tiles the top bar already covers (Live TV, TV Settings) and two Hisense apps (MyMusic and the
USB media center). It also points the Inputs menu at the Live TV app. Push it with
`tools/theme.sh examples/hisense-u7-wutaishan`.

Google's screensaver (`dreamx`) keeps working with launcherx disabled.

## Brazil 75U75Q: keep stock services with the clean launcher

Tested on one Brazilian 75U75Q, `wutaishan` / `his9655`, Android 14 (SDK 34), build
`UKN6.251123.001.049`, firmware `75u75quav0000.01.00A.q0818`, on 2 November 2026.
This alternative keeps both `com.google.android.apps.tv.launcherx` and
`com.google.android.tungsten.setupwraith` enabled. The original HOME role holder remains
setupwraith, while `MAIN` + `HOME` resolves to launcherx; neither needs to be changed.
Do not apply this folder's whole `debloat.txt` for this setup: it disables setupwraith, whose
provider native AirPlay needs on this board, as the list's existing notes explain.

Use the existing [theme/config keys](../../themes/README.md#keys):

```json
{
  "key_filter": false,
  "stock_home_redirect": "com.google.android.apps.tv.launcherx",
  "power_on_home": false,
  "power_on_home_always": false,
  "restore_key_services": false
}
```

Enable only `com.gathin.tvhome/.HomeKeyService` and check that it is actually
[bound](../../docs/TROUBLESHOOTING.md#accessibility-helper-enabled-but-the-remote-button-does-nothing).
It observes launcherx's Home windows; Google TV retains native remote handling and held-volume repeat.
The existing [redirect limitations](../../themes/README.md#keys), including an already-visible stock
Home and launcherx's profile chooser, still apply.

The ordinary process worked after a manual launch but failed to start its helper after reboot.
HiPerf logged `VtInterceptApp` blocking `com.gathin.tvhome(content provider)` with
`before boot_completed`, even with `sys.boot_completed=1`. A package entry in
`allowed_self_launch_3rd_packages` persisted but did not fix the next reboot. Static inspection suggests
foreground-app updates may populate the same cache and prevent the setting being read. That attempted setting
was restored to its original empty value.

The tested workaround is an opt-in build property, not a theme key. With the
[source-build prerequisites](../../docs/launcher.md#build-it-yourself), build in `launcher/`:

```sh
./gradlew --no-daemon -PlauncherProcess=:com.hisense.compat testReleaseUnitTest lintRelease assembleRelease
```

This creates the private process `com.gathin.tvhome:com.hisense.compat`, matching this firmware's
process-name exemption. Package, UID, signing configuration and permissions stay the same; use the
existing signing key for an in-place update. Omitting the property keeps the original default process.
Install with the existing [ADB tools](../../tools/native-adb.md), retain both stock packages, and start
`am start -W -n com.gathin.tvhome/.MainActivity` before enabling/verifying the helper. The generic
`build-and-install.sh` still calls `set-home-activity` with `STOCK_LAUNCHER=none` and can exit before
enabling the helper when launcherx retains Home; it does not preserve this unit's original HOME role.

The owner confirmed automatic clean Home with seven dock apps after one reboot of this build,
physical Home returning from an app, held-volume repeat, Input, gear, app shortcuts and the mic.
Actual AirPlay playback worked. AirPlay/HomeKit discovery, running receiver services and the existing
authenticated Android TV Remote status connection were also checked; Spotify listed the TV as a cast
target. New HomeKit pairing failed after an energy-settings prompt with both the clean launcher and
original Google TV (clean launcher disabled); its cause is unresolved and further diagnosis was deferred.
Chromecast video playback was not separately tested. Discovery alone does not establish
these features work. This is one unit and one successful reboot, not a cross-model or long-term claim.
