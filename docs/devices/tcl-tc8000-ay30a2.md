# TCL tc8000_ay30a2 (China, Android 11): debloat, then hand Home back

Checked on a real set on 2026-10-24. The debloat list and the Inputs config live in
[`examples/tcl-tc8000-ay30a2/`](../../examples/tcl-tc8000-ay30a2/debloat.txt).

## Identify the firmware

| Field | Observed value |
|---|---|
| `ro.product.model` / `name` / `device` | `tc8000_ay30a2` |
| `ro.product.board` / `ro.board.platform` | `ohm` / `TC8000` |
| `ro.hardware` | `amlogic` |
| Android / SDK | `11` / `30` |
| `ro.build.display.id` | `RQ3A.210705.001.2024101221 release-keys` |
| Security patch | `2022-09-05` |
| ABI | `armeabi-v7a`, `armeabi` (no arm64) |
| RAM | 3.8 GB (3,933,552 K) |
| Stock home | `com.tcl.cyberui` 7.6.08 (灵控桌面), the only HOME activity until another is installed |

This is an older China image than [`tcl-mt9655-cn-android14`](tcl-mt9655-cn-android14.md).
`adb install` of an APK that declares HOME succeeded here. The same `InstallConfig` row is
present (`launcherStrategy` enabled, packages empty), but this build's verifier did not enforce it.
Network ADB on port 5555 survived a reboot while the set's ADB switch stayed on.

## Do not disable the stock launcher

`pm disable-user com.tcl.cyberui` leaves the boot with no activity that can be HOME.
`systemReady` starts cyberui specifically, and with it gone the boot animation does not finish
and the Home key does nothing. Re-enable the package over adb; it does not need a second reboot
to come back. Leave it installed and let the steps below pull the screen off it.

## Cold boot still opens cyberui first

`TclBootAppManager` logs `start home ... reason is systemReady` with `com.tcl.cyberui` even
after `cmd package set-home-activity` points at this launcher. `HomeKeyService` with
`stock_home_redirect` then opens this launcher. On the set checked here that hand-off was about
9 seconds after `systemReady`, and the focused window was this launcher's `MainActivity`.

Two TCL app ops have to allow the process to start. Without them `TclAppBootManagerImpl` logs
`forbid bind service` / skips `BOOT_COMPLETED` with `callee_does't_have_OP_AUTO_START_permission`,
and the set stays on cyberui:

```
adb shell appops set com.gathin.tvhome APP_AUTO_START allow
adb shell appops set com.gathin.tvhome APP_ASSOC_START allow
```

`config.json` sets `stock_home_redirect` to `com.tcl.cyberui`. Enable `HomeKeyService`
(`HOME_KEY_SERVICE=1` in `launcher/build-and-install.sh`, or the `settings` lines below) and keep
whatever else is already in the list (小T, `com.tcl.walleve`, if you did not disable it):

```
adb shell settings put secure enabled_accessibility_services \
  '<existing>:com.gathin.tvhome/com.gathin.tvhome.HomeKeyService'
adb shell settings put secure accessibility_enabled 1
```

## The remote may not wake the set while the helper stays bound

On this board a key-filtering accessibility service that is still bound with the screen off has
been reported to swallow the power key, so the set only comes back after the power cable is
pulled. `HomeKeyService` filters keys. This session checked a cold boot only, not a standby wake.

## Inputs

`com.droidlogic.tvinput` registers HDMI, AV and a tuner, but the useful picker is TCL's own
source panel. `config.json` points the Inputs pill at it. Checked: the pill opens the 信号源
panel (HDMI with connection state, TV, AV, cast). Keep `com.tcl.settings`. The remote's source
key is still handled by the firmware and does not need the pill.

## What debloat does not remove

The boot advertisement is a file the firmware plays from `/data/system/ad/bootvideo/` before
any app runs. Disabling `com.tcl.bootadservice` stops that service; the shell cannot delete the
file. The art screensaver (`com.tcl.appreciate.art`) is the stock dream and carries a membership
pitch. Point `screensaver_components` at `com.android.dreams.basic/.Colors`, or at this
launcher's clock dream, after the art package is disabled.
