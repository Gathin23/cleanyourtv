# Hisense U7 (55U7SG, wutaishan)

Google TV on Android 14, US. The list is in `debloat.txt`. This page covers connecting, the remote and the launcher settings.

## Connecting

USB debugging doesn't open port 5555 on this set. Use Wireless debugging: pair once with the code from
Pair device with pairing code, then connect to the port on the main Wireless debugging screen (`tools/native-adb.md`).
It turns itself off on every reboot or Wi-Fi drop and comes back on a new port.

## Remote (SmartRC, Bluetooth)

- Input sends `KEY_F14`. Hisense's own switcher (`com.vt.tv.input`) handles it, no helper needed. `config.json` sets
  `handle_view_inputs` to `false` so the TV doesn't ask which app to open.
- Gear sends scancode 598 (`0x256`) and no keycode. To use it, put 598 in `settings_key_scancodes` in
  `launcher/app/src/main/res/values/config.xml` and install with `HOME_KEY_SERVICE=1`. A theme can't set scancodes.
- Volume stops repeating while a key helper filters keys, same as hengshan. I kept the ramp, so `config.json` sets
  `key_filter` to `false`. The gear does nothing. The Settings button on Home opens the same panel.
- Mic is hold-to-talk, same as stock.

## Launcher settings

`config.json` hides the tiles the top bar already covers (Live TV, TV Settings) and two Hisense apps (MyMusic and the
USB media center). It also points the Inputs menu at the Live TV app. Push it with
`tools/theme.sh examples/hisense-u7-wutaishan`.

Google's screensaver (`dreamx`) keeps working with launcherx disabled.
