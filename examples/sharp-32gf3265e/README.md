# Sharp 32GF3265E

Tested on one Sharp 32GF3265E ("SHARP 2K Smart TV", device umeda, Skyworth board), Google TV on Android 11
(SDK 30), firmware RTM9.241015.004, over several days in November 2026.

## Apply

1. Debloat: TV_HOST=<tv ip> tools/disable.sh examples/sharp-32gf3265e/debloat.txt
2. Install the repo launcher as Home (the script disables the stock launcher itself once the new one owns Home):
   TV_HOST=<tv ip> HOME_KEY_SERVICE=1 INPUT_KEY_SERVICE=1 launcher/build-and-install.sh
3. Push this folder's config (dock, glass band, gear key, power-on Home):
   TV_HOST=<tv ip> tools/theme.sh examples/sharp-32gf3265e

Undo: TV_HOST=<tv ip> tools/restore.sh examples/sharp-32gf3265e/debloat.txt com.google.android.apps.tv.launcherx
(the stock launcher stays disabled; the repo launcher is Home)

## Notes

- The remote's gear button maps to keycode 83 on this board's IR receiver; with settings_key_keycodes and
  settings_key_panel in examples/sharp-32gf3265e/config.json it opens the launcher's quick-settings panel.
  Over Bluetooth the same physical button can map to a different keycode - capture it with getevent -lt and
  add it to the list.
- key_filter has to be on for that mapping, and on this firmware a held key then gets no auto-repeat from
  Android (a held Volume key steps once per press). Over Bluetooth the remote repeats its key reports itself
  while held, which no key filter can tell apart from real presses; use the remote in IR mode for calm
  behaviour.
- Private DNS works on this set (the owner uses dns.adguard-dns.com); set it in the TV's network settings -
  this folder does not configure it.
- Idle power: the remote's power button only suspends the set (standby), it never cuts mains. Set over ADB
  if wanted: screen off after 10 min (system screen_off_timeout 600000) and full suspend after 1 h
  (secure sleep_timeout 3600000).
- The factory test packages stay enabled and must never be launched.
- The firmware re-enabled the Assistant (com.google.android.katniss) once at a power-on; if the mic button
  works again, disable it again from the list.
