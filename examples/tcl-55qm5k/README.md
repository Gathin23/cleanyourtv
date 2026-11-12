# TCL 55QM5K (G10, Google TV 12)

Cleaned on 12 November 2026 on a real 55QM5K over one session, with a reboot check at the end and the
buttons and casting checked on the real remote and a real phone. Board **G10** (`G10_4K_US_NF`,
Realtek RTD2875), Android 12 (SDK 31), firmware `STT2.230203.001`, incremental `AS50` — the same board
as [tcl-g10-google-tv](../tcl-g10-google-tv/), whose list was the baseline. The list disables
**43 packages**, all with `pm disable-user`, none uninstalled.

Replace `<tv ip>` with the TV's IP.

## Connecting

USB debugging plus Network/ADB debugging open port 5555 over Wi-Fi directly on this set. Both toggles
are inside Settings → Device Preferences → Developer options. When done, turn both off, revoke the USB
debugging authorisations, reboot, and check the port is closed ("Connection refused" means it worked):
`tools/adb-closed.py <tv ip>`.

## Forget and re-join Wi-Fi first

The saved network belongs to setupwraith (`dumpsys wifi | grep -o 'cname=[^ ]*'`). Disabling it would
delete the network and drop Wi-Fi and ADB. On the TV, forget the network and join it again in
Settings → Network & Internet; ownership moves to `com.android.tv.settings`. Do this before applying
the list.

## Apply

1. `TV_HOST=<tv ip> tools/disable.sh examples/tcl-55qm5k/debloat.txt` in small batches, with a
   screenshot between them (`tools/tv.py --screenshot shots/01.png HOME`).
2. Build and install the launcher with both key helpers, then push this folder's config:
   `TV_HOST=<tv ip> INPUT_KEY_SERVICE=1 HOME_KEY_SERVICE=1 RESTORE_KEY_SERVICES=1 launcher/build-and-install.sh`
   `TV_HOST=<tv ip> tools/theme.sh examples/tcl-55qm5k`
3. Allow the launcher through TCL's boot manager, or TclAppBoot blocks the key services at every boot:
   `TV_HOST=<tv ip> tools/tv.py 'appops set com.gathin.tvhome APP_AUTO_START allow'` and
   `TV_HOST=<tv ip> tools/tv.py 'appops write-settings'`.
4. The screensaver: `TV_HOST=<tv ip> tools/tv.py 'settings put secure screensaver_components com.gathin.tvhome/.ClockDream'`.

## Remote buttons

The remote is a Bluetooth **BT_RC923A** whose gear and app buttons send Linux scancodes, not keycodes
(read with `tools/tv.py 'timeout 60 getevent -t'` while pressing). `app_keys` in this folder's
[config.json](config.json) maps them:

| Button | Scancode | Target |
|---|---|---|
| Gear | 233 | TV Settings |
| Netflix | 242 | Stremio |
| YouTube | 245 | YouTube |
| Prime Video | 752 | Prime Video |
| Live TV | 365 | com.tcl.tv |
| Apple TV | 652 | ignore |
| TCL TV | 345 | ignore |
| Disney+ | 762 | ignore |

One key does not go through a theme: `settings_key_scancodes` (the gear) is a `config.xml`-only key,
so put `<item>233</item>` into the `settings_key_scancodes` array in
`launcher/app/src/main/res/values/config.xml` before building.

Trade-off: with a key helper bound, a held volume key steps once per press instead of ramping
(all-or-nothing on this firmware family). Reinstalling without the helpers brings the ramp back at the
cost of the mapped buttons.

## Kept and why

`com.tcl.ttvs` stays enabled: on this firmware it hosts TCL's whole firmware updater
(`versionUpdateApp`, checked with `dumpsys package com.tcl.ttvs`). The separate Samba front end
(`tv.samba.ssm.ui`) is disabled; also opt out in Settings → Privacy, because ttvs can re-enable it at
boot on this family. Chromecast and AirPlay were kept and both verified working. Peacock, Paramount+
and Prime Video were disabled first and re-enabled at the owner's request later (their lines are
commented in the list).

## Undo everything

```
TV_HOST=<tv ip> tools/restore.sh examples/tcl-55qm5k/debloat.txt com.google.android.apps.tv.launcherx
TV_HOST=<tv ip> tools/tv.py 'settings delete secure screensaver_components'
```

Private DNS to an ad-blocking resolver works on this TV (it does not block YouTube ads); undo with
`settings put global private_dns_mode opportunistic`.
