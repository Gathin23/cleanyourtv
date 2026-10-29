# Xiaomi MiTV-MTEQ0 (baymax): China firmware notes

Tested on a Xiaomi TV EA Pro 55" (MiTV-MTEQ0, `baymax`, sold as L55MA-EP), MediaTek/MStar,
3840x2160 panel, Android 10 / API 29, China-market firmware `23.6.26.4028`
(`QQ2A.200305.004.A1`). No Google services. Debloat list:
[`examples/xiaomi-mitv-mteq0/debloat.txt`](../../examples/xiaomi-mitv-mteq0/debloat.txt).
One day of use, two reboots, several standby cycles.

## Home

The same package-name HOME allowlist as the MiTV-AETP0
([`xiaomi-mitv-aetp0-dofus.md`](xiaomi-mitv-aetp0-dofus.md)). With the standard
`com.gathin.tvhome` ID, `set-home-activity` prints `Success` but HOME keeps resolving to
`com.mitv.tvhome`, and disabling it leaves `com.android.tv.settings/.system.FallbackHome`
(black screen).

The opt-in identity worked here as documented for the AETP0:

```bash
tools/venv/bin/python -I tools/tv.py 'pm path com.ts.launcher'        # must print nothing
(cd launcher && ./gradlew assembleRelease -PlauncherApplicationId=com.ts.launcher)
tools/venv/bin/python -I tools/b64push.py launcher/app/build/outputs/apk/release/app-release.apk /data/local/tmp/ts.apk
tools/venv/bin/python -I tools/tv.py 'pm install -r /data/local/tmp/ts.apk'
tools/venv/bin/python -I tools/tv.py 'cmd package set-home-activity --user 0 com.ts.launcher/com.gathin.tvhome.MainActivity'
tools/venv/bin/python -I tools/tv.py 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME'
```

`resolve-activity` answered `com.ts.launcher/com.gathin.tvhome.MainActivity`, also with
`com.mitv.tvhome` disabled, after a reboot, and after a force-stop. `com.mitv.cloudcontrol` was
already disabled; the allowlist it had downloaded stays on disk. The launcher's optional overlay
lives in `/sdcard/Android/data/com.ts.launcher/files/config.json`.

An accessibility redirect from FallbackHome is not a durable alternative on this set: see Standby.

A second MiTV-MTEQ0, on HyperOS `22.6.27.2363`, read the allowlist itself out of
`MitvConfigParser`'s logcat and found which package really answers the settings button:
[`xiaomi-mitv-mteq0/xiaomi-mitv-mteq0.md`](xiaomi-mitv-mteq0/xiaomi-mitv-mteq0.md).

## Standby force-stops third-party apps

Pressing Power puts the set into `xiaoai_mode` (screen off). Two seconds later
`MitvPowerManagerService` calls `MitvActivityManagerService.killAllThirdApp`, which force-stops
every running non-system app unless the cloud rule (`MitvConfigParser`, flag `0x8000`) exempts it.
Android then removes a force-stopped app's accessibility service from
`enabled_accessibility_services` for good, so a Home or key helper built on accessibility is gone
after the first standby. Notification listeners are rebound after a force-stop, accessibility
services are not. A real HOME app is simply started again on wake.

The first presses of Power also show a Chinese guide for the screen-off mode. It stops after
`settings.xiaoai.ui.max.count` (5) presses; `settings put global settings.xiaoai.ui.count 5`
skips it.

## ADB closes at every boot

`AdbService` starts with ADB on, then `com.xiaomi.mitv.settings` (`SettingsUtil`) writes
`adb_enabled=0` about four seconds after boot. Re-enable under Settings > Account & Security >
ADB debugging. `persist.adb.tcp.port` stays 5555, so turning it back on reopens the network port.

## Inputs

The passthrough channel URI the launcher sends is accepted but does not switch the source.
`com.xiaomi.mitv.tvplayer/.ExternalSourceActivity` with action `com.xiaomi.mitv.tvplayer.EXTSRC_PLAY`
and an integer extra `input` does, with the same numbering as the MiTV-MFTR0
([`xiaomi-mitv-mftr0-freeguy.txt`](xiaomi-mitv-mftr0-freeguy.txt)):

