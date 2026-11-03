# JVC 4K Smart TV (KTC, elliniko), Google TV 14

Tested on build `UKN4.250714.142` for a week with `debloat.txt` applied and the CleanYourTV launcher as Home.

## Remote keys

With `com.google.android.apps.tv.launcherx` disabled, nothing answers the gear button any more. It sends
`KEY_CALENDAR`, scancode 397 (`getevent -lt` shows `MSC_SCAN 00a16c12`, which is not the number to use).
Set this in `launcher/app/src/main/res/values/config.xml` before building - `settings_key_scancodes` is
read from `config.xml` only, a theme cannot set it - and install with `HOME_KEY_SERVICE=1`
(`-HomeKeyService` on Windows):

```xml
<integer-array name="settings_key_scancodes">
    <item>397</item>
</integer-array>
```

The red and blue buttons send the standard `KEYCODE_PROG_RED` / `KEYCODE_PROG_BLUE` (tested with
`app_keys` entries like `KEYCODE_PROG_RED:<package>`; green and yellow not tried).

**Trade-off:** with the key helper bound, holding Volume moves one step per press instead of ramping. The
same happened with Button Mapper's accessibility service on this TV, so it is the key filter, not the
launcher. Without any key-filtering service, held Volume repeats normally.

## ADB

Port 5555 never opens; only Wireless debugging is offered, and only over Wi-Fi (on Ethernet Android
switches it off). Pair once, then use the native backend in `tools/native-adb.md`. The pairing was lost
once after a reboot and had to be repeated.
