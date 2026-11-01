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
