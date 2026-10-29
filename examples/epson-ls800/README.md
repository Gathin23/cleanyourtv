# Epson LS800 (Android TV 14 laser projector)

Epson's Android TV module HA90 (built by Askey, board `sti6202d101`, Amlogic), firmware
UKG3.250826.001 (`u-2.05-U-0`, June 2026). Both halves work: the debloat list and the Clean Your TV
launcher as Home. Tested on 21 October 2026.

| File | What it is |
|---|---|
| `debloat.txt` | The 9 packages that were disabled, grouped and commented, with what to keep and why (`build-and-install.sh` disables the stock launcher on top) |
| `config.json` | Dock order and hidden tiles; push it with `tools/theme.sh` |
| `restore.sh` | Undo everything below in one go |

All commands run from the repo root, with the projector's IP in `TV_HOST`.

## Turning ADB on

Settings → Device Preferences → About → press OK on **Build** 7 times, then Developer options. This
firmware only offers **Wireless debugging** (TLS), not plain network ADB, so pair once with Google's
`adb` ([platform-tools](https://developer.android.com/tools/releases/platform-tools)):

1. Developer options → Wireless debugging → on → **Pair device with pairing code**.
2. `adb pair <ip>:<pairing port> <code>`, then `adb connect <ip>:<port shown under IP address & Port>`.
3. `adb tcpip 5555`, so `tools/tv.py` (port 5555) works. It came back by itself after a restart here.

Android switches Wireless debugging off whenever Wi-Fi drops, so pair again if the port disappears.

## Install, from scratch or again

```bash
TV_HOST=<projector-ip> tools/disable.sh examples/epson-ls800/debloat.txt
TV_HOST=<projector-ip> launcher/build-and-install.sh
TV_HOST=<projector-ip> tools/theme.sh examples/epson-ls800
```

Building needs JDK 17 and the Android SDK (`ANDROID_HOME`, platform 34). On Android 14,
`build-and-install.sh` pushes the APK and runs `pm install -r` itself, so no on-screen confirmation is
needed.

### Extras set on this unit

```bash
# Names in the Inputs menu (this firmware has no Inputs screen in Settings to rename them).
# Undo: settings delete secure tv_input_custom_labels
TV_HOST=<projector-ip> tools/tv.py "settings put secure tv_input_custom_labels 'com.droidlogic.tvinput%2F.services.Hdmi2InputService%2FHW6,ARC:com.droidlogic.tvinput%2F.services.Hdmi3InputService%2FHW7,Nintendo%20Switch'"
```

HDMI 1 names itself over CEC ("PlayStation 5"). Epson's operation-log upload is switched off in
Projector Settings → Information → Projector Operation Log Information, not by disabling
`com.epson.logcollection` (that crashes the Information screen).

## Re-enable an app

```bash
TV_HOST=<projector-ip> tools/tv.py 'pm enable com.instagram.airwave'
```

Use the package name from `debloat.txt`. To put everything back, the launcher included:

```bash
TV_HOST=<projector-ip> examples/epson-ls800/restore.sh            # add --uninstall-launcher to remove it too
```

## Change the dock or the wallpaper

Edit `dock_apps` in `config.json` (four package names, left to right; `tools/tv.py 'pm list packages'`
lists them), then push it again:

```bash
TV_HOST=<projector-ip> tools/theme.sh examples/epson-ls800
```

For a wallpaper, put a `wallpaper.jpg` (1920x1080) next to `config.json` and push the same way;
`themes/README.md` has the dim and rotation options. `tools/theme.sh --reset` goes back to the
launcher's built-in config.

## Remote buttons

Home, Input, Settings, the projector-menu button, the mic, Back and volume all work without the stock
launcher. Two buttons are handled by the firmware before any app sees them:

- **YouTube** sends `KEYCODE_MACRO_3`, which Epson's system framework hard-codes to YouTube Music; on
  this firmware YouTube Music forwards straight to YouTube. Keep YouTube Music enabled, or the button
  opens its Play Store page instead. It cannot be remapped to another app.
- **Apps** (`ALL_APPS`) does nothing once the stock launcher is disabled: Android 14 sends it only to a
  system launcher. Re-enabling `com.google.android.tvlauncher` brings it back (along with the stock home
  screen's background work); the new launcher's grid already lists every app.

## When you are done

Switch **USB debugging** and **Wireless debugging** off in Developer options, revoke the debugging
authorisations there, restart, and check that ADB is closed:

```bash
tools/venv/bin/python tools/adb-closed.py <projector-ip>
```
