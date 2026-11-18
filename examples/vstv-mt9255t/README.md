# VSTV MT9255T / MT5862 (MediaTek)

Android 9 (SDK 28), build `TVOS-04.16.031.01.12` (2022-09-05), ~1 GB RAM. The board is a MediaTek
mt5862 CVTE design sold as "VSTV"; its stock UI is the `com.vstv.*` vendor stack, and Home is the
sideloaded Talkies launcher (`com.techpp.glass`).

`debloat.txt` is the list. The debloat list disables 18 packages: vendor telemetry and factory
tooling, AOSP components a TV never uses and the voice/assistant path. The OTA path, the Play
Store/Services stack and the phone remote were off on the tested unit too (sideload-only, no OTA);
the shared list keeps them as comments. Everything is disabled with `pm disable-user --user 0`, never uninstalled, so
any line can be commented out and the package comes back with the undo.

## Apply and undo

```bash
TV_HOST=<tv ip> tools/disable.sh examples/vstv-mt9255t/debloat.txt
TV_HOST=<tv ip> tools/restore.sh examples/vstv-mt9255t/debloat.txt com.vstv.mercurylauncher
```

The undo passes `com.vstv.mercurylauncher` on purpose: it is the stock launcher this set ships, and
`tools/restore.sh` needs it to point Home back at a package that resolves.

## What is disabled

- **Vendor diagnostics and factory tooling** - `com.vstv.diagnosis`, `com.vstv.factory`,
  `com.vstv.factory.test`. No everyday feature is lost.
- **The OTA path (commented out)** - `com.vstv.upgradecenter`, `com.vstv.ota`. Off on the tested unit;
  the shared list keeps both on so firmware updates still arrive.
- **AOSP dead weight** - the print spooler, wallpaper backup, the basic daydream, the sound recorder,
  the HTML viewer, backup confirm, shared-storage backup, the proxy handler and the two CTS shims.
- **The voice/assistant path** - `com.vstv.tv.assistant`, `com.google.android.katniss`,
  `com.vs.phone.remote.search.assistants` (`com.google.android.tv.remote.service`, the phone remote's
  pairing, was off on the tested unit and is a comment here). The remote's
  mic button does nothing while these are off; nothing else on the set depends on them.
- **The Play stack (commented out)** - `com.android.vending`, `com.google.android.gms`, `com.google.android.gsf` and
  `com.vs.phoneremote.google`. Nothing installed here comes from Play, and every app in use starts
  without Play Services. `com.vs.phoneremote.google` is `android:persistent` and runs as uid 1000, so
  its idle process keeps running even while the package is disabled. They are comments in the shared
  list: uncomment them only on a sideload-only set like this one.

## Never disable

`com.vstv.fbhome` is `VsFallbackHome`, the only home in this firmware declared
`android:directBootAware="true"`. While user 0 is still locked the framework can only launch a
direct-boot-aware Home, so disabling it (together with the stock launcher) makes `systemReady()`
report "No home screen found": user 0 never leaves `BOOTING` and the TV sits on the Android wordmark
until it is re-enabled over USB. Also keep `com.vstv.mercurylauncher`,
`com.vstv.input` (the DTV/HDMI inputs), `com.vstv.septembersettings` (the Settings app),
`com.vstv.global.service` (the OSD/volume/toast surface) and every `com.mediatek.*` package.

## Firmware quirks

- **Wi-Fi is off after every boot.** `wlan0` is a USB module that only probes on demand, and
  `WifiService` makes a single attempt about 8 s into boot, before the module is there, so the radio
  stays off until Settings is opened and the toggle flipped.
- **There is no Accessibility screen.** No activity on this build handles
  `android.settings.ACCESSIBILITY_SETTINGS`, so accessibility is configured over adb:

  ```bash
  adb shell settings put secure enabled_accessibility_services com.techpp.glass/.SleepService
  adb shell settings put secure accessibility_enabled 1
  ```

  **Never `am force-stop com.techpp.glass`.** That clears both settings above, and Talkies' Home
  key, "Turn off" and remote power handling live in that accessibility service. If it happens, run
  the two commands again.
- **The remote feels slow because of Talkies' focus animation**, not input lag. One DPAD move
  redraws the focus ring for about half a second, and presses that arrive during the animation are
  swallowed. Talkies' Control Center -> Talkies settings -> Effects has a `Lite` value that roughly
  halves it (about 265 ms); change it there, because the launcher rewrites its own `home.xml` and a
  hand-edited value does not stick.
- **Sideloaded apps get no AOT compilation** (`status=run-from-vdex-fallback`), so cold starts are
  JIT-bound. `cmd package compile -m speed -f <package>` fixes one app at a time, and a reinstall
  resets it.
