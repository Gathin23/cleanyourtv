# Artel A55LU8500 (Google TV 14): debloated, with the launcher on the Home button

MOKA device `R3GS` (`ro.product.vendor.brand` is ACER, the product ARTEL), Realtek `rtd2841a`, Google TV on
Android 14 / SDK 34, build `UKR1.250314.001`, incremental `25052320`, about 0.9 GB RAM, Uzbekistan. Shop
listings give this model as Android 11; the set reports Android 14. Same platform as
[`kalley-43g200/`](../kalley-43g200/) and [`acer-ar32hdigu2841at/`](../acer-ar32hdigu2841at/) (device
`R3_GTV` there), and it behaves the same way: the firmware turns a fixed set of packages back on at every boot
([`docs/devices/acer-r4g.md`](../../docs/devices/acer-r4g.md)).

Done over network ADB on one set, in four batches with a screenshot of Home after each, then two reboots.
Used for YouTube, Kinopoisk and HDMI; no antenna was connected.

## Connect

Developer options (Settings > System > About, press Build seven times) has **Wi-Fi debugging**. Turning it on
opened plain ADB on port 5555 as well as the Wireless debugging port, so `tools/tv.py` connected without
pairing once the TV's "Allow debugging?" prompt was accepted (`ro.adb.secure=1`). Wi-Fi debugging switches
itself off at every restart and when the TV goes to standby: turn it on again after each.

## Debloat

```bash
TV_HOST=<tv ip> tools/disable.sh examples/artel-a55lu8500/debloat.txt
```

(Windows: `tools\disable.ps1 -TvHost <tv ip> examples\artel-a55lu8500\debloat.txt`.) The list disables 10
packages: telemetry, Android's ad services, the Realtek partner customizer, Google TV's partner auto-installs,
Health Connect, print and the hotspot app. All 10 stayed disabled across two reboots; the HbbTV app was off on the
tested set too and is left as a comment, since shared lists keep broadcast features.

Ten more disable fine but are back after the next boot, as on the Kalley and Acer sets: Netflix and its token
manager, YouTube Music, Play Games, ambient mode, the SCast and FastScreen casting apps, the store-demo sticker,
M Sign (digital signage) and Audio Connect. They are named in the list's header rather than listed, and
[`config.json`](config.json) hides the tiles of those that have one. YouTube, the Assistant and Chromecast
built-in were kept by the owner's choice.

## Launcher

`launcherx` and `setupwraith` stay enabled (the list's header says why), so the new launcher takes the Home
button through `HomeKeyService` with `stock_home_redirect`, as on the Acer AR32HDIGU2841AT:

1. `STOCK_LAUNCHER=none HOME_KEY_SERVICE=1 launcher/build-and-install.sh`
   (Windows: `launcher\build-and-install.ps1 -TvHost <tv ip> -StockLauncher none -HomeKeyService`). The script
   installs and starts the launcher, then stops at its "Home resolves to launcherx" check and suggests disabling
   setupwraith: don't, on this set. Turn `HomeKeyService` on instead, in Settings > Accessibility or with the
   `enabled_accessibility_services` lines in the script, as in [`panasonic-4ktv-3mup/`](../panasonic-4ktv-3mup/).
2. `TV_HOST=<tv ip> tools/theme.sh examples/artel-a55lu8500`.

[`config.json`](config.json): YouTube and Kinopoisk in the dock, Live TV `com.realtek.tv` for the Inputs menu,
the Home redirect, the re-enabled tiles hidden, and `key_filter` false. With the default filter on, holding
Volume moved one step only; with it off the volume ramps again and Home still works. Checked on the real remote:
Home from inside YouTube returns to the launcher, the Inputs menu lists the HDMI inputs, and the D-pad and Back
work. After both reboots the launcher was back on Home, `HomeKeyService` was still enabled, and `logcat` showed
no `RecoveryActivity`.

## Sleep timer

The TV's own power-off timer is in Settings > System > Power & energy ("Device power-off timer"). The
[Sleep Timer add-on](../../addons/sleep-timer/) works on this set too, as a card on the launcher's Home.

## Close ADB afterwards

Switch Wi-Fi debugging and USB debugging off in Developer options and revoke the USB debugging
authorisations. With Wi-Fi debugging off, `tools/adb-closed.py <tv ip>` reported port 5555 closed.

## Undo

```bash
TV_HOST=<tv ip> tools/restore.sh examples/artel-a55lu8500/debloat.txt com.google.android.apps.tv.launcherx
TV_HOST=<tv ip> tools/theme.sh --reset
```
