# AQUA MatrixTV EE 50" 4K (Google TV 14, omonia)

Indonesian set sold under Haier's AQUA brand, product `AQ9676EU`, firmware `UKN7.260716.049` (MediaTek,
CVTE-family board). 4K panel with the UI rendered at 1920x1080 (`wm size` override is the factory default).
Both halves work on this set: the debloat list and the Clean Your TV launcher as Home. Tested on
24 October 2026 on one set, including two reboots, with the real remote. Same-day test, not a multi-day soak.
A close sibling of [`haier-hr9216eu`](../haier-hr9216eu/) (MatrixTV GE, EU).

| File | What it is |
|---|---|
| `debloat.txt` | The 20 packages that were disabled, grouped and commented |
| `config.json` | Dock (Netflix, YouTube, Vidio, Spotify), 6 columns, Live TV app and the gear button; push it with `tools/theme.sh` |

All commands run from the repo root, with the TV's IP in `TV_HOST`. Network ADB must be on while you
run them (Settings → System → About → Android TV OS build x7 → Developer options → USB debugging). On the
test set port 5555 answered straight away and survived reboots.

## Install, from scratch or again

The order matters: check the Wi-Fi note in `debloat.txt` first, then the list while Google TV's home still
owns Home (setupwraith goes with it), then the launcher.

```bash
TV_HOST=<tv-ip> tools/disable.sh examples/aqua-matrixtv-ee-50/debloat.txt
HOME_KEY_SERVICE=1 TV_HOST=<tv-ip> launcher/build-and-install.sh
TV_HOST=<tv-ip> tools/theme.sh examples/aqua-matrixtv-ee-50
```

`HOME_KEY_SERVICE=1` is the owner's choice: with `settings_key_keycodes` `[83]` and `settings_key_panel` from
`config.json`, the remote's gear button opens the launcher's quick panel (TV settings, Picture, Network, Apps),
as it opened Google TV's dashboard before. The price is the volume ramp: while that key-filtering service is
bound, holding Volume Up moves one step per press (same trade as
[docs/devices/tcl-55p71b-g09-key-repeat.md](../../docs/devices/tcl-55p71b-g09-key-repeat.md) and
`haier-hr9216eu`). Skip the flag to keep the ramp; the gear then does nothing and Settings opens from the
launcher's top-bar pill.

## Undo everything

```bash
TV_HOST=<tv-ip> tools/restore.sh examples/aqua-matrixtv-ee-50/debloat.txt com.google.android.apps.tv.launcherx
```

The launcher stays installed but is no longer Home. Never re-enable just
`com.google.android.tungsten.setupwraith` on its own while the new launcher is Home; `restore.sh`
brings Google TV's home back first.

## Re-enable one app

```bash
TV_HOST=<tv-ip> tools/venv/bin/python -I tools/tv.py 'pm enable com.amazon.amazonvideo.livingroom'
```

Comment its line out in `debloat.txt` so the next run leaves it alone. Without a computer: Settings →
Apps → See all apps → pick the app → Enable.

## Quirks on this firmware, all checked on the TV

- setupwraith owned a saved Wi-Fi network from first setup that was not the one in use
  (`dumpsys wifi`, `cname=com.google.android.tungsten.setupwraith`). `tools/disable.sh` refuses setupwraith
  while it owns any network, so it was forgotten first (`cmd wifi forget-network <id>`). If it owns the network
  the TV is on, forget and re-join that network in Settings instead.
- `com.haier.gamingbar` comes back enabled at every boot, so it is commented out in the list. The Haier tab
  provider and Zeasn launcher, which come back on the MatrixTV GE, stayed disabled here.
- Live TV is `com.cltv.hybrid` (Anoki "LiveTV"), not `com.mediatek.wwtv.tvcenter`; `config.json` points the
  Inputs menu's Live TV row at it. `com.mediatek.tv.oneworld.tvcenter` ships disabled. HDMI 1-4 open in
  `com.google.android.tv.inputplayer` from the Inputs menu.
- With Google TV's home disabled, the remote's Netflix, YouTube, Input and voice buttons keep working through
  `com.mediatek.tv.agent` and Haier's global-key receivers. The YouTube Music button opens YouTube Music, which
  on this build forwards into YouTube; the Prime Video button only works while Prime Video is enabled.
- The gear, YouTube Music and Prime Video buttons all arrive as `KEY_UNKNOWN` (scancode 240) on
  "SmartTV-RC Consumer Control", told apart only by HID usage (0x0c009f, 0x0c00cb, 0x0c00a6). The gear maps to
  `KEYCODE_NOTIFICATION` (83), which `dumpsys window policy` lists as a global key for launcherx. Use the keycode:
  scancode 240 in `settings_key_scancodes` would catch the other two buttons too.
- Android 14 archives apps that were never opened: iQIYI shows up as a reactivate stub and downloads itself again
  on first launch.
- HDMI and Live TV screenshots come out empty (protected video layer); `topResumedActivity` shows
  what is really playing.
