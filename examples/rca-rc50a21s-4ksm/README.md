# RCA RC50A21S-4KSM (Android TV 9, Toptech/Zeasn firmware, Realtek RTD2851P)

RCA set sold with a Zeasn home screen (big RCA logo, Netflix/Claro video/Prime Video tiles, round icon dock),
model `RC50A21S-4KSM`. Firmware `RTK2851P eng.tianju.20210703.181325`, a `userdebug` build on a
Realtek RTD2851P (`rtd285o`, board `mac6p`), about 1.5 GB RAM. The Android model properties are generic
(`SmartTV`), so match on the build id. Both halves work: the debloat list and the reference launcher as Home.
Applied on 21 October 2026 on one set and used for two days since, through several reboots and power off/on.
Not a longer soak yet.

| File | What it is |
|---|---|
| `debloat.txt` | The 6 packages that were disabled, grouped and commented |
| `config.json` | Inputs pill wired to RCA's own source panel, plus a placeholder dock; push it with `tools/theme.sh` |

All commands run from the repo root, with the TV's IP in `TV_HOST`.

## Turning ADB on

Settings → Device Preferences → About → press OK on Build 7 times → back → Developer options → USB debugging.
USB debugging alone opens network ADB on port 5555.

This firmware is `userdebug` and `ro.adb.secure` is empty: **no "Allow USB debugging?" prompt appears, and any
device on the network gets a shell while debugging is on.** Switch it off when you are done. On the test set,
`settings put global adb_enabled 0` closed the port and it stayed closed after a restart
(`tools/adb-closed.py <tv ip>` answers "refused").

The TV took a new DHCP address after most reboots, and moved between Wi-Fi and Ethernet when a cable was
plugged in, so read the IP again (Settings → Network) before reconnecting.

## Install

```bash
TV_HOST=<tv ip> tools/disable.sh examples/rca-rc50a21s-4ksm/debloat.txt
TV_HOST=<tv ip> STOCK_LAUNCHER=com.zeasn.rca.toptech launcher/build-and-install.sh
TV_HOST=<tv ip> tools/theme.sh examples/rca-rc50a21s-4ksm
```

`disable.sh` skips the stock launcher while it still owns Home; run it once more after the launcher is in.
Edit `dock_apps` in `config.json` before pushing it.

## Inputs: use RCA's own panel

`TvInputManager` lists HDMI 1-3, AV, VGA and YPbPr, but nothing on this firmware answers `ACTION_VIEW` on a
passthrough channel URI the way a Live TV app would, so the launcher's HDMI rows do not switch input. Worse, any
installed app that claims `vnd.android.cursor.item/channel` catches that intent instead: on the test set
**ES File Explorer** (`com.estrongs.android.pop`, its "transfer station" activity) opened every time HDMI 1 was
picked (#805).

RCA's own source panel works: `com.toptech.tvmenu/.input.InputActivity` (action
`com.toptech.tv.action.INPUT_ACTIVITY`) lists TV, AV, YPbPr, VGA, HDMI1 (ARC), HDMI2 and HDMI3 and switches
correctly. `config.json` points `source_panel_service` at it and empties `live_tv_package`, so the Inputs pill
opens that panel directly. This part is what the test set has run on for the two days.

## The remote's Input (Fuente/Source) button: optional, not tested with the merged options

The remote's key layout (`/system/usr/keylayout/venus_IR_input.kl`) maps scancode 768 to `KEYCODE_TV_INPUT`.
`dumpsys window policy` routes it as a global key to `com.toptech.tvmenu/.receiver.GlobalKeyReceiver`, which does
nothing once the Zeasn launcher is not Home. The Inputs pill works without any of the following.

To get the button back, `InputKeyService` has to catch the key, and three things on this firmware get in the way:

1. **Nothing answers `com.android.tv.action.VIEW_INPUTS`**, which is what the service starts, so the key is
   swallowed. `"handle_view_inputs": true` in `config.json` (#730) makes the launcher answer it with its Inputs
   menu, which with this folder's `config.json` should open RCA's panel.
2. **The firmware empties `enabled_accessibility_services` and sets `accessibility_enabled` to 0 at every boot**,
   and Settings has no Accessibility screen to turn the service back on. `"restore_key_services": true` with
   `RESTORE_KEY_SERVICES=1` at install (#670) puts it back when Home starts, as long as the firmware empties the
   list before the service is bound (see the author's note in #670).
3. **Key repeat stops while the service filters keys**: a held volume button steps once per press (with the service
   off it steps continuously; #763). `"key_filter": false` (#770) brings repeat back but then the service no
   longer catches the Input key, so on this set it is one or the other. The remote's volume buttons are not
   `KEYCODE_VOLUME_*`: they arrive as `KEYCODE_BUTTON_7` (up, scancode 766) and `KEYCODE_BUTTON_6` (down, 765),
   global keys that `com.realtek.tv.settings` turns into volume steps.

The test set got the button working with local launcher changes that did the same as 1 and 2 by other means, plus
a volume repeat inside the service for 3. The merged `handle_view_inputs` and `restore_key_services` have **not**
been run on this set, so treat this section as a starting point. Install with the service on:

```bash
TV_HOST=<tv ip> INPUT_KEY_SERVICE=1 RESTORE_KEY_SERVICES=1 STOCK_LAUNCHER=com.zeasn.rca.toptech launcher/build-and-install.sh
```

## Other firmware quirks

- **ES File Explorer loses its data on every Home press.** The system services code on this build
  (`/system/framework/oat/arm/services.vdex`) contains the string `com.estrongs.android.pop`, and the log shows
  `ActivityManager: Force stopping com.estrongs.android.pop ...: clear data` between the firmware's own
  `HOME44444444` / `HOME5555555555` lines. Nothing in this folder causes it.
- Settings has no Accessibility screen.

## Undo everything

```bash
TV_HOST=<tv ip> tools/restore.sh examples/rca-rc50a21s-4ksm/debloat.txt com.zeasn.rca.toptech
```
