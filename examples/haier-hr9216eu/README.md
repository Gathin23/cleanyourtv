# Haier MatrixTV GE / HR9216EU (Google TV 14, haeundae)

EU set, firmware `UKN6.260116.152` (MediaTek, CVTE-family board), 1080p panel with the UI rendered at
1280x720 (`wm size` override is the factory default here). Both halves work on this set: the debloat
list and the Clean Your TV launcher as Home. Tested on 23 October 2026 on one set, including two
reboots, with the real remote.

| File | What it is |
|---|---|
| `debloat.txt` | The 14 packages that were disabled, grouped and commented |
| `config.json` | Dock order (YouTube, Netflix) and 4 columns; push it with `tools/theme.sh` |

All commands run from the repo root, with the TV's IP in `TV_HOST`. Network ADB must be on while you
run them (Settings → Device Preferences → About → Build x7 → Developer options). Port 5555 opens with
`adb tcpip 5555` but does not survive a reboot; Wireless debugging does and re-advertises on mDNS.

## Install, from scratch or again

The order matters: the Wi-Fi note in `debloat.txt` first (forget + re-join the saved network so it no
longer belongs to setupwraith), then the list while the stock home still owns Home, then the launcher.

```bash
TV_HOST=<tv-ip> tools/disable.sh examples/haier-hr9216eu/debloat.txt
HOME_KEY_SERVICE=1 TV_HOST=<tv-ip> launcher/build-and-install.sh
TV_HOST=<tv-ip> tools/theme.sh examples/haier-hr9216eu
```

`HOME_KEY_SERVICE=1` is the owner's choice: it maps the remote's gear button (KEY_UNKNOWN, scancode
240; add it to settings_key_scancodes in `launcher/app/src/main/res/values/config.xml` before building, the only place scancodes can go) to the TV's
Settings. The price is the volume ramp: while that key-filtering service is bound, volume moves one
step per press instead of ramping (same trade as
[docs/devices/tcl-55p71b-g09-key-repeat.md](../../docs/devices/tcl-55p71b-g09-key-repeat.md), third
set it was seen on, first on Google TV 14). Skip the flag to keep the ramp; the gear button then does
nothing and Settings opens from the launcher's top-bar pill.

## Undo everything

```bash
TV_HOST=<tv-ip> tools/restore.sh examples/haier-hr9216eu/debloat.txt com.google.android.apps.tv.launcherx
```

The launcher stays installed but is no longer Home. Never re-enable just
`com.google.android.tungsten.setupwraith` on its own while the new launcher is Home; `restore.sh`
brings Google TV's home back first.

## Re-enable one app

```bash
TV_HOST=<tv-ip> tools/venv/bin/python -I tools/tv.py 'pm enable com.google.android.youtube.tvmusic'
```

Comment its line out in `debloat.txt` so the next run leaves it alone. Without a computer: Settings →
Apps → See all app → pick the app → Enable.

## Quirks on this firmware, all checked on the TV

- Two of the disabled packages, `com.haier.oemtabprovider` and `com.haier.launcher.haiermatrix`, come
  back enabled at every boot. Both are inert once launcherx is disabled; re-disable after a boot or
  leave them.
- Netflix, Prime, Hotstar, iQiyi, Tencent, WBD and TrueID shipped disabled from the factory. Netflix
  was re-enabled (`pm enable com.netflix.ninja`) and pinned to the dock.
- With the stock home disabled, the remote's Netflix / YouTube / Input / Live TV / voice buttons keep
  working through Haier's own global-key services; only the gear button needed the launcher's helper.
- HDMI and Live TV screenshots come out black (protected video layer); `topResumedActivity` shows
  what is really playing.
