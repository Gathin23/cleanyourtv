# TCL tc8000_ay30a2 (China, Android 11): debloat, then hand Home back

Checked on a real set on 2026-10-24. The debloat list and the Inputs config live in
[`examples/tcl-tc8000-ay30a2/`](../../examples/tcl-tc8000-ay30a2/debloat.txt).

Checked again on 2026-10-31 on build `RQ3A.210705.001.2026042801`: the debloat half still works,
but the launcher half no longer does — the newer verifier refuses every HOME package
([Newer firmware blocks the launcher](#newer-firmware-2026042801-blocks-the-launcher)).

## Identify the firmware

| Field | Observed value |
|---|---|
| `ro.product.model` / `name` / `device` | `tc8000_ay30a2` |
| `ro.product.board` / `ro.board.platform` | `ohm` / `TC8000` |
| `ro.hardware` | `amlogic` |
| Android / SDK | `11` / `30` |
| `ro.build.display.id` | `RQ3A.210705.001.2024101221 release-keys` (2026-10-24); `RQ3A.210705.001.2026042801 release-keys` (2026-10-31) |
| Security patch | `2022-09-05` |
| ABI | `armeabi-v7a`, `armeabi` (no arm64) |
| RAM | 3.8 GB (3,933,552 K) |
| Stock home | `com.tcl.cyberui` 7.6.08 (灵控桌面), the only HOME activity until another is installed |

This is an older China image than [`tcl-mt9655-cn-android14`](tcl-mt9655-cn-android14.md).
On build `2024101221`, `adb install` of an APK that declares HOME succeeded: the same
`InstallConfig` row was present (`launcherStrategy` enabled, packages empty), but that build's
verifier did not enforce it. Build `2026042801` does — see below. Network ADB on port 5555
survived a reboot on both builds while the set's ADB switch stayed on.

## Newer firmware (2026042801) blocks the launcher

On the 2026-10-31 set, `pm install -r` of the launcher built from source (SDK 30 ≥ `minSdk` 26,
585,805-byte APK) failed before anything was changed on the TV:

```
Failure [INSTALL_FAILED_VERIFICATION_FAILURE]
```

The system log names the gate — it is the stock launcher's signature, not a parse error:

```
D OverseasAppConfig: pkgName = com.gathin.tvhome
I ActivityManager: Start proc ... com.android.packageinstaller/.verifier.VerifierReciver
W verifier: fetch signature info from: com.tcl.cyberui
W verifier: signature does not match for tcl com.gathin.tvhome, will not be installed!
```

`content query --uri content://com.tcl.providers.config/all` shows the same row as the older build
(`launcherStrategy` enabled, `packages` empty), so on this build a HOME package is accepted only if
it is signed with `com.tcl.cyberui`'s key. The TCL app store (and 电视卫士's USB installer, if it is
left enabled) may still install ordinary apps; neither was tried for a HOME package here, and the
MT9655 notes show the USB installer ending in the same signature refusal there
([`tcl-mt9655-cn-android14`](tcl-mt9655-cn-android14.md#the-launcher-cannot-be-replaced)).

A factory reset on the same set (2026-10-31) left the `InstallConfig` row and the refusal exactly
as they were, so resetting the TV does not unlock the launcher either; the row lives on the system
partition. After the reset the preloads changed slightly (`com.quark.yun.tv`, `com.tcl.qiyiguo`,
`com.newtv.cboxtv` and `com.gitv.dvb.live` were gone; `com.tcl.ffeducation`, `com.tcl.gamecenter`
and `com.tcl.tshop` appeared), and the list still applied cleanly.

So on `2026042801` the honest summary is **debloat only**: do not spend the session retrying
`adb install`, and do not disable `com.tcl.cyberui`. Only a TCL-signed build could replace Home
here, so CleanYourTV cannot on this build.

What was checked after the debloat on that build: 26 packages disabled with `pm disable-user`
(27 after the factory reset, with the three preloads above), the same list still disabled after one
full reboot (`pm list packages -d`), no entries in the crash
buffer, Wi-Fi and DNS working, and `com.tcl.tv`, `com.tcl.settings`, `com.tcl.systemserver`,
`com.sohu.inputmethod.sogou.tv`, `com.tcl.autopair`, `com.droidlogic.tvinput` (12 TV input
services bound) and `com.tcl.cyberui` all left enabled.

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
