# DEXP A651 (Changhong AI PONT, longshan), Android TV 14

One unit, firmware `UKNV.260518.001`, Russian market, Wi-Fi.

## Power-on lands on Live TV

The firmware starts `com.mediatek.wwtv.tvcenter` at every boot. `power_on_home` with `HOME_KEY_SERVICE=1`
sends it back to Home (checked after a reboot), but with `HomeKeyService` bound, holding volume up or down
moves one step per press: the same trade as
[tcl-55p71b-g09-key-repeat.md](../../docs/devices/tcl-55p71b-g09-key-repeat.md). Clearing
`enabled_accessibility_services` brings the ramp back. The tested set keeps the ramp and opens Home with the
Home button.

## RuStore

RuStore is preloaded by `com.homwee.upgrade` as version 1.36, which shows "Здесь пока нет приложений" with
nothing else in the menu. Re-enabling `com.homwee.upgrade` did not change that; installing the current
`RuStoreTV.apk` from rustore.ru over it (same RuStore signing certificate, `pm install -r`) brought the
catalog back. Keep `com.homwee.upgrade`, since it is RuStore's installer.

## Other notes

- Home candidates: the stock tvlauncher, `com.android.tv.settings/.system.FallbackHome`, and any user app that
  declares HOME (Movix, `com.ertelecom.domrutvstb`, does).
- `com.homwee.factory/com.gdch.hotel.ui.ProtocolActivity` shows up in the boot log on its own; it was not opened.

## Also tested: Hyundai AI PONT

A second unit on the same board and firmware, sold as a Hyundai. The retail model number was not recorded.
These are same-day observations from 2026-11-02, not several-day testing.

| Field | Observed value |
|---|---|
| `ro.product.model` / device | `AI PONT` / `longshan` |
| Manufacturer | `Changhong` |
| Android / API | 14 / 34 |
| Firmware | `UKNV.260518.001` |
| RAM | `MemTotal: 1824352 kB`, about 1.8 GB |
| Android display size | 1920 x 1080 override on a 3840 x 2160 physical size |
| Network | Wi-Fi |
| Stock Home | `com.google.android.tvlauncher/.MainActivity` |

The whole list was applied with `tools/disable.ps1`, then `launcher/build-and-install.ps1` handed Home over and
disabled the stock launcher. Every listed package and `com.google.android.tvlauncher` stayed disabled after a
reboot, and Home stayed on `com.gathin.tvhome`. `com.cltv.fast` was enabled on this unit and was left that way.

- Home candidates were the same two as above, and setupwraith owned the saved Wi-Fi network, so it stayed enabled.
- About 12 seconds after `sys.boot_completed` the launcher was in front, not Live TV, with `power_on_home` off.
  That was after a software reboot; a cold power-on was not checked.
- YouTube and Settings opened, and Home came back from both. The owner tried the real remote and reported
  everything working, with no per-button record.
- With `com.google.android.backdrop` disabled, `screensaver_components` still pointed at it, so it was set to the
  launcher's Clock as in `docs/launcher.md`. The screensaver was not watched starting.
- Not checked: several days of use, Chromecast, or a firmware update.
