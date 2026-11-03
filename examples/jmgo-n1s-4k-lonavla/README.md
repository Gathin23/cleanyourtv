# JMGO N1S 4K projector (S91A, lonavla, MediaTek m7632)

Google TV on Android 14 (SDK 34), firmware `UKNV.260415.001`. `ro.product.model` is `S91A`, `ro.product.device`
`lonavla`, `ro.product.manufacturer` `JMGO`. The panel is 3840x2160, with a stock `wm size` override of 1920x1080
at 320 dpi: that is the firmware's own setting, not something left over from a session.

The ten packages in [`debloat.txt`](debloat.txt) were disabled and the reference launcher took Home. Both held
after a reboot and through several days of use, and nothing had to be re-enabled. It is a different board from
the N1S in [`jmgo-n1s-holan/`](../jmgo-n1s-holan/debloat.txt) (K80A, `holan`, m7332), but the package set is
almost the same, and that list was the starting point.

## Network ADB

- Port 5555 opens when **Wireless debugging** is switched on, with no pairing, while `adb_enabled` (USB debugging)
  stays 0. `ro.adb.secure=1`: the first connection asks on the projector.
- Unlike the `holan` unit, port 5555 was **closed after a reboot**, and Wireless debugging had switched itself
  off. Turning it on again in Developer options opens 5555 again.
- `getprop service.adb.tls.port` is empty on this firmware, and `adb mdns services` listed nothing. The
  Wireless debugging port showed up as one of three high ports in `netstat -tln`. After switching Wireless
  debugging off, `tools/adb-closed.py` found that port and 5555 refused. The other two stayed open and reset an
  ADB `CNXN` instead of answering it, so they are not ADB.

## Home

- HOME candidates: `com.google.android.apps.tv.launcherx` (priority 2),
  `com.google.android.tungsten.setupwraith/.RecoveryActivity` (priority 1), and `com.android.tv.settings` FallbackHome.
- **setupwraith owned the saved Wi-Fi network** (`dumpsys wifi | grep -o 'cname=[^ ]*'`). On the `holan` unit
  the network survived anyway. Here the owner forgot the network and joined it again in Settings first.
  After that, the network belonged to `android.uid.system:1000`, and disabling setupwraith kept Wi-Fi.
- Order that worked: setupwraith with `tools/disable.sh`, then
  `STOCK_LAUNCHER=com.google.android.apps.tv.launcherx launcher/build-and-install.sh`. `mFocusedApp` named
  `com.gathin.tvhome` straight away and again after a reboot.
- Power-on: FallbackHome, then the new launcher (`wm_set_resumed_activity`). No Live TV app takes over, since
  there is no tuner. Leave `live_tv_package` empty; the one HDMI input is `com.mediatek.tvinput/.hdmi.HDMIInputService/HW4`.
- Location is off (`location_mode 0`), so the Wi-Fi pill reads "Wi-Fi" instead of the network name.

## Kept on purpose

- The owner's picks: Google Assistant (`com.google.android.katniss`), Chromecast
  (`com.google.android.apps.mediashell`), firmware updates (`com.jmgo.update`), Atmosphere
  (`com.jmgo.overseas.atmosphere`) and the Google ambient screensaver (`com.google.android.apps.tv.dreamx`).
- Optics and remote, as on the `holan` list: `com.jmgo.hippo`, `com.jmgo.tofautofocus`, `com.jmgo.autokeystone`,
  `com.jmgo.dewarping`, `com.jmgo.ptz`, `com.jmgo.autobrightness`, `com.jmgo.autocolortemp`,
  `com.jmgo.aieyeprotector`, `com.jmgo.a2dpsinkservice`, `com.jmgo.remotecontrolota`,
  `com.jmgo.android.tv.globalkeyoverlay`, and every `com.mediatek.*` and `com.dolby.*` package except the
  partner-row customizer.
- `com.jmgo.factory`, `mediatek.factorymenu.ui` and `com.jmgo.customizationsetupwizard` were not touched or opened.

## Remote buttons after launcherx is gone

`getevent -lt` on the "JMGO Remote Consumer Control" device:

| Button | What arrives | Result |
|---|---|---|
| Gear | HID usage `0x0C00BE`, evdev `BTN_MISC` (scancode 256), no Android keycode | does nothing; `settings_key_scancodes` 256 with `HOME_KEY_SERVICE=1` opens Settings |
| Burger | `KEY_MENU`, `KEYCODE_MENU` 82 | does nothing on Home, still works in apps; not mapped, as apps use it |
| YouTube, Prime Video, Input, mic | | still work without a helper |

The trade from [the G09 note](../../docs/devices/tcl-55p71b-g09-key-repeat.md) holds here as well. With `HomeKeyService` bound
(`capabilities=8`), held Volume Up moves one step per press. `consume_volume_keys` true did not bring the ramp
back, and a tap stayed one step. With the helper unbound (`enabled_accessibility_services` back to empty, its
value before the session), the ramp returned. The owner kept the ramp and opens Settings from the launcher's
Settings pill. `com.jmgo.hippo`'s `JmgoKeyAccessibilityService` is declared, but it was not in
`enabled_accessibility_services` before the session.
