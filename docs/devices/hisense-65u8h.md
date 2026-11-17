# Hisense 65U8H (wuyishan): one-session debloat and launcher handover

A US Hisense 65U8H was worked on over ADB on 2026-11-02. This is a one-session report, not a
several-day package-disable list. The 26-package set is recorded here until it has been lived with;
promote it to `examples/hisense-65u8h/` with a `tv.json` after that.

| Identifier | Observed value |
|---|---|
| Retail model | Hisense 65U8H (US) |
| ADB product model / device | `SmartTV 4K FFM` / `wuyishan` |
| Board | MediaTek MT9900 (`mt5895`), `armeabi-v7a`, 2 GB RAM |
| Android | Google TV / Android 11, SDK 30, build `RTT2.211209.001` / `00.00.00.56` |
| UI | 1920x1080 at density 320, with no `wm size` or `wm density` override |
| Stock Home | `com.google.android.apps.tv.launcherx/.home.HomeActivity` |
| Live TV and HDMI | `com.mediatek.wwtv.tvcenter/.LiveTVMainActivity` |
| Input menu | `com.vt.tv.input/.view.activity.MainActivity` |

## Home handover

`cmd package set-home-activity` printed `Success` while `launcherx` was enabled, but Home still
resolved to `launcherx`. After disabling `launcherx`, `setupwraith`'s `.RecoveryActivity` became Home
(priority 1), so it had to go too.

`setupwraith` also owned the saved Wi-Fi network: `dumpsys wifi` showed only its `cname`. The network
was forgotten and re-joined in Settings first, after which the owner was `com.android.tv.settings`.
Disabling `setupwraith` then no longer deleted the network, and the TV stayed on Wi-Fi and ADB. With
both packages disabled, Home resolved to `com.gathin.tvhome/.MainActivity` and survived two reboots;
the boot log shows `FallbackHomeCustomizer`, then the launcher.

Native AirPlay kept working after `setupwraith` was disabled, tested from an Apple device. This differs
from the [wutaishan U7 report](../../examples/hisense-u7-wutaishan/debloat.txt), where AirPlay stops
with the setup wizard. Chromecast, Miracast and the Google TV screensaver also worked.

## Packages disabled in the one session

26 packages, in three batches with a screenshot between each, then two reboots. The disabled list was
identical after the reboot. Nothing was uninstalled.

- Tracking: `com.hisense.evservice` (ACR), `android.autoinstalls.config.google.gtvpai` (PAI),
  `com.google.android.partnersetup`, `com.google.android.feedback`,
  `com.google.android.syncadapters.calendar`.
- Retail, first-run and preloads: `com.hisense.storemode`, `com.hisense.fte`, `com.vt.eula`,
  `com.google.android.onetimeinitializer`, `com.hisense.hibrowser`, `com.hisense.tv.protector`
  (DeviceAssistant), `com.vt.tv.mediacenter` (USB media center), `com.android.printspooler`,
  `com.android.dreams.basic`, `com.google.android.youtube.tvmusic`.
- Voice assistants: `com.google.android.katniss`, `com.google.android.tv.assistant`,
  `com.hisense.alexa`.
- DLNA: `com.hisense.tv.dlna`.
- Streaming apps not used: `com.amazon.amazonvideo.livingroom`, `com.hulu.livingroomplus`,
  `com.wbd.stream`, `com.tubitv`, `tv.pluto.android`, `com.haystack.android`.
- Setup wizard: `com.google.android.tungsten.setupwraith`.

Already disabled at the factory: Netflix, Disney+, Google TV movies, Play Games and
`net.vidaatv.android.tv`.

Kept: all `com.mediatek.*` input and middleware packages, `com.vt.tv.input`, the smartsense/AIoT family
(`com.hisense.tv.smartsense`, `com.vt.tv.smartsense.ui`, `com.vt.tv.aiot`, `com.vt.tv.aipq`), Play
Store/Services, the keyboard, Bluetooth/remote services, OTA, Chromecast (`mediashell`), Miracast,
AirPlay/HomeKit, the screensaver (`dreamx`), YouTube TV, Twitch and Jellyfin.

## Remote, volume and display

- Input opens the launcher's Inputs menu natively through `com.vt.tv.input`; no key helper is needed.
- The YouTube button is keycode 265 (scancode 246) and the gear is keycode 83 (scancode 512). With
  `launcherx` disabled they do nothing. `HOME_KEY_SERVICE=1` with `app_keys` and
  `settings_key_keycodes` opened the gear's quick-settings panel, but the YouTube button still did not
  open and the held-volume ramp dropped to one step per press. The helper was removed and the ramp
  returned, so no key helper is used. This is the same trade as the
  [TCL G09 key-repeat note](tcl-55p71b-g09-key-repeat.md).
- The Wi-Fi pill shows "Wi-Fi", not the SSID, because Android Location is off; enabling Location
  reveals the network name.
- `wm size` is 1920x1080 and `wm density` 320 with no override.

## Not tested

- No several-day run, so no `examples/` list yet.
- Casting from a phone, HDMI signal playback and CEC were not tested beyond the Inputs menu listing
  HDMI 1-4, AV and Live TV.
- Screenshots and the exact commands stay private and are not committed.