The ready-to-push overlay is [`examples/xiaomi-mitv-mteq0/config.json`](../../examples/xiaomi-mitv-mteq0/config.json):
push it with `TV_HOST=<tv ip> tools/theme.sh examples/xiaomi-mitv-mteq0` after installing the launcher.
It keeps the shared launcher defaults unchanged and applies this route only when this device profile is pushed.

| Input | TvInputInfo ID | `input` |
|---|---|---|
| HDMI 1 | `com.mediatek.tvinput/.hdmi.HDMIInputService/HW5` | 23 |
| HDMI 2 | `com.mediatek.tvinput/.hdmi.HDMIInputService/HW6` | 24 |
| HDMI 3 | `com.mediatek.tvinput/.hdmi.HDMIInputService/HW7` | 25 |
| AV | `com.mediatek.tvinput/.composite.CompositeInputService/HW2` | 2 |

An HDMI-CEC device appears as an extra input (`.../HDMI200008`) whose parent is the port it is on.
HDMI 1 and 2 were checked by the TV input session (`dumpsys tv_input`); HDMI screenshots are empty.

## Wi-Fi 5 GHz

`ClientModeImpl.setDefaultCountryCode` in `wifi-service.jar` takes the Wi-Fi country code from
`Settings.Global service_region`, then `ro.mitv.region`, and falls back to `CN`. Both are empty on
this set, so the driver runs with `CN` and never sees 5 GHz networks on channels 100-144 (a router
on channel 104 was invisible; Xiaomi routers pick CN-legal channels, so they work). Fix:

```bash
tools/venv/bin/python -I tools/tv.py 'settings put global service_region VN'   # your country code
# then Wi-Fi off and on (or reboot); dumpsys wifi shows mDriverCountryCode
```

## Installing apps

- `com.android.packageinstaller` is Xiaomi's MiTVPackageInstaller, which has no activity for
  `android.content.pm.action.CONFIRM_INSTALL`. A store that commits a PackageInstaller session
  (Aptoide uses one for split APKs) waits at 80% forever. The adb shell can accept such a session
  with `IPackageInstaller.setPermissionsResult(sessionId, true)`. Stores that open the APK with
  `ACTION_VIEW` (MStore, Aptoide for single APKs) work normally.
- Third-party stores need "install unknown apps": `appops set <pkg> REQUEST_INSTALL_PACKAGES allow`.
- No Google services: the official YouTube TV app shows "device does not meet requirements" and an
  old Google voice search build gets HTTP 400 from the speech server. SmartTube and Stremio's
  Android TV build work.

## Language and keyboard

`framework-res`, TV Settings and Xiaomi Settings ship only English and Chinese. The shell holds
`CHANGE_CONFIGURATION`, so a small `app_process` tool calling
`IActivityManager.updatePersistentConfiguration` can set a locale list (for example `vi-VN,en-US`);
apps with that translation follow it and the system falls back to English, not Chinese. It
survived reboots (stored in `system_locales`).

The only IME is Sogou (`com.sohu.inputmethod.sogou.tv`). Install another keyboard first, select it
with `ime enable` / `ime set`, then disable Sogou. The selected IME survived the standby
force-stop.

## What the shell cannot do here

- `pm disable-user` on a component: `Shell cannot change component state for ... to 3`.
- `pm hide`: requires `MANAGE_USERS`.
- `pm uninstall -k --user 0` on a system app: `DELETE_FAILED_INTERNAL_ERROR`
  (`Attempt to delete unknown system package`).
- Persistent apps keep running when disabled (`com.xiaomi.mitv.upgrade`,
  `com.mediatek.TimeMeasurementAgent`); `am force-stop` does not stop them either.
- Every Xiaomi app runs as `android.uid.system`, so per-app network blocking is not possible;
  Private DNS (an ad-blocking resolver) is the only network-level filter.

## Remote buttons

Long-press Home opens Xiaomi Smart Home (`mLongPressOnHomeBehavior=3`), long-press Menu opens
the smart panel; both do nothing once those apps are disabled. Double-tap Home still opens the
recents panel from `com.xiaomi.mitv.systemui` (clear apps from there). The mic key sends no key
event; the Bluetooth service hands it to `com.xiaomi.voicecontrol` only.
