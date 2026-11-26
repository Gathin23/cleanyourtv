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
`TV_HOST=<tv ip> tools/theme.sh examples/hisense-u7-wutaishan`.

Google's screensaver (`dreamx`) keeps working with launcherx disabled.

## Second unit: Hisense 65U88QG

A Hisense 65U88QG (`SmartTV 4K FFM`, `wutaishan`, Google TV 14, build `UKN6.251123.001.049`) got this list
unchanged over ADB on 2026-11-21: four batches with a screenshot between them, then three reboots. The disabled
list was identical after each reboot. It is a one-session report, not lived with for several days yet. Already
disabled at the factory on this unit: `com.google.android.partnersetup`, `com.google.android.feedback`,
`com.google.android.syncadapters.calendar` and `net.vidaatv.hisense.android.tv`.

Checked by the owner afterwards: Home after reboots, Live TV and HDMI 1 and 2 from the Inputs menu, Chromecast from
a phone, AirPlay, Gemini, YouTube and Netflix. At power-on the set opens `FallbackHome` and then the launcher.

What differed from the U7:

- **AirPlay kept working.** With `setupwraith` disabled, the set still advertised `_airplay._tcp` on the LAN and the
  owner played to it from an Apple device. On the U7 it stopped advertising. Same build, so the difference is not
  explained here; a third unit would tell which is usual.
- **Port 5555 was open** at the start (`service.adb.tcp.port` 5555, not persistent). After a reboot it was closed and
  the rest of the session ran over Wireless debugging. It came back on a new connect port once while the toggle
  stayed on, so re-read `adb mdns services` when a connection goes offline.
- **`setupwraith` owned the saved Wi-Fi network** here too. Re-selecting it in Settings was not enough (`cname` still
  `setupwraith`); it had to be forgotten and joined again with the password, after which
  `cname=android.uid.system:1000` and disabling the wizard left Wi-Fi up.

### Remote buttons

Read from `WindowManager: interceptKeyTq` lines in `logcat` while the owner pressed each button. "Dead" is with
`launcherx` disabled and no key helper:

| Button | keycode | scancode | Without a helper |
|---|---|---|---|
| Netflix | 265 | 470 | works (`NETFLIX_KEY_START`) |
| YouTube | 190 | 469 | dead |
| Prime Video | 1503 | 468 | dead |
| Disney+ | 1562 | 418 | dead |
| Tubi | 1561 | 419 | dead |
| Gear | 83 | 598 | dead |
| Profile | 288 | 600 | dead |
| Live TV | 172 | 362 | dead |
| Input | 1594 | 184 | works (`com.vt.tv.input`) |
| Custom app button | 1554 | 420 | works (Hisense's handler opens the assigned app) |

The firmware sends the dead ones to `launcherx` or `com.vt.tv.smartsense`. To bring them back, put one `app_keys`
entry per button in `launcher/app/src/main/res/values/config.xml` and install with `INPUT_KEY_SERVICE=1` (Android
drops the helper on every reinstall, so pass it each time). The owner's mapping:

```xml
<string-array name="app_keys" translatable="false">
    <item>190:com.google.android.youtube.tv</item>              <!-- YouTube -->
    <item>1503:com.amazon.amazonvideo.livingroom</item>         <!-- Prime Video -->
    <item>1562:com.disney.disneyplus</item>                     <!-- Disney+ -->
    <item>1561:com.google.android.youtube.tvmusic</item>        <!-- Tubi button, repurposed -->
    <item>83:settings</item>                                    <!-- gear -->
    <item>288:screensaver</item>                                <!-- profile, repurposed -->
    <item>scan:362:input:com.vt.source.external/.hdmi.HdmiTvInputService/HW2</item> <!-- Live TV -> HDMI 1 -->
</string-array>
```

Netflix, Input and the custom app button need no entry. Injected keys never reach the helper, so test with the real
remote.

Volume: with the helper bound, held volume moved one step per press (the trade in the
[key-repeat note](../../docs/devices/tcl-55p71b-g09-key-repeat.md)). `consume_volume_keys` did not help.
`volume_pauses_filter` did in part: tap once, then hold within 4 s and it ramps; a cold hold is one step. The owner
kept that. No soundbar was connected, so CEC volume is untested.

Not tested: a several-day run, CEC and an eARC soundbar, HDMI 3, AV.
