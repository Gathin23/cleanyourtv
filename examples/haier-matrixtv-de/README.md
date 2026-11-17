# Haier MatrixTV DE (Google TV 14, omonia)

Product `HR9676EUF`, firmware `UKN2.241219.053` (MediaTek, CVTE-family board, the board of the
[AQUA MatrixTV EE 50](../aqua-matrixtv-ee-50/)). Both halves work on this set: the debloat list and the launcher as
Home. Tested on 31 October 2026 on one set, over several reboots, with the real remote. Same-day test, not a
multi-day soak.

| File | What it is |
|---|---|
| `debloat.txt` | The 14 packages the list disables, grouped and commented (four more were off on this set by the owner's choice and are commented out) |
| `config.json` | Live TV app and animations off; add your own `dock_apps` |

All commands run from the repo root, with the TV's IP in `TV_HOST`.

## Connecting

Port 5555 was closed out of the box, so Wireless debugging was the way in: `adb pair`, `adb connect`, then
`adb tcpip 5555`. Both 5555 and Wireless debugging are gone after each full start, so each start needs a new pairing
code from the TV.

## Install, from scratch or again

Check the Wi-Fi note in `debloat.txt` first, then the list while Google TV's home still owns Home, then the launcher.

```bash
TV_HOST=<tv-ip> tools/disable.sh examples/haier-matrixtv-de/debloat.txt
TV_HOST=<tv-ip> launcher/build-and-install.sh
TV_HOST=<tv-ip> tools/theme.sh examples/haier-matrixtv-de
```

## Undo everything

```bash
TV_HOST=<tv-ip> tools/restore.sh examples/haier-matrixtv-de/debloat.txt com.google.android.apps.tv.launcherx
```

## Quirks on this firmware, all checked on the TV

- The gear sends `KEYCODE_NOTIFICATION` (83), a global key for launcherx's `GlobalKeyListener`, so it does nothing once
  launcherx is disabled, and Settings opens from the launcher's top bar. `settings_key_keycodes` `[83]`,
  `consume_volume_keys` and `HOME_KEY_SERVICE=1` brought it back with the volume ramp intact, checked by the owner on the
  real remote. That is the opposite of the trade noted for the AQUA and the [MatrixTV GE](../haier-hr9216eu/). But the
  owner found the TV laggy with the helper on and better with it off, so this set runs without it. That was how it felt,
  not a measurement, and the first check came right after a restart.
- Launcher animations cost a lot here: 49% of frames were late with a 57 ms median moving along the dock with them on,
  and none, at 12 ms, with `animations` off. Wallpaper made almost no difference.
- `com.netflix.ninja` comes back enabled at every boot, and started itself through a scheduled job. Hide it with
  `hidden_apps`. `cmd appops set com.netflix.ninja RUN_ANY_IN_BACKGROUND ignore` was accepted and survived a reboot.
- Voice searches for a song or video answer "Google Assistant is having trouble processing your request" once
  launcherx is disabled (`docs/TROUBLESHOOTING.md`).
- The remote's power button is a light sleep with no boot, so Open on start does not fire from it.
- `input keyevent 26` or `KEYCODE_SLEEP` over ADB took the set off the network within seconds, and it then needed the
  real remote to wake.
- With a wake from sleep, YouTube's own "Who's watching" screen can appear on an account with several channels.
