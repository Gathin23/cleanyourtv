# TCL 65" QM8L Google TV (US)

Cleaned up on 26 October 2026, on a real unit in one session. Board **G17** (MediaTek tcl9655), product
`G17_4K_US`, Android 14, firmware `UTT2.250416.001 AU27`. The same board as the
[65C8L](../tcl-65c8l-google-tv/) (firmware AU08, Australia); this list is that one without the three packages the
US build does not have. Replace `<tv ip>` below with your TV's IP.

What changed:

- **24 apps disabled**, none uninstalled, listed with reasons in [debloat.txt](debloat.txt): TCL tracking (Samba TV
  ACR, BI analytics, logkit), TCL promos / FAST channels and the message hub, the retail and hotel demo, the
  e-manual and repair guide, the partner-app auto-installers, Google feedback, the soundbar updater, a preloaded
  "AstroTV" service, Google's promo screensaver (dreamx), the setup wizard (setupwraith) and the stock Google TV
  home (launcherx).
- **Google TV home replaced** by the launcher from `launcher/`, with its clock screensaver in place of Google's.
- **Remote buttons remapped**, since the stock home answered them.

Kept: Assistant, Chromecast, AirPlay, Miracast and every installed streaming app.

Checked afterwards: Live TV starts, the Input button's source panel opens, the four HDMI inputs work, YouTube
streams with AdGuard Private DNS, the TV starts on Home at power-on, and the remapped buttons work on the real
remote. All 24 packages stayed disabled after a reboot; nothing re-enabled.

Take a full backup (package lists, settings, `getprop`) into the gitignored `backup/` folder before any change.

## Connecting to the TV

This set offers only **Wireless debugging** (Settings > System > Developer options), and its connect port changes
after each reboot. Pair with the on-screen code, connect, then run the tools through that connection (Windows, with
Google's platform-tools; see [tools/native-adb.md](../../tools/native-adb.md)):

```
adb pair <tv ip>:<pairing-port> <code>
adb connect <tv ip>:<connect-port>
set TV_ADB_BINARY=adb          (PowerShell: $env:TV_ADB_BINARY = 'adb')
set TV_HOST=<tv ip>:<connect-port>
```

If the scripts report `device '<ip>:<port>' not found`, run `adb connect <tv ip>:<connect-port>` once more: the
device can be registered only under its `adb-...._adb-tls-connect._tcp` name. Each `tools/*.sh` script has a
PowerShell twin that takes `-TvHost <tv ip>:<connect-port>`.

## Forget and re-join Wi-Fi first

`dumpsys wifi` shows the saved network owned by setupwraith (`cname=...setupwraith`). Disabling setupwraith would
delete it and drop Wi-Fi and ADB. On the TV, **forget the network and join it again** in Settings > Network &
Internet; ownership moves to `com.android.tv.settings`. Wireless debugging goes off when Wi-Fi drops and comes back
on a new port, so reconnect afterwards.

## Apply the list and install the launcher

Apply in small batches, with a screenshot between them (`tools/tv.py --screenshot shots/01.png HOME`):

1. Disable everything above the "Launcher handover" block with `tools/disable.sh` (or `disable.ps1`).
2. Set the clock screensaver, then run the full list again so setupwraith and dreamx go:
   `tools/tv.py 'settings put secure screensaver_components com.gathin.tvhome/.ClockDream'`.
3. Build and install the launcher. It disables launcherx once it owns Home:
   `launcher/build-and-install.sh` (or `.ps1`).
4. Push this folder's [config.json](config.json): `tools/theme.sh examples/tcl-65qm8l-google-tv`.
5. For the remapped buttons and power-on Home, install with both helpers:
   `INPUT_KEY_SERVICE=1 HOME_KEY_SERVICE=1 RESTORE_KEY_SERVICES=1 launcher/build-and-install.sh`
   (`-InputKeyService -HomeKeyService -RestoreKeyServices` on Windows), then push the config again.

## Remote buttons

On this remote the Settings, TV-guide and app-shortcut buttons send distinct keycodes, mapped in
[config.json](config.json) `app_keys`: Settings 83, TV guide 4001 (Live TV), Netflix 4062, Prime Video 4099,
YouTube 190. These worked on the real remote. **Trade-off:** the key helper stops hold-to-ramp volume (one step per
press). It is all-or-nothing: leave the helper off to keep it, at the cost of the remapped buttons.

## HDMI input names

Custom names such as "Cable" or "Blu-ray" are stored in one setting, as `<input id>,<name>` pairs joined by `:`:

```
tools/tv.py 'settings get secure tv_input_custom_labels'
```

Edit the name of the pair whose id ends in the port's `HW` number (HDMI 1 is `HW15`, 2 `HW16`, 3 `HW17`, 4 `HW18`)
and put the whole string back with `settings put secure tv_input_custom_labels '<value>'`. Don't turn on the
launcher's `inputs_connected_only` here: all four ports report as disconnected, so it hides every input.

## Re-enable one app

On the TV: **Settings > Apps > See all apps > Show system apps**, pick the app, choose **Enable**. Or
`tools/tv.py 'pm enable <package>'`, then put a `#` in front of its line in `debloat.txt`.

## Undo everything

```
TV_HOST=<tv ip> tools/restore.sh examples/tcl-65qm8l-google-tv/debloat.txt com.google.android.apps.tv.launcherx
TV_HOST=<tv ip> tools/tv.py 'settings delete secure screensaver_components'
```

Private DNS to an ad-blocking resolver works on this TV (it does not block YouTube ads); undo with
`settings put global private_dns_mode opportunistic`.

## When you're done

Turn off Wireless and USB debugging and **Revoke USB debugging authorisations** in Developer options, restart the
TV, then check the port is closed ("Connection refused" means it worked): `tools/adb-closed.py <tv ip>`.
