# Hisense 65U88QG (wutaishan): one-session debloat, launcher handover, AirPlay kept

A Hisense 65U88QG was worked on over ADB on 2026-11-21. It is the same board and the same build as
[`examples/hisense-u7-wutaishan`](../../examples/hisense-u7-wutaishan/README.md), so this is a second unit for that
list, written down as a one-session report: it has not been lived with for several days yet. Two things differed
from the U7, and both are worth knowing before applying that list: native AirPlay kept working, and network ADB
on port 5555 was open.

**In short:** after the cleanup and the launcher handover everything the owner used worked as before -
Home, Live TV and HDMI, Chromecast, AirPlay, Gemini, the Input button and the streaming apps - with one exception:
**held volume**. Most of the remote's app buttons, the gear, the profile button and the Live TV button went dead
with `launcherx`, because the firmware sends them to it. Mapping them in the launcher's `app_keys` and installing
with `INPUT_KEY_SERVICE=1` brought every one back (and a button whose stock target is gone can be given a new job:
any app, an HDMI input or an action such as `settings` or `screensaver`). The price of that helper on this set is
that a held volume key moves one step; `volume_pauses_filter` restores the ramp after a first tap. Details below.

| Identifier | Observed value |
|---|---|
| Retail model | Hisense 65U88QG |
| ADB product model / device | `SmartTV 4K FFM` / `wutaishan` |
| Android | Google TV / Android 14, SDK 34, build `UKN6.251123.001.049` |
| UI | `wm size` 1920x1080 override on a 3840x2160 panel, density 320; the override was there before any change |
| Stock Home | `com.google.android.apps.tv.launcherx/.home.HomeActivity` |
| Live TV and HDMI | `com.vt.tv.tvcenter/.LiveTVMainActivity` |
| Input menu | `com.vt.tv.input/.view.activity.MainActivity` (Input key, no helper needed) |

## Packages disabled in the one session

The U7 list unchanged, in four batches with a screenshot between them, then rebooted three times. The
disabled list was identical after each reboot; nothing came back on.

- Tracking: `com.vt.tv.evservice`, `android.autoinstalls.config.google.gtvpai`.
- Hisense content: `com.vidaa.oemtabcontentprovider`, `com.vt.hishow`.
- Demo, hotel and first-run: `com.vt.storemode`, `com.vt.tv.b2b`, `com.vt.fte`.
- Off by choice: `com.vt.tv.protector`, `com.vt.alexa`, `com.vt.tv.dlna`, `com.vt.tv.ai_sense`.
- Setup wizard: `com.google.android.tungsten.setupwraith`, then `launcherx` by
  `launcher/build-and-install.sh`.

Already disabled at the factory on this unit: `com.google.android.partnersetup`, `com.google.android.feedback`,
`com.google.android.syncadapters.calendar` and `net.vidaatv.hisense.android.tv`. Nothing was uninstalled.

Kept: the OTA updaters, Google Assistant/Gemini, Chromecast (`mediashell`), the AirPlay packages, Live TV and all
`com.vt.source.*` inputs, the smartsense/AIoT family, Netflix, Prime Video, YouTube and YouTube Music.

## Differences from the U7 unit

- **AirPlay kept working.** With `setupwraith` disabled, the set still advertised `_airplay._tcp` on the LAN, and
  the owner played to it from an Apple device. On the U7 it stopped advertising and the AirPlay page came up
  empty. Same build, so the difference is not explained here; a third unit would tell which is usual.
- **Port 5555 was open** at the start (`service.adb.tcp.port` 5555, not persistent), where the U7 README says USB
  debugging leaves it closed. After a reboot it was closed, and the rest of the session ran over Wireless
  debugging through `TV_ADB_BINARY` ([`tools/native-adb.md`](../../tools/native-adb.md)). Wireless debugging came
  back on a new connect port once during the session while the toggle stayed on, so `adb mdns services` is worth
  re-reading when a connection goes offline.
- `setupwraith` owned the saved Wi-Fi here too. Re-selecting the network in Settings was not enough (same network
  ID, `cname` still `setupwraith`); it had to be forgotten and joined again with the password, after which
  `cuid=1000 cname=android.uid.system:1000`, and disabling the wizard left Wi-Fi up.

Checked by the owner after the handover: Home after reboots, Live TV and HDMI 1 and 2 from the Inputs menu,
Chromecast from a phone, AirPlay, Gemini, YouTube and Netflix. At power-on the set opens `FallbackHome` and then
the launcher; Live TV does not start by itself.

## Remote buttons

Read from `WindowManager: interceptKeyTq` lines in `logcat` while the owner pressed each button (on this set
`getevent` was not needed):

| Button | keycode | scancode | Without a helper, `launcherx` disabled |
|---|---|---|---|
| Netflix | 265 | 470 | works (`NETFLIX_KEY_START`, MediaTek receiver) |
| YouTube | 190 | 469 | nothing (global key for `launcherx`) |
| Prime Video | 1503 | 468 | nothing (`com.vt.tv.smartsense` global key) |
| Disney+ | 1562 | 418 | nothing |
| Tubi | 1561 | 419 | nothing |
| Gear | 83 | 598 | nothing (global key for `launcherx`) |
| Profile | 288 | 600 | nothing (global key for `launcherx`) |
| Live TV | 172 | 362 | nothing (global key for `launcherx`) |
| Input | 1594 | 184 | works (`com.vt.tv.input`) |
| Custom app button | 1554 | 420 | works: Hisense's handler opens the app the owner assigned to it |

### The workaround

1. Read each button's code from `logcat` while pressing it on the real remote (the table above), since injected
   keys never reach the helper.
2. Put one `app_keys` entry per button in `launcher/app/src/main/res/values/config.xml`: `keycode:package` for an
   app, `keycode:settings` for the gear, an action word such as `screensaver` for a button whose stock target is
   gone, or `scan:<scancode>:input:<input id>` to make a button switch to an HDMI port (here HDMI 1 is
   `com.vt.source.external/.hdmi.HdmiTvInputService/HW2`, hardware id 2 at `hdmi_port=1` in `dumpsys tv_input`).
   Netflix, Input and the custom app button need no entry.

   The owner's mapping, as an example:

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
3. Build and install with `INPUT_KEY_SERVICE=1` (`launcher/build-and-install.sh`); Android drops the helper on
   every reinstall, so pass it each time.
4. Volume: with the helper bound, held volume moved one step per press, the trade in the
   [key-repeat note](tcl-55p71b-g09-key-repeat.md). `consume_volume_keys` did not help here.
   `volume_pauses_filter` did in part: tap once, then hold within 4 s, and it ramps; a cold hold is one step. All
   mapped buttons keep working. The owner kept that. No soundbar was connected, so CEC volume is untested.

## Not tested

- No several-day run, so nothing is added to the U7 list yet.
- CEC and an eARC soundbar, HDMI 3, AV, and the held-volume behaviour over CEC.
- Screenshots and the session's commands stay private.
