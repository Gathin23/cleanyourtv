# Sony BRAVIA KD-55XF7596 (Android TV 9)

**Model:** Sony KD-55XF7596 (2018, X75F series), EU firmware
**Platform:** `BRAVIA_ATV3_4K_EU` / device `BRAVIA_ATV3_4K`, MediaTek mt5891 (`ro.build.product` still says `BRAVIA_ATV2`)
**OS:** Android TV 9 (SDK 28), build `PTT1.190515.001.S54 672201`, security patch 2022-01-05
**RAM / storage:** 2 GB, 8.1 GB `/data`

Same firmware build as [`sony-xbr-65x900f/`](../sony-xbr-65x900f/debloat.txt), so this list is that one plus the
leftovers [`sony-kd-55xe8596/`](../sony-kd-55xe8596/debloat.txt) disables, with this owner's choices.

## What was tested

- The 38 packages in [`debloat.txt`](debloat.txt), applied with `tools/disable.sh` in six blocks (Samba TV
  and promos, demo, telemetry, help, optional features, hotel mode and leftovers). After each block: Home
  screenshot, Netflix, HDMI 1 and the DVB-T input, and the crash log. No crashes.
- Rebooted afterwards: all 38 still disabled, nothing re-enabled by the firmware. Free RAM after boot
  went from about 200 MB to about 500 MB.
- Launcher built from `launcher/` and installed with `launcher/build-and-install.sh`. Home confirmed
  with `dumpsys window | grep mFocusedApp` before and after a reboot. Netflix, YouTube, Input, Settings
  and Home work on the real remote without the accessibility services. Inputs pill switches HDMI 1.
- No antenna was connected: for DVB-T only the tuner service answering was checked, not reception.

## Kept on purpose

Chromecast, screen mirroring, DLNA, the phone remote, Sony IP control (`webapi.core`, `remoteaccess`) and
`com.sony.dtv.b2b.vendorprotocol` for home automation, Prime Video and Sony firmware updates. Left alone as
untested: YouView (`com.sony.dtv.youview`, `com.youview.tv.servicehost`), `interactivetvutil`,
`b2b.noderuntime.*`, Vewd/HbbTV, `dialservice`.

## Notes from this firmware

- Android 9 has no `topResumedActivity`. Check what is on screen with
  `dumpsys activity activities | grep mResumedActivity`; `mFocusedApp` is in `dumpsys window`.
- HDMI 1 is `com.sony.dtv.tvinput.external/.ExternalTvInputService/HW2`, DVB-T is
  `com.sony.dtv.tvinput.tuner/.DvbTerrCableInputService/HW0`. The active input shows in
  `dumpsys tv_input | sed -n '/sessionStateMap/,$p' | grep inputId`.
- Screenshots of Netflix, HDMI and Live TV come back empty (0 bytes).
- `disable.sh` skips `com.sony.dtv.smarthomesettings`: it asks for `TV_INPUT_HARDWARE`. It is a false
  positive here (`pm query-services -a android.media.tv.TvInputService` does not list it), and the package
  already shipped disabled on this set.
- With `katniss` and `backdrop` off, `voice_interaction_service` and `screensaver_components` still name
  them. Android ignores disabled components, and leaving the settings means `restore.sh` brings both back.
- The stock `tvlauncher` HOME filter has priority 2: `set-home-activity` only takes effect once it is
  disabled, which `build-and-install.sh` does in the right order.
- No display overrides: 1920x1080, density 320, `display_density_forced` null, before and after.

config.xml used: `hidden_apps` `com.sony.dtv.tvx` and `com.sony.dtv.tvxlauncher.programguide`,
`live_tv_package` `com.sony.dtv.tvx`.
