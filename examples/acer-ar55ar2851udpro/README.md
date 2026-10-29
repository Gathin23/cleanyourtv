# Acer AR55AR2851UDPRO 55" (Android TV 14): debloated, with the launcher on the Home button

SCBC device `R4`, Realtek `rtd2851m`, Android TV 14 / SDK 34, build `UKR1.260308`, India. Done over network ADB
on 2026-10-24, with a screenshot after every batch, three cold boots (power cable out for 30 s) and the real
remote.

This is the Android TV variant of the platform in [`docs/devices/acer-r4g.md`](../../docs/devices/acer-r4g.md)
(the Google TV `R4_GTV`). Here the firmware does **not** turn disabled packages back on at boot, so a normal
list works.

## Connect

The set offers Wireless debugging. Pair once with the stock `adb` (Developer options > Wireless debugging >
Pair device with pairing code), then open port 5555 for `tools/tv.py`:

```bash
adb pair <tv ip>:<pairing port> <code>
adb connect <tv ip>:<connect port>
adb tcpip 5555
export TV_HOST=<tv ip>
```

On this set port 5555 stayed open across cold boots, so close it when you are done: USB debugging and Wireless
debugging off, the laptop removed from Paired devices, then `tools/adb-closed.py <tv ip>`.

## Debloat

```bash
tools/disable.sh examples/acer-ar55ar2851udpro/debloat.txt
```

28 packages, all disabled, none uninstalled. The header of [`debloat.txt`](debloat.txt) says what each group is
and what was kept on purpose.

## Launcher

`set-home-activity` does not stick here: Android drops the preference ("Result set changed, dropping preferred
activity") and Home stays on `com.google.android.tvlauncher`. The stock launcher was therefore left enabled,
and the launcher takes the Home button through `HomeKeyService` with `stock_home_redirect`
([`config.json`](config.json)). After each cold boot the Home button opened the new launcher, and the boot-time
cover from #1121 ran.

1. Build and install with the stock launcher kept:
   `STOCK_LAUNCHER=none HOME_KEY_SERVICE=1 launcher/build-and-install.sh`
   (Windows: `launcher\build-and-install.ps1 -TvHost <tv ip> -StockLauncher none -HomeKeyService`).
   The script installs and starts the launcher, then stops at its "Home resolves to tvlauncher" check before it
   turns `HomeKeyService` on. Turn it on as the script would: Settings > Accessibility > Home button, or the
   `enabled_accessibility_services` lines in the script.
2. Push the settings: `tools/theme.sh examples/acer-ar55ar2851udpro`.

[`config.json`](config.json): dock Prime Video, JioHotstar, Amazon Music, Netflix, 6 columns, the Live TV app
`com.apps.livetv` for the Inputs menu, the network name on the Wi-Fi pill (`icon_only_buttons` with
`wifi_label_width` 300, the widest the launcher keeps: it cuts a longer name with an ellipsis, and 0 is
icon only), and hold OK on a tile for Move and App info (`tile_menu`).

## Remote (RC710)

| Button | What it sends | Notes |
|---|---|---|
| Home, Back, volume, Settings, Input, mic | standard keys | All work with the launcher. Holding volume still ramps with `HomeKeyService` on |
| YouTube | keycode 2030, taken by `com.realtek.keyinterceptserver` | Opens `com.google.android.youtube.tv` by package; `app_keys` never sees it. With YouTube disabled, each press crashes that service, which Android restarts at once |
| ☰ Menu | `KEYCODE_MENU` (82) | For apps; the home screens ignore it |
| Bookmark | `KEYCODE_BOOKMARK` (174) | Nothing on this set answers it |
| TV | `KEYCODE_GUIDE` (172) | The programme guide inside Live TV only |

## Undo

```bash
tools/restore.sh examples/acer-ar55ar2851udpro/debloat.txt
tools/theme.sh --reset
```

Uninstall the launcher (`pm uninstall com.gathin.tvhome`) to give the Home button back to the stock home.
