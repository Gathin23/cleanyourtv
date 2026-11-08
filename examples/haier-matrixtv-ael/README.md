# Haier S7 65" / Haier MatrixTV AEL (Google TV 14, irvine)

Product `HRT51EUL`, firmware `UKNV.260720.004`, MediaTek mt5889 (CVTE-family board), Russia. Both halves work on
this set: the debloat list and the launcher as Home. Tested on 8 November 2026 on one set, over two reboots, with
the real remote. Same-day test, not a multi-day soak.

| File | What it is |
|---|---|
| `debloat.txt` | The 20 packages the list disables, grouped and commented |
| `config.json` | Five-slot dock with Live TV first, Live TV at power-on, the gear, guide and Kinopoisk buttons |

All commands run from the repo root, with the TV's IP in `TV_HOST`.

## Install, from scratch or again

The list first, while Google TV's home still owns Home, then the launcher with the key helper, then the dock.

```bash
TV_HOST=<tv-ip> tools/disable.sh examples/haier-matrixtv-ael/debloat.txt
TV_HOST=<tv-ip> HOME_KEY_SERVICE=1 launcher/build-and-install.sh
TV_HOST=<tv-ip> tools/theme.sh examples/haier-matrixtv-ael
```

Reboot once after the launcher is Home and check that Home and ADB come back before anything else.

## Undo everything

```bash
TV_HOST=<tv-ip> tools/restore.sh examples/haier-matrixtv-ael/debloat.txt com.google.android.apps.tv.launcherx
```

## Remote buttons

The stock home answered the gear and the program-guide button as global keys, so both die with it. The Haier
and MediaTek key dispatchers answer the app buttons, but through a trampoline in `com.haier.apps`:

| Button | Keycode | What `config.json` does |
|---|---|---|
| Gear | 83 (`KEYCODE_NOTIFICATION`) | `settings_key_keycodes` opens the TV's Settings |
| Program guide | 172 (`KEYCODE_GUIDE`) | `app_keys` `172:guide:com.mediatek.wwtv.tvcenter` opens Live TV's EPG screen |
| Kinopoisk | 201 (`KEYCODE_BUTTON_14`, scan 409) | `app_keys` `201:ru.kinopoisk.tv` |
| Okko | 250 (`KEYCODE_TV_INPUT_COMPONENT_2`, scan 240) | nothing: the MediaTek dispatcher opens it through `com.haier.apps` |

`HOME_KEY_SERVICE=1` on the install line binds the helper that reads those two lists. On this set the bound
helper costs the volume ramp: a held volume key moves one step per press, and `consume_volume_keys` did not bring
the ramp back (the trade of [`docs/devices/tcl-55p71b-g09-key-repeat.md`](../../docs/devices/tcl-55p71b-g09-key-repeat.md)).
The owner kept the buttons. The two states switch with one settings write each, no rebuild:

```bash
# ramp back, three buttons gone
tools/tv.py 'settings put secure enabled_accessibility_services ""; settings put secure accessibility_enabled 0'
# buttons back, stepwise volume
tools/tv.py 'settings put secure enabled_accessibility_services com.gathin.tvhome/.HomeKeyService; settings put secure accessibility_enabled 1'
```

The keycodes came from `dumpsys activity broadcasts`: the `GLOBAL_BUTTON` records carry the whole `KeyEvent`,
where `getevent` on this remote prints only HID usages and the vendor key layout is not readable from the shell.

## Quirks on this firmware, all checked on the TV

- `com.haier.apps` looks like a Kinopoisk promo app and is in every other Haier list as bloat, but here it is the
  trampoline behind the remote's Kinopoisk and Okko buttons. With it disabled the Haier dispatcher crashed on the
  first press, Android marked it a bad process and every button it answers stayed dead until a reboot. It is
  commented out in the list; leave it.
- `com.haier.oemtabprovider` comes back enabled at every boot. Inert with the stock home off.
- The firmware resumes `com.at.factory.factorymanager` for a few seconds at every boot by itself. Never start it
  by hand.
- The firmware opens Live TV about 1.5 minutes after boot by itself. The owner wanted Live TV at power-on, so
  `boot_to_live_tv` makes the launcher open it 6 s after Home comes up instead of waiting for the firmware.
- Port 5555 answers as soon as USB debugging is on (`service.adb.tcp.port=5555`); it does not survive a reboot
  with USB debugging off, which `tools/adb-closed.py` confirmed.
- Saved Wi-Fi networks belonged to `android.uid.system`, so setupwraith went without a network check failing; the
  set was on its own Ethernet port. Check `dumpsys wifi` on yours before applying the last line of the list.
